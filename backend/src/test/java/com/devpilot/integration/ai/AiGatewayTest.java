package com.devpilot.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.integration.ai.api.AiCallStatus;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.AiProviderCall;
import com.devpilot.integration.ai.api.AiRequest;
import com.devpilot.integration.ai.api.AiResult;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.PromptValue;
import com.devpilot.integration.ai.api.TruncateMode;
import com.devpilot.integration.ai.api.Truncation;
import com.devpilot.integration.ai.api.UserContentBlock;
import com.devpilot.integration.ai.api.output.EvidenceDraftOutput;
import com.devpilot.integration.ai.api.output.ReviewEvaluateOutput;
import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * docs/17 §12.1 {@code AiGatewayTest}: §5.3 두 표의 각 행 → {@code AiCallStatus}·{@code error_code}·재시도
 * 여부, SYNC 재시도 0회, ASYNC 최대 2행(attempt 1·2), 가드 위반 재시도의 feedback 블록, {@code ai_call_log} 행 수와
 * {@code effort}, {@code Retry-After}·기본 2s 대기 전달, {@code max_output_tokens} 재시도의 thinking off,
 * 402의 잔액 소진 전이, 트랜잭션 안 호출 거부(T-2, docs/09 §7 {@code AiGatewayTransactionGuardTest}).
 */
@IntegrationTest
class AiGatewayTest extends ApiTestSupport {

    @Autowired private AiGateway gateway;
    @Autowired private TransactionTemplate transactionTemplate;

    private UUID userId;

    @BeforeEach
    void provisionUser() throws Exception {
        TestUser user = TestUser.owner();
        api.get(user, "/api/v1/me");
        userId = userId(user);
    }

