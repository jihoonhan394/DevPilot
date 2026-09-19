package com.devpilot.integration.ai.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.time.UserTimeSettingsProvider;
import com.devpilot.integration.ai.AiGateway;
import com.devpilot.integration.ai.api.AiBudgetDecision;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.AiPendingJobCounter;
import com.devpilot.integration.ai.api.AiRequest;
import com.devpilot.integration.ai.api.AiStatus;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.PromptValue;
import com.devpilot.integration.ai.api.TruncateMode;
import com.devpilot.integration.ai.api.Truncation;
import com.devpilot.integration.ai.api.UserContentBlock;
import com.devpilot.integration.ai.api.output.ReviewEvaluateOutput;
import com.devpilot.integration.ai.log.AiCallLogRepository;
import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.AuditLogCapture;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestProperties;
import com.devpilot.testsupport.TestUser;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * docs/17 §8.5 B1~B17 (test profile 예산 USD 25 = 25,000,000 micro), §8.6 감사 이벤트, docs/09 §10.3. 월
 * 합계는 서비스 전체라 다른 테스트의 행과 섞이지 않게 먼 미래 달력 월에서 행을 직접 넣고 끝나면 지운다.
 */
@IntegrationTest
class AiBudgetGuardTest extends ApiTestSupport {

    private static final Instant FAR_MONTH = Instant.parse("2031-03-10T03:00:00Z");

    @Autowired private AiBudgetGuard guard;
    @Autowired private AiGateway gateway;
    @Autowired private AiCallLogRepository repository;
    @Autowired private UserTimeSettingsProvider userTimeSettings;
    @Autowired private AiBudgetBlockRecorder blockRecorder;
    @Autowired private AiBudgetWarningNotifier warningNotifier;

    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setUpUsers() throws Exception {
        TestUser a = TestUser.owner();
        TestUser b = TestUser.invited();
        api.get(a, "/api/v1/me");
        api.get(b, "/api/v1/me");
        userA = userId(a);
        userB = userId(b);
        clock.setInstant(FAR_MONTH);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from devpilot.ai_call_log where user_id in (?, ?)", userA, userB);
    }

    @Test
    void shouldStayEnabledJustBelowWarning() {
        insertCost(userA, 19_999_999L, FAR_MONTH);
        insertCalls(userA, 10, "SUCCESS", 1);

        assertThat(guard.usage(userA).aiStatus()).isEqualTo(AiStatus.ENABLED);
        assertAllowed(guard.checkLimitsOnly(userA, AiOperation.REVIEW_EVALUATE));
    }

    @Test
    void shouldWarnAtEightyPercentAndStillAllow() {
        insertCost(userA, 20_000_000L, FAR_MONTH);

        assertThat(guard.usage(userA).aiStatus()).isEqualTo(AiStatus.BUDGET_WARNING);
        assertAllowed(guard.checkLimitsOnly(userA, AiOperation.REVIEW_EVALUATE));
        cleanUp();
        insertCost(userA, 24_999_999L, FAR_MONTH);
        assertThat(guard.usage(userA).aiStatus()).isEqualTo(AiStatus.BUDGET_WARNING);
    }

