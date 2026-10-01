package com.devpilot.skill.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.skill.domain.SkillLevelRules.Outcome;
import com.devpilot.skill.domain.SkillLevelRules.RuleInput;
import com.devpilot.skill.domain.SkillLevelRules.SelfAssessment;
import com.devpilot.testsupport.TestRuleSettings;
import com.devpilot.testsupport.UnitTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * docs/06 §7.3 {@code I_DOWN_CLAIM_UNSUPPORTED} vector CU-1~CU-10 (ADR-063).
 *
 * <p>다른 하락 규칙은 근거 레벨 3 이상에서만 발동해서, 자기평가만 높고 근거가 0인 사람에게는 하나도 걸리지 않았다. 그 사이 난이도는 주장한 수준에서 나오고(§5.3)
 * 예산은 낙관적으로 고정됐다 — 어려운 문제 + 거짓 안심이다.
 *
 * <p>그래서 이 규칙은 <b>레벨을 내리지 않고 주장만 거둔다.</b> 근거는 이미 0이라 내릴 것이 없다.
 */
@UnitTest
class ClaimUnsupportedRuleTest {

    private static final Instant NOW = Instant.parse("2026-10-15T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-10-15");

    /** 근거가 0이다 — 이 규칙이 메우려는 자리가 바로 여기다. */
    private static final AxisLevels NO_EVIDENCE = AxisLevels.ZERO;

    private final SkillLevelRules rules = new SkillLevelRules(TestRuleSettings.skillLevel());

    static Stream<Arguments> vectors() {
        return Stream.of(
                Arguments.of(
                        "CU-1 주장 수준에서 두 번 막혔다",
                        claim(2),
                        List.of(failed(2, 1), failed(2, 3)),
                        true),
                Arguments.of(
                        "CU-2 둘 다 주장 수준 이하면 난이도가 달라도 센다",
                        claim(2),
                        List.of(failed(2, 1), failed(1, 3)),
                        true),
                Arguments.of(
                        "CU-3 주장 수준 위는 적정 난이도다 — 틀려도 주장을 거두지 않는다",
                        claim(2),
                        List.of(failed(3, 1), failed(3, 3)),
                        false),
                Arguments.of(
                        "CU-4 사이에 성공이 있으면 가장 최근 둘이 모두 실패가 아니다",
                        claim(2),
                        List.of(failed(2, 1), solved(2, 3)),
                        false),
                Arguments.of("CU-5 한 번은 컨디션일 수 있다", claim(2), List.of(failed(2, 1)), false),
                Arguments.of(
                        "CU-6 이미 꺼졌으면 거둘 주장이 없다",
                        new SelfAssessment(2, false),
                        List.of(failed(2, 1), failed(2, 3)),
                        false),
                Arguments.of(
                        "CU-7 자기평가하지 않은 skill",
                        claim(null),
                        List.of(failed(1, 1), failed(1, 3)),
                        false),
                Arguments.of(
                        "CU-8 0을 주장한 사람에게는 거둘 주장이 없다",
                        claim(0),
                        List.of(failed(1, 1), failed(1, 3)),
                        false),
                Arguments.of(
                        "CU-9 진단은 §7.4가 본다 — 세지 않으므로 1회뿐이다",
                        claim(2),
                        List.of(diagnostic(failed(2, 1)), failed(2, 3)),
                        false),
                Arguments.of(
                        "CU-10 5를 주장해도 claimCap은 3이다",
                        claim(5),
                        List.of(failed(3, 1), failed(3, 3)),
                        true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    void shouldWithdrawTheClaimOnlyWhenItWasNotSupported(
            String description,
            SelfAssessment selfAssessment,
            List<RuleEvent> events,
            boolean expectedDeactivate) {
        Outcome outcome = evaluate(events, selfAssessment);

        assertThat(outcome.deactivateSelfAssessment())
                .as("%s", description)
                .isEqualTo(expectedDeactivate);
    }

    /**
     * 거두는 것은 주장뿐이다 — <b>내려가는 레벨이 없다</b> (ADR-063).
     *
     * <p>{@code K1_ANY_EVENT}는 그대로 돈다(§7.2: 이벤트가 하나라도 있으면 K1). 실패도 "해 봤다"는 기록이라 올라가는 쪽은 막지 않는다 — 이
     * 규칙은 주장만 건드린다.
     */
    @Test
    void shouldWithdrawTheClaimWithoutLoweringAnyLevel() {
        Outcome outcome = evaluate(List.of(failed(2, 1), failed(2, 3)), claim(2));

        assertThat(outcome.deactivateSelfAssessment()).isTrue();
        assertThat(outcome.changes())
                .allSatisfy(
                        change -> {
                            assertThat(change.toLevel()).isGreaterThanOrEqualTo(change.fromLevel());
                        });
        assertThat(describe(outcome.changes())).containsExactly("KNOWLEDGE 0->1 K1_ANY_EVENT");
    }

    /** 실패는 근거로 세지 않는다 — 주장을 거두는 근거이지, 레벨을 올리는 증거가 아니다. */
    @Test
    void shouldNotCountFailuresAsEvidence() {
        Outcome outcome = evaluate(List.of(failed(2, 1), failed(2, 3)), claim(2));

        assertThat(outcome.evidenceCount()).isZero();
    }

    private Outcome evaluate(List<RuleEvent> events, SelfAssessment selfAssessment) {
        return rules.evaluate(
                new RuleInput(NO_EVIDENCE, events, null, Map.of(), TODAY, NOW, selfAssessment));
    }

    private static List<String> describe(List<SkillLevelRules.LevelChange> changes) {
        return changes.stream()
                .map(
                        change ->
                                "%s %d->%d %s"
                                        .formatted(
                                                change.axis(),
                                                change.fromLevel(),
                                                change.toLevel(),
                                                change.ruleCode()))
                .toList();
    }

    private static SelfAssessment claim(@Nullable Integer level) {
        return new SelfAssessment(level, true);
    }

    private static RuleEvent failed(int difficulty, int daysAgo) {
        return evaluated(difficulty, "FAILED", daysAgo);
    }

    private static RuleEvent solved(int difficulty, int daysAgo) {
        return evaluated(difficulty, "SOLVED_INDEPENDENTLY", daysAgo);
    }

    private static RuleEvent diagnostic(RuleEvent evaluated) {
        Map<String, Object> payload = new LinkedHashMap<>(evaluated.payload());
        payload.put("purpose", "DIAGNOSTIC");
        return new RuleEvent(
                evaluated.id(),
                evaluated.eventType(),
                evaluated.planDate(),
                evaluated.occurredAt(),
                payload);
    }

    private static RuleEvent evaluated(int difficulty, String outcome, int daysAgo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("difficulty", difficulty);
        payload.put("outcome", outcome);
        payload.put("purpose", "PRACTICE");
        payload.put("maxHintLevel", "PSEUDOCODE");
        payload.put("challengeId", UUID.randomUUID().toString());
        payload.put("isTransfer", false);
        return new RuleEvent(
                UUID.randomUUID(),
                LearningEventType.CHALLENGE_EVALUATED,
                TODAY.minusDays(daysAgo),
                NOW.minus(daysAgo, ChronoUnit.DAYS),
                payload);
    }
}