    @Test
    void shouldReturnValueAndWriteOneRowWhenSyncCallSucceeds() {
        AiResult<ReviewEvaluateOutput> result = gateway.call(reviewEvaluate());

        assertThat(result.status()).isEqualTo(AiCallStatus.SUCCESS);
        assertThat(result.requireValue().rubric()).hasSize(2);
        assertThat(result.promptVersion()).isEqualTo("v1");
        assertThat(result.promptVersionLabel(AiOperation.REVIEW_EVALUATE))
                .isEqualTo("review.evaluate@v1");
        assertThat(rows()).containsExactly("1|SUCCESS|off|null|fake|v1");
        assertThat(
                        count(
                                "select count(*) from devpilot.ai_call_log where user_id = ? and"
                                        + " cost_micro_usd > 0",
                                userId))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select request_fingerprint from devpilot.ai_call_log where id = ?",
                                String.class,
                                result.aiCallId()))
                .matches("[0-9a-f]{64}");
    }

    @Test
    void shouldFailWithoutRetryWhenSyncOutputViolatesGuard() {
        fakeAi().use(AiOperation.REVIEW_EVALUATE, "enum-violation");

        AiResult<ReviewEvaluateOutput> result = gateway.call(reviewEvaluate());

        assertThat(result.status()).isEqualTo(AiCallStatus.INVALID_OUTPUT);
        assertThat(result.errorCode()).isEqualTo(ErrorCode.AI_OUTPUT_INVALID);
        assertThat(result.failureCode()).isEqualTo(AsyncFailureCode.AI_OUTPUT_INVALID);
        assertThat(fakeAi().callCount(AiOperation.REVIEW_EVALUATE)).isEqualTo(1);
        assertThat(rows()).containsExactly("1|INVALID_OUTPUT|off|guard:ENUM|fake|v1");
    }

    @Test
    void shouldMapEveryFinishReasonAndTransportErrorWhenSyncCallFails() {
        assertFailure(
                "content-filter",
                AiCallStatus.REFUSED,
                ErrorCode.AI_REFUSED,
                AsyncFailureCode.AI_REFUSED,
                "content_filter");
        assertFailure(
                "rate-limited",
                AiCallStatus.RATE_LIMITED,
                ErrorCode.AI_UNAVAILABLE,
                AsyncFailureCode.AI_RATE_LIMITED,
                "rate_limit");
        assertFailure(
                "connection-error",
                AiCallStatus.PROVIDER_ERROR,
                ErrorCode.AI_UNAVAILABLE,
                AsyncFailureCode.AI_UNAVAILABLE,
                "io");
        assertFailure(
                "bad-request",
                AiCallStatus.PROVIDER_ERROR,
                ErrorCode.AI_UNAVAILABLE,
                AsyncFailureCode.AI_UNAVAILABLE,
                "http_400");
        assertFailure(
                "max-output-tokens",
                AiCallStatus.INVALID_OUTPUT,
                ErrorCode.AI_OUTPUT_INVALID,
                AsyncFailureCode.AI_OUTPUT_INVALID,
                "max_output_tokens");
        assertFailure(
                "unknown-stop",
                AiCallStatus.INVALID_OUTPUT,
                ErrorCode.AI_OUTPUT_INVALID,
                AsyncFailureCode.AI_OUTPUT_INVALID,
                "stop:length_exceeded_weird");
        assertFailure(
                "system-resource",
                AiCallStatus.PROVIDER_ERROR,
                ErrorCode.AI_UNAVAILABLE,
                AsyncFailureCode.AI_UNAVAILABLE,
                "insufficient_system_resource");
        assertFailure(
                "json-parse-violation",
                AiCallStatus.INVALID_OUTPUT,
                ErrorCode.AI_OUTPUT_INVALID,
                AsyncFailureCode.AI_OUTPUT_INVALID,
                "json_parse");
        assertFailure(
                "schema-violation",
                AiCallStatus.INVALID_OUTPUT,
                ErrorCode.AI_OUTPUT_INVALID,
                AsyncFailureCode.AI_OUTPUT_INVALID,
                "schema");
        assertThat(fakeAi().callCount(AiOperation.REVIEW_EVALUATE)).isEqualTo(9);
    }

    @Test
    void shouldExhaustBalanceAndBlockNextCallWithoutLogWhenInsufficientBalanceArrives() {
        fakeAi().use(AiOperation.REVIEW_EVALUATE, "insufficient-balance");

        AiResult<ReviewEvaluateOutput> first = gateway.call(reviewEvaluate());
        AiResult<ReviewEvaluateOutput> second = gateway.call(reviewEvaluate());

        assertThat(first.status()).isEqualTo(AiCallStatus.PROVIDER_ERROR);
        assertThat(first.errorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE);
        assertThat(aiBalanceMonitor.exhausted()).isTrue();
        assertThat(second.status()).isEqualTo(AiCallStatus.PROVIDER_ERROR);
        assertThat(second.retryAfterSeconds()).isEqualTo(3_600L);
        assertThat(second.aiCallId()).isNull();
        assertThat(fakeAi().callCount(AiOperation.REVIEW_EVALUATE)).isEqualTo(1);
        assertThat(rows()).hasSize(1);
    }

    @Test
    void shouldRetryAsyncOnceWithFeedbackBlockWhenGuardIsViolated() {
        fakeAi().use(AiOperation.EVIDENCE_DRAFT, "language-then-ok");

        AiResult<EvidenceDraftOutput> result = gateway.call(evidenceDraft());

        assertThat(result.status()).isEqualTo(AiCallStatus.SUCCESS);
        assertThat(rows())
                .containsExactly(
                        "1|INVALID_OUTPUT|low|guard:LANGUAGE|fake|v1",
                        "2|SUCCESS|low|null|fake|v1");
        List<AiProviderCall> calls = fakeAi().receivedCalls(AiOperation.EVIDENCE_DRAFT);
        assertThat(calls.get(0).input()).doesNotContain("<validation_feedback>");
        assertThat(calls.get(1).input())
                .contains("<validation_feedback>")
                .contains("- output: 설명은 한국어로 쓴다 (LANGUAGE)")
                .doesNotContain("The config stream");
        assertThat(calls.get(1).instructions()).isEqualTo(calls.get(0).instructions());
        assertThat(calls.get(1).waitBefore()).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select guard_actions::text from devpilot.ai_call_log where user_id"
                                        + " = ? and attempt_no = 1",
                                String.class,
                                userId))
                .contains("RETRIED");
    }

    @Test
    void shouldWarnInsteadOfFailingOnLastAttemptWhenLanguageKeepsFailing() {
        fakeAi().use(AiOperation.EVIDENCE_DRAFT, "language-always");

        AiResult<EvidenceDraftOutput> result = gateway.call(evidenceDraft());

        assertThat(result.status()).isEqualTo(AiCallStatus.SUCCESS);
        assertThat(result.guardActions()).extracting(action -> action.action()).contains("WARNED");
        assertThat(fakeAi().callCount(AiOperation.EVIDENCE_DRAFT)).isEqualTo(2);
    }

    @Test
    void shouldRetryWithThinkingOffWhenOutputWasCutAtMaxTokens() {
        fakeAi().use(AiOperation.EVIDENCE_DRAFT, "max-tokens-then-ok");

        AiResult<EvidenceDraftOutput> result = gateway.call(evidenceDraft());

        assertThat(result.status()).isEqualTo(AiCallStatus.SUCCESS);
        List<AiProviderCall> calls = fakeAi().receivedCalls(AiOperation.EVIDENCE_DRAFT);
        assertThat(calls.get(0).thinking()).isTrue();
        assertThat(calls.get(0).reasoningEffort()).isEqualTo("low");
        assertThat(calls.get(1).thinking()).isFalse();
        assertThat(calls.get(1).reasoningEffort()).isNull();
        assertThat(calls.get(1).input()).contains("응답이 길이 한도에서 잘렸다");
        assertThat(rows())
                .containsExactly(
                        "1|INVALID_OUTPUT|low|max_output_tokens|fake|v1",
                        "2|SUCCESS|off|null|fake|v1");
    }

    @Test
    void shouldWaitRetryAfterBeforeRetryingTransportError() {
        fakeAi().use(AiOperation.EVIDENCE_DRAFT, "rate-limited-then-ok");

        gateway.call(evidenceDraft());

        List<AiProviderCall> calls = fakeAi().receivedCalls(AiOperation.EVIDENCE_DRAFT);
        assertThat(calls.get(1).waitBefore()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void shouldWaitDefaultTwoSecondsWhenServerErrorHasNoRetryAfter() {
        fakeAi().use(AiOperation.EVIDENCE_DRAFT, "server-error-then-ok");

        AiResult<EvidenceDraftOutput> result = gateway.call(evidenceDraft());

        assertThat(result.status()).isEqualTo(AiCallStatus.SUCCESS);
        assertThat(fakeAi().receivedCalls(AiOperation.EVIDENCE_DRAFT).get(1).waitBefore())
                .isEqualTo(Duration.ofSeconds(2));
        assertThat(rows())
                .containsExactly(
                        "1|PROVIDER_ERROR|low|http_503|fake|v1", "2|SUCCESS|low|null|fake|v1");
    }

    @Test
    void shouldNotRetryRefusalEvenForAsyncOperation() {
        fakeAi().use(AiOperation.EVIDENCE_DRAFT, "refused");

        AiResult<EvidenceDraftOutput> result = gateway.call(evidenceDraft());

        assertThat(result.status()).isEqualTo(AiCallStatus.REFUSED);
        assertThat(fakeAi().callCount(AiOperation.EVIDENCE_DRAFT)).isEqualTo(1);
    }

    @Test
    void shouldRejectCallInsideTransactionWithoutReachingProvider() {
        assertThatThrownBy(
                        () ->
                                transactionTemplate.executeWithoutResult(
                                        status -> gateway.call(reviewEvaluate())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fakeAi().callCount(AiOperation.REVIEW_EVALUATE)).isZero();
        assertThat(rows()).isEmpty();
    }

    private void assertFailure(
            String fixture,
            AiCallStatus status,
            ErrorCode errorCode,
            AsyncFailureCode failureCode,
            String logCode) {
        fakeAi().use(AiOperation.REVIEW_EVALUATE, fixture);

        AiResult<ReviewEvaluateOutput> result = gateway.call(reviewEvaluate());

        assertThat(result.status()).as(fixture).isEqualTo(status);
        assertThat(result.errorCode()).as(fixture).isEqualTo(errorCode);
        assertThat(result.failureCode()).as(fixture).isEqualTo(failureCode);
        assertThat(
                        jdbc.queryForObject(
                                "select error_code from devpilot.ai_call_log where id = ?",
                                String.class,
                                result.aiCallId()))
                .as(fixture)
                .isEqualTo(logCode);
    }

    private List<String> rows() {
        return jdbc.queryForList(
                """
                select attempt_no || '|' || status || '|' || effort || '|' || coalesce(error_code, 'null')
                       || '|' || provider || '|' || prompt_version
                  from devpilot.ai_call_log where user_id = ? order by created_at, attempt_no
                """,
                String.class,
                userId);
    }

    private AiRequest<ReviewEvaluateOutput> reviewEvaluate() {
        return AiRequest.of(
                AiOperation.REVIEW_EVALUATE,
                Map.of(
                        "reviewType", PromptValue.of("EXPLAIN"),
                        "presentedPrompt", PromptValue.of("원인 예외를 보존하는 이유를 설명하세요."),
                        "expectedAnswer", PromptValue.of("cause를 넘겨 원인 추적을 가능하게 한다."),
                        "rubric", PromptValue.list(List.of("R1 cause 보존", "R2 추적 가능성"), "(없음)")),
                List.of(
                        UserContentBlock.text(
                                "answerText",
                                "원인 예외를 cause로 넘겨 연결한다.",
                                Truncation.of(1, TruncateMode.TAIL_CHARS, 500))),
                ReviewEvaluateOutput.class,
                userId,
                new GuardContext(0, Set.of("R1", "R2"), null, Set.of()));
    }

    private AiRequest<EvidenceDraftOutput> evidenceDraft() {
        return AiRequest.of(
                AiOperation.EVIDENCE_DRAFT,
                Map.of(
                        "eventType", PromptValue.of("SESSION_COMPLETED"),
                        "skillCode", PromptValue.of("JAVA.EXCEPTION"),
                        "skillName", PromptValue.of("Java Exception"),
                        "eventFacts", PromptValue.list(List.of("actualMinutes: 35"), "(없음)"),
                        "sourceDetail",
                                PromptValue.truncatable(
                                        "설정 로더 정리",
                                        Truncation.of(2, TruncateMode.TAIL_CHARS, 500))),
                List.of(
                        UserContentBlock.text(
                                "learnerNotes",
                                "스트림을 닫는 위치를 옮겼다.",
                                Truncation.of(1, TruncateMode.TAIL_CHARS, 300))),
                EvidenceDraftOutput.class,
                userId,
                GuardContext.empty());
    }
}