    @Test
    void shouldDisableAndBlockMonthlyWhenBudgetIsReached() {
        insertCost(userA, 25_000_000L, FAR_MONTH);
        try (AuditLogCapture capture = AuditLogCapture.start()) {
            AiBudgetDecision decision = guard.checkLimitsOnly(userA, AiOperation.REVIEW_EVALUATE);

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.errorCode()).isEqualTo(ErrorCode.AI_MONTHLY_BUDGET_EXCEEDED);
            assertThat(decision.retryAfterSeconds())
                    .isEqualTo(
                            java.time.Duration.between(
                                            FAR_MONTH, Instant.parse("2031-03-31T15:00:00Z"))
                                    .toSeconds());
            assertThat(capture.events()).containsExactly("AI_BUDGET_BLOCKED");
            assertThat(capture.fields(0))
                    .extracting(pair -> pair.key)
                    .containsExactlyInAnyOrder(
                            "event",
                            "userRef",
                            "operation",
                            "reason",
                            "month",
                            "spentMicroUsd",
                            "budgetMicroUsd");
        }
        assertThat(guard.usage(userA).aiStatus()).isEqualTo(AiStatus.DISABLED);
        assertThat(guard.usage(userB).aiStatus()).isEqualTo(AiStatus.DISABLED);
        assertThat(
                        jdbc.queryForObject(
                                "select status || '|' || attempt_no || '|' || cost_micro_usd || '|'"
                                    + " || error_code from devpilot.ai_call_log where user_id = ?"
                                    + " and status = 'BUDGET_BLOCKED'",
                                String.class,
                                userA))
                .isEqualTo("BUDGET_BLOCKED|1|0|AI_MONTHLY_BUDGET_EXCEEDED");
    }

    @Test
    void shouldAllowFiftyNinthCallAndBlockSixtiethDaily() {
        insertCalls(userA, 59, "SUCCESS", 1);
        assertAllowed(guard.checkLimitsOnly(userA, AiOperation.REVIEW_EVALUATE));

        insertCalls(userA, 1, "SUCCESS", 1);
        AiBudgetDecision decision = guard.checkLimitsOnly(userA, AiOperation.REVIEW_EVALUATE);

        assertThat(decision.errorCode()).isEqualTo(ErrorCode.AI_DAILY_LIMIT_EXCEEDED);
        assertThat(guard.usage(userA).aiStatus()).isEqualTo(AiStatus.ENABLED);
        assertAllowed(guard.checkLimitsOnly(userB, AiOperation.REVIEW_EVALUATE));
    }

    @Test
    void shouldDenyWithUnavailableWithoutLogWhenProviderIsDisabled() {
        AiBudgetGuard disabled =
                new AiBudgetGuard(
                        TestProperties.testProfile(Map.of("devpilot.ai.provider", "disabled")),
                        repository,
                        aiBalanceMonitor,
                        userTimeSettings,
                        List.of(),
                        blockRecorder,
                        clock);

        AiBudgetDecision decision = disabled.checkLimitsOnly(userA, AiOperation.REVIEW_EVALUATE);

        assertThat(decision.errorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE);
        assertThat(decision.blockedCallId()).isNull();
        assertThat(disabled.usage(userA).aiStatus()).isEqualTo(AiStatus.DISABLED);
        assertThat(count("select count(*) from devpilot.ai_call_log where user_id = ?", userA))
                .isZero();
    }

    @Test
    void shouldCheckMonthlyBeforeDailyWhenBothAreExceeded() {
        insertCost(userA, 25_000_000L, FAR_MONTH);
        insertCalls(userA, 60, "SUCCESS", 1);

        assertThat(guard.checkLimitsOnly(userA, AiOperation.REVIEW_EVALUATE).errorCode())
                .isEqualTo(ErrorCode.AI_MONTHLY_BUDGET_EXCEEDED);
    }

    @Test
    void shouldCountCallsFromUsersPlanDayStart() {
        Instant beforeBoundary = Instant.parse("2026-10-05T18:30:00Z");
        insertCallsAt(userA, 60, "SUCCESS", 1, beforeBoundary);

        clock.setInstant(Instant.parse("2026-10-05T18:59:00Z"));
        assertThat(guard.checkLimitsOnly(userA, AiOperation.REVIEW_EVALUATE).errorCode())
                .isEqualTo(ErrorCode.AI_DAILY_LIMIT_EXCEEDED);

        clock.setInstant(Instant.parse("2026-10-05T19:00:00Z"));
        assertAllowed(guard.checkLimitsOnly(userA, AiOperation.REVIEW_EVALUATE));
    }

    @Test
    void shouldCountRetryRowsButNotBlockedRows() {
        insertCalls(userA, 55, "SUCCESS", 1);
        insertCalls(userA, 5, "INVALID_OUTPUT", 2);
        insertCalls(userA, 3, "BUDGET_BLOCKED", 1);
        assertThat(guard.checkLimitsOnly(userA, AiOperation.REVIEW_EVALUATE).errorCode())
                .isEqualTo(ErrorCode.AI_DAILY_LIMIT_EXCEEDED);

        jdbc.update("delete from devpilot.ai_call_log where user_id = ?", userA);
        insertCalls(userA, 57, "SUCCESS", 1);
        insertCalls(userA, 10, "BUDGET_BLOCKED", 1);
        assertAllowed(guard.checkLimitsOnly(userA, AiOperation.REVIEW_EVALUATE));
    }

    @Test
    void shouldSumCostAcrossUsersForMonthlyBudget() {
        insertCost(userA, 15_000_000L, FAR_MONTH);
        insertCost(userB, 10_000_000L, FAR_MONTH);

        assertThat(guard.checkLimitsOnly(userA, AiOperation.REVIEW_EVALUATE).errorCode())
                .isEqualTo(ErrorCode.AI_MONTHLY_BUDGET_EXCEEDED);
    }

    @Test
    void shouldResetMonthlyBudgetAtSeoulMonthBoundary() {
        insertCost(userA, 25_000_000L, Instant.parse("2031-10-31T14:30:00Z"));

        clock.setInstant(Instant.parse("2031-10-31T14:59:59Z"));
        assertThat(guard.checkLimitsOnly(userA, AiOperation.REVIEW_EVALUATE).allowed()).isFalse();
        clock.setInstant(Instant.parse("2031-10-31T15:30:00Z"));
        assertAllowed(guard.checkLimitsOnly(userA, AiOperation.REVIEW_EVALUATE));
    }

    @Test
    void shouldLimitConcurrencyPerUserWithSyncReservationsAndPendingJobs() {
        AiBudgetGuard withPending = guardWithPending(Map.of(userA, 1));
        AiBudgetDecision held = withPending.check(userA, AiOperation.REVIEW_EVALUATE);
        assertAllowed(held);

        AiBudgetDecision second = withPending.check(userA, AiOperation.REVIEW_EVALUATE);

        assertThat(second.errorCode()).isEqualTo(ErrorCode.AI_CONCURRENCY_LIMIT);
        assertThat(second.retryAfterSeconds()).isEqualTo(5L);
        held.reservation().close();
        held.reservation().close();
        AiBudgetDecision third = withPending.check(userA, AiOperation.REVIEW_EVALUATE);
        assertAllowed(third);
        third.reservation().close();
    }

    @Test
    void shouldAllowAsyncStartWhenOnlyOnePendingJobExists() {
        AiBudgetDecision decision =
                guardWithPending(Map.of(userA, 1)).check(userA, AiOperation.CHALLENGE_EVALUATE);

        assertAllowed(decision);
        decision.reservation().close();
    }

    @Test
    void shouldIgnoreOtherUsersRunningJobs() {
        AiBudgetDecision decision =
                guardWithPending(Map.of(userB, 5)).check(userA, AiOperation.REVIEW_EVALUATE);

        assertAllowed(decision);
        decision.reservation().close();
    }

    @Test
    void shouldWarnOnceWhenCallCrossesEightyPercent() {
        clock.setInstant(Instant.parse("2032-05-10T03:00:00Z"));
        insertCost(userA, 10_000_000L, clock.instant());
        insertCost(userB, 9_999_999L, clock.instant());
        try (AuditLogCapture capture = AuditLogCapture.start()) {
            gateway.call(reviewEvaluate(userA));
            gateway.call(reviewEvaluate(userA));

            assertThat(capture.events()).containsExactly("AI_BUDGET_WARNING");
        }
        assertThat(guard.usage(userB).aiStatus()).isEqualTo(AiStatus.BUDGET_WARNING);
    }

    @Test
    void shouldNotWarnWhenStillBelowEightyPercent() {
        clock.setInstant(Instant.parse("2032-07-10T03:00:00Z"));
        insertCost(userA, 1_000_000L, clock.instant());
        try (AuditLogCapture capture = AuditLogCapture.start()) {
            warningNotifier.afterCall(1_000L);

            assertThat(capture.events()).isEmpty();
        }
    }

    private AiBudgetGuard guardWithPending(Map<UUID, Integer> pending) {
        AiPendingJobCounter counter = user -> pending.getOrDefault(user, 0);
        return new AiBudgetGuard(
                TestProperties.testProfile(),
                repository,
                aiBalanceMonitor,
                userTimeSettings,
                List.of(counter),
                blockRecorder,
                clock);
    }

    private static void assertAllowed(AiBudgetDecision decision) {
        assertThat(decision.allowed()).as(String.valueOf(decision.errorCode())).isTrue();
    }

    private void insertCost(UUID userId, long micro, Instant at) {
        insert(userId, "SUCCESS", 1, micro, at);
    }

    private void insertCalls(UUID userId, int count, String status, int attemptNo) {
        insertCallsAt(userId, count, status, attemptNo, clock.instant());
    }

    private void insertCallsAt(UUID userId, int count, String status, int attemptNo, Instant at) {
        for (int i = 0; i < count; i++) {
            insert(userId, status, attemptNo, 0L, at);
        }
    }

    private void insert(UUID userId, String status, int attemptNo, long micro, Instant at) {
        jdbc.update(
                """
                insert into devpilot.ai_call_log (id, user_id, operation, provider, model, prompt_id, prompt_version,
                    attempt_no, cost_micro_usd, status, created_at)
                values (?, ?, 'REVIEW_EVALUATE', 'fake', 'deepseek-flash', 'review.evaluate', 'v1', ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                userId,
                attemptNo,
                micro,
                status,
                OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
    }

    private static AiRequest<ReviewEvaluateOutput> reviewEvaluate(UUID userId) {
        return AiRequest.of(
                AiOperation.REVIEW_EVALUATE,
                Map.of(
                        "reviewType", PromptValue.of("EXPLAIN"),
                        "presentedPrompt", PromptValue.of("원인 예외를 보존하는 이유를 설명하세요."),
                        "expectedAnswer", PromptValue.of("cause를 넘긴다."),
                        "rubric", PromptValue.list(List.of("R1 cause", "R2 추적"), "(없음)")),
                List.of(
                        UserContentBlock.text(
                                "answerText",
                                "cause로 넘긴다.",
                                Truncation.of(1, TruncateMode.TAIL_CHARS, 500))),
                ReviewEvaluateOutput.class,
                userId,
                new GuardContext(0, Set.of("R1", "R2"), null, Set.of()));
    }
}
