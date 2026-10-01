package com.devpilot.skill.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.skill.domain.SkillLevelRules.LevelChange;
import com.devpilot.skill.domain.SkillLevelRules.Outcome;
import com.devpilot.skill.domain.SkillLevelRules.RuleInput;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * docs/06 §7.2 상승 규칙표·§7.3 하락 규칙표·§7.4 진단의 rule_code마다 조건을 만족하는 경우와 만족하지 않는 경우 (BL-SKL-04). §7.6
 * vector 표는 {@code SkillLevelRulesTest}가 따로 돌린다 — 여기서는 그 표가 다루지 않는 rule_code를 채운다.
 *
 * <p>규칙 입력은 payload뿐이므로(§7.1) 다른 축이 움직이지 않도록 현재 레벨과 payload를 골라 둔다. 변경 목록은 {@code SkillAxis} 선언
 * 순서(K, I, E, D)다.
 */
@UnitTest
class SkillLevelRuleTableTest {

    private static final Instant NOW = Instant.parse("2026-10-15T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-10-15");
    private static final String CHALLENGE_A = "11111111-1111-4111-8111-111111111111";
    private static final String CHALLENGE_B = "22222222-2222-4222-8222-222222222222";
    private static final String REVIEW_A = "33333333-3333-4333-8333-333333333333";
    private static final String REVIEW_B = "44444444-4444-4444-8444-444444444444";

    private final SkillLevelRules rules = new SkillLevelRules(TestRuleSettings.skillLevel());

    static Stream<Arguments> knowledgeCases() {
        return Stream.of(
                Arguments.of(
                        "K4_RECALL_LONG: 독립 GOOD 회상 2개가 모두 intervalBefore ≥ 14",
                        new AxisLevels(3, 1, 1, 1),
                        List.of(
                                recall("GOOD", "SELF_EXPLAIN", 14, 1),
                                recall("GOOD", "QUESTION_ONLY", 21, 3)),
                        null,
                        List.of("KNOWLEDGE 3->4 K4_RECALL_LONG"),
                        false),
                Arguments.of(
                        "K4_RECALL_LONG 미달: intervalBefore ≥ 14인 회상이 1개뿐",
                        new AxisLevels(3, 1, 1, 1),
                        List.of(
                                recall("GOOD", "SELF_EXPLAIN", 20, 1),
                                recall("GOOD", "SELF_EXPLAIN", 3, 4)),
                        null,
                        List.of(),
                        false),
                Arguments.of(
                        "K5_TRANSFER: difficulty 5를 독립으로 해결",
                        new AxisLevels(4, 3, 1, 1),
                        List.of(evaluated(5, "SOLVED_INDEPENDENTLY", "QUESTION_ONLY", CHALLENGE_A)),
                        null,
                        List.of("KNOWLEDGE 4->5 K5_TRANSFER"),
                        false),
                Arguments.of(
                        "K5_TRANSFER 미달: difficulty가 5가 아니다",
                        new AxisLevels(4, 3, 1, 1),
                        List.of(evaluated(4, "SOLVED_INDEPENDENTLY", "QUESTION_ONLY", CHALLENGE_A)),
                        null,
                        List.of(),
                        false),
                Arguments.of(
                        "이미 5면 더 올릴 단계가 없다",
                        new AxisLevels(5, 5, 5, 5),
                        List.of(
                                event(
                                        LearningEventType.SELF_EXPLANATION_SUBMITTED,
                                        1,
                                        payload("length", 120))),
                        null,
                        List.of(),
                        false),
                Arguments.of(
                        "K_DOWN_RECALL_FAIL 미달: AGAIN 2개가 최근 14 plan-day 밖",
                        new AxisLevels(3, 1, 1, 1),
                        List.of(
                                recall("AGAIN", "SELF_EXPLAIN", 2, 20),
                                recall("AGAIN", "SELF_EXPLAIN", 2, 22)),
                        null,
                        List.of(),
                        false),
                Arguments.of(
                        "K_DOWN_RECALL_FAIL 미달: 최근 REVIEW_ANSWERED가 1개뿐",
                        new AxisLevels(3, 1, 1, 1),
                        List.of(recall("AGAIN", "SELF_EXPLAIN", 2, 1)),
                        null,
                        List.of(),
                        false));
    }

