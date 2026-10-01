package com.devpilot.skill.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.learning.application.LearningEventRecorder;
import com.devpilot.learning.application.LearningEventRecorder.NewLearningEvent;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * docs/06 §7.3 {@code I_DOWN_CLAIM_UNSUPPORTED} 배선 (ADR-063).
 *
 * <p>규칙 자체는 {@code ClaimUnsupportedRuleTest}가 벡터로 본다. 여기서 보는 것은 <b>자기평가가 규칙까지 전달되는가</b>와 꺼진 뒤
 * planning level이 실제로 근거로 떨어지는가다 — 단위 테스트로는 잡히지 않는 자리다.
 */
@IntegrationTest
class ClaimUnsupportedIntegrationTest extends ApiTestSupport {

    /** 기본 온보딩은 JAVA를 3으로 적는다 → {@code claimCap = 3}. */
    private static final String SKILL_CODE = "JAVA.EXCEPTION";

    @Autowired private LearningEventRecorder learningEventRecorder;

    /** 주장한 수준에서 두 번 막히면 레벨은 그대로고 주장만 꺼진다. */
    @Test
    void shouldWithdrawTheClaimAfterTwoFailuresAtTheClaimedLevel() throws Exception {
        TestUser user = onboardedOwner();
        UUID userId = userId(user);
        UUID skillId = skillId(SKILL_CODE);
        assertThat(selfAssessmentActive(userId, skillId)).isTrue();

        recordFailure(userId, skillId, 3, LocalDate.parse("2026-10-04"));
        recordFailure(userId, skillId, 3, LocalDate.parse("2026-10-05"));

        assertThat(selfAssessmentActive(userId, skillId)).isFalse();
        // 거둔 것은 주장뿐이다 — 근거를 내리지 않는다 (ADR-063)
        assertThat(stateChanges(userId, skillId)).noneMatch(change -> change.contains("->0"));
    }

    /** 주장한 수준 위에서 막히는 것은 적정 난이도다 (docs/06 §5.3) — 주장을 거두지 않는다. */
    @Test
    void shouldKeepTheClaimWhenOnlyTheStretchLevelFails() throws Exception {
        TestUser user = onboardedOwner();
        UUID userId = userId(user);
        UUID skillId = skillId(SKILL_CODE);

        recordFailure(userId, skillId, 4, LocalDate.parse("2026-10-04"));
        recordFailure(userId, skillId, 4, LocalDate.parse("2026-10-05"));

        assertThat(selfAssessmentActive(userId, skillId)).isTrue();
    }

    /** 한 번은 컨디션일 수 있다. */
    @Test
    void shouldKeepTheClaimAfterASingleFailure() throws Exception {
        TestUser user = onboardedOwner();
        UUID userId = userId(user);
        UUID skillId = skillId(SKILL_CODE);

        recordFailure(userId, skillId, 3, LocalDate.parse("2026-10-05"));

        assertThat(selfAssessmentActive(userId, skillId)).isTrue();
    }

    /**
     * 주장이 꺼지면 planning level이 근거로 떨어진다 (docs/06 §7.5). 그래서 다음 과제의 난이도가 내려가고 예산이 정직해진다 — 이 규칙을 넣은 이유
     * 전체가 이 한 줄이다.
     */
    @Test
    void shouldDropPlanningLevelToEvidenceOnceTheClaimIsWithdrawn() throws Exception {
        TestUser user = onboardedOwner();
        UUID userId = userId(user);
        UUID skillId = skillId(SKILL_CODE);
        assertThat(planningKnowledge(user)).isEqualTo(3);

        recordFailure(userId, skillId, 3, LocalDate.parse("2026-10-04"));
        recordFailure(userId, skillId, 3, LocalDate.parse("2026-10-05"));

        // K1_ANY_EVENT로 근거 K가 1이 됐으므로 planning도 1이다 (자기평가 3이 더는 쓰이지 않는다)
        assertThat(planningKnowledge(user)).isEqualTo(1);
    }

    private void recordFailure(UUID userId, UUID skillId, int difficulty, LocalDate planDate) {
        learningEventRecorder.record(
                new NewLearningEvent(
                        userId,
                        skillId,
                        null,
                        LearningEventType.CHALLENGE_EVALUATED,
                        null,
                        null,
                        planDate,
                        Map.of(
                                "difficulty",
                                difficulty,
                                "outcome",
                                "FAILED",
                                "purpose",
                                "PRACTICE",
                                "maxHintLevel",
                                "PSEUDOCODE",
                                "challengeId",
                                UUID.randomUUID().toString(),
                                "isTransfer",
                                false),
                        null,
                        Instant.parse("2026-10-05T01:00:00Z")));
    }

    private int planningKnowledge(TestUser user) throws Exception {
        return api.body(api.get(user, "/api/v1/skills/me?size=200"))
                .path("items")
                .valueStream()
                .filter(item -> SKILL_CODE.equals(item.path("skill").path("code").asString()))
                .map(item -> item.path("planningLevels").path("knowledge"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + SKILL_CODE + " in my skills"))
                .asInt();
    }

    private boolean selfAssessmentActive(UUID userId, UUID skillId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "select self_assessment_active from devpilot.user_skill_state where user_id"
                                + " = ? and skill_id = ?",
                        Boolean.class,
                        userId,
                        skillId));
    }

    private List<String> stateChanges(UUID userId, UUID skillId) {
        return jdbc.queryForList(
                "select axis || ' ' || from_level || '->' || to_level from"
                        + " devpilot.skill_state_change where user_id = ? and skill_id = ?",
                String.class,
                userId,
                skillId);
    }

    private UUID skillId(String code) {
        return jdbc.queryForObject(
                "select id from devpilot.skill where code = ?", UUID.class, code);
    }
}