    static Stream<Arguments> implementationCases() {
        return Stream.of(
                Arguments.of(
                        "I3_SOLVED_INDEPENDENT: difficulty ≥ 2 독립 해결 3개, challengeId 2종",
                        new AxisLevels(1, 2, 1, 1),
                        List.of(
                                evaluated(2, "SOLVED_INDEPENDENTLY", "QUESTION_ONLY", CHALLENGE_A),
                                evaluated(3, "SOLVED_INDEPENDENTLY", "QUESTION_ONLY", CHALLENGE_B),
                                evaluated(2, "SOLVED_INDEPENDENTLY", "QUESTION_ONLY", CHALLENGE_A)),
                        null,
                        List.of("IMPLEMENTATION 2->3 I3_SOLVED_INDEPENDENT"),
                        false),
                Arguments.of(
                        "I4_PRODUCTION_LIKE: difficulty 4 해결 + EVIDENCE_ACCEPTED",
                        new AxisLevels(1, 3, 1, 1),
                        List.of(
                                evaluated(4, "SOLVED_WITH_HINTS", "CONCEPT_HINT", CHALLENGE_A),
                                event(LearningEventType.EVIDENCE_ACCEPTED, 2, payload())),
                        null,
                        List.of("IMPLEMENTATION 3->4 I4_PRODUCTION_LIKE"),
                        false),
                Arguments.of(
                        "I4_PRODUCTION_LIKE 미달: EVIDENCE_ACCEPTED가 없다",
                        new AxisLevels(1, 3, 1, 1),
                        List.of(evaluated(4, "SOLVED_WITH_HINTS", "CONCEPT_HINT", CHALLENGE_A)),
                        null,
                        List.of(),
                        false),
                Arguments.of(
                        "I5_TRANSFER: difficulty 5 독립 해결 2종, 그중 하나가 전이 문제",
                        new AxisLevels(1, 4, 1, 1),
                        List.of(
                                transfer(
                                        evaluated(
                                                5,
                                                "SOLVED_INDEPENDENTLY",
                                                "SELF_EXPLAIN",
                                                CHALLENGE_A)),
                                evaluated(5, "SOLVED_INDEPENDENTLY", "SELF_EXPLAIN", CHALLENGE_B)),
                        null,
                        List.of("IMPLEMENTATION 4->5 I5_TRANSFER"),
                        false),
                Arguments.of(
                        "I5_TRANSFER 미달: 전이 문제가 없다",
                        new AxisLevels(1, 4, 1, 1),
                        List.of(
                                evaluated(5, "SOLVED_INDEPENDENTLY", "SELF_EXPLAIN", CHALLENGE_A),
                                evaluated(5, "SOLVED_INDEPENDENTLY", "SELF_EXPLAIN", CHALLENGE_B)),
                        null,
                        List.of(),
                        false),
                Arguments.of(
                        "I_DOWN_TRANSFER_FAIL: 방금 기록한 전이 challenge가 FAILED",
                        new AxisLevels(1, 3, 1, 1),
                        List.of(transfer(evaluated(3, "FAILED", "PSEUDOCODE", CHALLENGE_A))),
                        0,
                        List.of("IMPLEMENTATION 3->2 I_DOWN_TRANSFER_FAIL"),
                        true),
                Arguments.of(
                        "I_DOWN_TRANSFER_FAIL 미달: 전이 challenge가 아니다",
                        new AxisLevels(1, 3, 1, 1),
                        List.of(evaluated(3, "FAILED", "PSEUDOCODE", CHALLENGE_A)),
                        0,
                        List.of(),
                        false));
    }

    static Stream<Arguments> explanationCases() {
        return Stream.of(
                Arguments.of(
                        "E2_PARTIAL: EXPLAIN 복습 답변 coverage 4500",
                        new AxisLevels(1, 1, 1, 1),
                        List.of(explainAnswer(4_500, "CONCEPT_HINT", false)),
                        null,
                        List.of("EXPLANATION 1->2 E2_PARTIAL"),
                        false),
                Arguments.of(
                        "E3_COVERAGE: 러버덕 설명 증거 2개(coverage 7000, 독립)",
                        new AxisLevels(1, 1, 2, 1),
                        List.of(rubberDuck(0, 4, false), rubberDuck(0, 3, false)),
                        null,
                        List.of("EXPLANATION 2->3 E3_COVERAGE"),
                        false),
                Arguments.of(
                        "E3_COVERAGE 미달: hint를 본 러버덕은 독립이 아니다",
                        new AxisLevels(1, 1, 2, 1),
                        List.of(rubberDuck(0, 4, true), rubberDuck(0, 3, true)),
                        null,
                        List.of(),
                        false),
                Arguments.of(
                        "설명 증거 아님: gap이 남았거나 턴이 3 미만인 러버덕",
                        new AxisLevels(1, 1, 1, 1),
                        List.of(rubberDuck(1, 5, false), rubberDuck(0, 2, false)),
                        null,
                        List.of(),
                        false),
                Arguments.of(
                        "E4_TRANSFER_QUESTION: coverage 8200 설명 증거 + variant 정답",
                        new AxisLevels(1, 3, 3, 1),
                        List.of(
                                explanationCoverage(
                                        evaluated(2, "PARTIAL", "PSEUDOCODE", CHALLENGE_A), 8_200),
                                variantAnswer()),
                        null,
                        List.of("EXPLANATION 3->4 E4_TRANSFER_QUESTION"),
                        false),
                Arguments.of(
                        "E4_TRANSFER_QUESTION 미달: variant 정답이 없다",
                        new AxisLevels(1, 3, 3, 1),
                        List.of(
                                explanationCoverage(
                                        evaluated(2, "PARTIAL", "PSEUDOCODE", CHALLENGE_A), 8_200)),
                        null,
                        List.of(),
                        false),
                Arguments.of(
                        "E5_TRADEOFF: difficulty 5 문제의 설명 coverage 9000",
                        new AxisLevels(1, 3, 4, 1),
                        List.of(
                                explanationCoverage(
                                        evaluated(5, "PARTIAL", "PSEUDOCODE", CHALLENGE_A), 9_000)),
                        null,
                        List.of("EXPLANATION 4->5 E5_TRADEOFF"),
                        false));
    }

    static Stream<Arguments> debuggingCases() {
        return Stream.of(
                Arguments.of(
                        "D1_ANY_REVIEW: 코드 리뷰를 한 번 마쳤다",
                        new AxisLevels(1, 1, 1, 0),
                        List.of(event(LearningEventType.COACH_REVIEW_COMPLETED, 1, payload())),
                        null,
                        List.of("DEBUGGING 0->1 D1_ANY_REVIEW"),
                        false),
                Arguments.of(
                        "D2_FOUND_WITH_HINT: DIRECTION 이하 hint로 직접 짚었다",
                        new AxisLevels(1, 1, 1, 1),
                        List.of(
                                finding("FOUND_AFTER_HINT", "BUG", "CORRECTNESS", REVIEW_A)
                                        .with("maxHintLevel", "DIRECTION")
                                        .build()),
                        null,
                        List.of("DEBUGGING 1->2 D2_FOUND_WITH_HINT"),
                        false),
                Arguments.of(
                        "D2_FOUND_WITH_HINT 미달: PSEUDOCODE까지 보고 찾았다",
                        new AxisLevels(1, 1, 1, 1),
                        List.of(
                                finding("FOUND_AFTER_HINT", "BUG", "CORRECTNESS", REVIEW_A)
                                        .with("maxHintLevel", "PSEUDOCODE")
                                        .build()),
                        null,
                        List.of(),
                        false),
                Arguments.of(
                        "D3_FOUND_UNPROMPTED: 스스로 짚은 BUG·RISK 2개",
                        new AxisLevels(1, 1, 1, 2),
                        List.of(
                                finding("MENTIONED_UNPROMPTED", "RISK", "SECURITY", REVIEW_A)
                                        .build(),
                                finding("MENTIONED_UNPROMPTED", "BUG", "SECURITY", REVIEW_B)
                                        .build()),
                        null,
                        List.of("DEBUGGING 2->3 D3_FOUND_UNPROMPTED"),
                        false),
                Arguments.of(
                        "D4_REPEATED_UNPROMPTED: 3개 · 리뷰 2종 · confidence HIGH 1개",
                        new AxisLevels(1, 1, 1, 3),
                        List.of(
                                finding("MENTIONED_UNPROMPTED", "BUG", "CORRECTNESS", REVIEW_A)
                                        .with("confidence", "HIGH")
                                        .build(),
                                finding("MENTIONED_UNPROMPTED", "BUG", "CORRECTNESS", REVIEW_A)
                                        .with("confidence", "MEDIUM")
                                        .build(),
                                finding("MENTIONED_UNPROMPTED", "RISK", "CORRECTNESS", REVIEW_B)
                                        .with("confidence", "LOW")
                                        .build()),
                        null,
                        List.of("DEBUGGING 3->4 D4_REPEATED_UNPROMPTED"),
                        false),
                Arguments.of(
                        "D4_REPEATED_UNPROMPTED 미달: 모두 같은 리뷰에서 나왔다",
                        new AxisLevels(1, 1, 1, 3),
                        List.of(
                                finding("MENTIONED_UNPROMPTED", "BUG", "CORRECTNESS", REVIEW_A)
                                        .with("confidence", "HIGH")
                                        .build(),
                                finding("MENTIONED_UNPROMPTED", "BUG", "CORRECTNESS", REVIEW_A)
                                        .with("confidence", "HIGH")
                                        .build(),
                                finding("MENTIONED_UNPROMPTED", "RISK", "CORRECTNESS", REVIEW_A)
                                        .with("confidence", "HIGH")
                                        .build()),
                        null,
                        List.of(),
                        false),
                Arguments.of(
                        "D5_TRANSFER: 스스로 짚은 finding의 axis가 3종",
                        new AxisLevels(1, 1, 1, 4),
                        List.of(
                                finding("MENTIONED_UNPROMPTED", "BUG", "CORRECTNESS", REVIEW_A)
                                        .build(),
                                finding("MENTIONED_UNPROMPTED", "BUG", "SECURITY", REVIEW_A)
                                        .build(),
                                finding("MENTIONED_UNPROMPTED", "RISK", "CONCURRENCY", REVIEW_A)
                                        .build()),
                        null,
                        List.of("DEBUGGING 4->5 D5_TRANSFER"),
                        false),
                Arguments.of(
                        "D_DOWN_REPEATED_MISS: 같은 axis 최근 finding 3개가 모두 MISSED",
                        new AxisLevels(1, 1, 1, 3),
                        List.of(
                                finding("MISSED", "BUG", "SECURITY", REVIEW_A).build(),
                                finding("MISSED", "BUG", "SECURITY", REVIEW_A).build(),
                                finding("MISSED", "RISK", "SECURITY", REVIEW_B).build()),
                        null,
                        List.of("DEBUGGING 3->2 D_DOWN_REPEATED_MISS"),
                        true),
                Arguments.of(
                        "D_DOWN_REPEATED_MISS 미달: 가장 최근 3개 중 하나는 직접 찾았다",
                        new AxisLevels(1, 1, 1, 3),
                        List.of(
                                finding("FOUND_AFTER_HINT", "BUG", "SECURITY", REVIEW_A).build(),
                                finding("MISSED", "BUG", "SECURITY", REVIEW_A).build(),
                                finding("MISSED", "RISK", "SECURITY", REVIEW_B).build()),
                        null,
                        List.of(),
                        false),
                Arguments.of(
                        "D_DOWN_REPEATED_MISS 미달: 같은 axis finding이 2개뿐",
                        new AxisLevels(1, 1, 1, 2),
                        List.of(
                                finding("MISSED", "BUG", "SECURITY", REVIEW_A).build(),
                                finding("MISSED", "BUG", "SECURITY", REVIEW_B).build()),
                        null,
                        List.of(),
                        false),
                Arguments.of(
                        "D_DOWN_REPEATED_MISS 미달: axis가 없는 finding은 세지 않는다",
                        new AxisLevels(1, 1, 1, 3),
                        List.of(
                                finding("MISSED", "BUG", null, REVIEW_A).build(),
                                finding("MISSED", "BUG", null, REVIEW_A).build(),
                                finding("MISSED", "RISK", null, REVIEW_B).build()),
                        null,
                        List.of(),
                        false));
    }

    static Stream<Arguments> diagnosticCases() {
        return Stream.of(
                Arguments.of(
                        "DIAG_PASSED: claimedLevel이 없으면 진단 difficulty를 쓴다 (docs/06 §7.4)",
                        new AxisLevels(1, 1, 1, 1),
                        List.of(
                                event(
                                        LearningEventType.DIAGNOSTIC_PASSED,
                                        1,
                                        payload("difficulty", 2))),
                        null,
                        List.of("KNOWLEDGE 1->2 DIAG_PASSED", "IMPLEMENTATION 1->2 DIAG_PASSED"),
                        false),
                Arguments.of(
                        "DIAG_PASSED: 이미 목표 레벨 이상이면 바꾸지 않는다",
                        new AxisLevels(3, 3, 1, 1),
                        List.of(
                                event(
                                        LearningEventType.DIAGNOSTIC_PASSED,
                                        1,
                                        payload("claimedLevel", 2, "difficulty", 2))),
                        null,
                        List.of(),
                        false),
                Arguments.of(
                        "DIAGNOSTIC_FAILED: 레벨은 그대로이고 자기평가만 끈다",
                        new AxisLevels(1, 1, 1, 1),
                        List.of(
                                event(
                                        LearningEventType.DIAGNOSTIC_FAILED,
                                        1,
                                        payload("claimedLevel", 3))),
                        null,
                        List.of(),
                        true));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource({
        "knowledgeCases",
        "implementationCases",
        "explanationCases",
        "debuggingCases",
        "diagnosticCases"
    })
    void shouldApplyRuleTableWhenEventsAreGiven(
            String description,
            AxisLevels current,
            List<RuleEvent> events,
            @Nullable Integer triggerIndex,
            List<String> expectedChanges,
            boolean expectedDeactivate) {
        UUID trigger = triggerIndex == null ? null : events.get(triggerIndex).id();

        Outcome outcome =
                rules.evaluate(new RuleInput(current, events, trigger, Map.of(), TODAY, NOW));

        assertThat(describe(outcome.changes())).as("%s", description).isEqualTo(expectedChanges);
        assertThat(outcome.deactivateSelfAssessment())
                .as("%s", description)
                .isEqualTo(expectedDeactivate);
    }

    private static List<String> describe(List<LevelChange> changes) {
        return changes.stream()
                .map(
                        change ->
                                change.axis()
                                        + " "
                                        + change.fromLevel()
                                        + "->"
                                        + change.toLevel()
                                        + " "
                                        + change.ruleCode())
                .toList();
    }

    // ------------------------------------------------------------ 이벤트 조립 (docs/04 §6 payload)

    private static RuleEvent event(
            LearningEventType type, int daysAgo, Map<String, Object> payload) {
        return new RuleEvent(
                UUID.randomUUID(),
                type,
                TODAY.minusDays(daysAgo),
                NOW.minus(daysAgo, ChronoUnit.DAYS),
                payload);
    }

    /** {@code REVIEW_ANSWERED} (docs/04 §6). {@code reviewType = RECALL}이라 설명 증거는 아니다. */
    private static RuleEvent recall(
            String finalRating, String hintLevel, int intervalBefore, int daysAgo) {
        return event(
                LearningEventType.REVIEW_ANSWERED,
                daysAgo,
                payload(
                        "finalRating",
                        finalRating,
                        "hintLevel",
                        hintLevel,
                        "intervalBefore",
                        intervalBefore,
                        "reviewType",
                        "RECALL"));
    }

    /** 설명 증거가 되는 {@code REVIEW_ANSWERED} (docs/06 §7.2 "설명 증거와 coverage"). */
    private static RuleEvent explainAnswer(int coverageBp, String hintLevel, boolean wasVariant) {
        return event(
                LearningEventType.REVIEW_ANSWERED,
                1,
                payload(
                        "reviewType",
                        "EXPLAIN",
                        "rubricCoverageBp",
                        coverageBp,
                        "hintLevel",
                        hintLevel,
                        "finalRating",
                        "GOOD",
                        "wasVariant",
                        wasVariant));
    }

    /** E4가 요구하는 variant 정답 (docs/06 §7.2). coverage가 없어 설명 증거로는 세지 않는다. */
    private static RuleEvent variantAnswer() {
        return event(
                LearningEventType.REVIEW_ANSWERED,
                2,
                payload(
                        "reviewType",
                        "RECALL",
                        "wasVariant",
                        true,
                        "evaluatedOutcome",
                        "CORRECT",
                        "finalRating",
                        "GOOD"));
    }

    private static RuleEvent evaluated(
            int difficulty, String outcome, String maxHintLevel, String challengeId) {
        return event(
                LearningEventType.CHALLENGE_EVALUATED,
                1,
                payload(
                        "difficulty",
                        difficulty,
                        "outcome",
                        outcome,
                        "maxHintLevel",
                        maxHintLevel,
                        "challengeId",
                        challengeId,
                        "isTransfer",
                        false));
    }

    private static RuleEvent transfer(RuleEvent evaluated) {
        return copyWith(evaluated, "isTransfer", true);
    }

    private static RuleEvent explanationCoverage(RuleEvent evaluated, int coverageBp) {
        return copyWith(evaluated, "explanationCoverageBp", coverageBp);
    }

    /** {@code RUBBER_DUCK_COMPLETED} (docs/06 §7.2 RD-5). coverage는 고정값 7000이다. */
    private static RuleEvent rubberDuck(int gapCount, int turns, boolean hintDisclosed) {
        return event(
                LearningEventType.RUBBER_DUCK_COMPLETED,
                1,
                payload("gapCount", gapCount, "turns", turns, "hintDisclosed", hintDisclosed));
    }

    private static FindingBuilder finding(
            String discoveredBy, String findingType, @Nullable String axis, String coachReviewId) {
        return new FindingBuilder(discoveredBy, findingType, axis, coachReviewId);
    }

    private static RuleEvent copyWith(RuleEvent source, String key, Object value) {
        Map<String, Object> payload = new LinkedHashMap<>(source.payload());
        payload.put(key, value);
        return new RuleEvent(
                source.id(), source.eventType(), source.planDate(), source.occurredAt(), payload);
    }

    private static Map<String, Object> payload(Object... keysAndValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            payload.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return payload;
    }

    /** {@code COACH_FINDING_CLOSED} payload 조립 (docs/04 §6). */
    private static final class FindingBuilder {

        private final Map<String, Object> payload = new LinkedHashMap<>();

        private FindingBuilder(
                String discoveredBy,
                String findingType,
                @Nullable String axis,
                String coachReviewId) {
            payload.put("discoveredBy", discoveredBy);
            payload.put("findingType", findingType);
            payload.put("coachReviewId", coachReviewId);
            if (axis != null) {
                payload.put("axis", axis);
            }
        }

        FindingBuilder with(String key, Object value) {
            payload.put(key, value);
            return this;
        }

        RuleEvent build() {
            return event(LearningEventType.COACH_FINDING_CLOSED, 1, new LinkedHashMap<>(payload));
        }
    }
}
