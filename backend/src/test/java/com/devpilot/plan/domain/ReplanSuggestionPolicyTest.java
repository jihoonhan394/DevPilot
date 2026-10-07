package com.devpilot.plan.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.plan.domain.DeadlineRiskEvaluator.RiskEstimate;
import com.devpilot.plan.domain.ReplanSuggestionPolicy.DeferSuggestion;
import com.devpilot.plan.domain.ReplanSuggestionPolicy.Expansion;
import com.devpilot.plan.domain.ReplanSuggestionPolicy.Suggestions;
import com.devpilot.plan.domain.ReplanSuggestionPolicy.TargetItem;
import com.devpilot.plan.domain.ReplanSuggestionPolicy.TargetReduction;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.SkillAxis;
import com.devpilot.testsupport.TestRuleSettings;
import com.devpilot.testsupport.UnitTest;
import com.devpilot.testsupport.VectorLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * docs/06 §4.4 vector 1~4 ({@code 06-04-replan-suggestion.yaml}), AC-03 S1·S3, AC-30 S1·S2, docs/09
 * §5.3. 모든 경우에 축소·defer와 확장이 동시에 비어 있지 않은지 함께 확인한다.
 */
@UnitTest
class ReplanSuggestionPolicyTest {

    private static final String VECTOR_FILE = "06-04-replan-suggestion.yaml";

    private final DeadlineRiskEvaluator evaluator =
            new DeadlineRiskEvaluator(TestRuleSettings.risk());
    private final ReplanSuggestionPolicy policy = new ReplanSuggestionPolicy(evaluator);

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("vectors")
    void shouldMatchVectorWhenSuggestionsAreMade(
            String id, int effective, List<TargetItem> items, Map<String, Object> expected) {
        Suggestions result = policy.suggest(items, effective);

        assertThat(result.current())
                .as(id)
                .isEqualTo(
                        new RiskEstimate(
                                integer(expected, "requiredMust"),
                                integer(expected, "requiredShould"),
                                integer(expected, "ratioBp"),
                                RiskLevel.valueOf((String) expected.get("risk")),
                                // 이 vector 들은 잴 수 없는 축을 쓰지 않는다 (ADR-062)
                                0));
        assertThat(result.reductions()).as(id).isEqualTo(reductions(expected));
        assertThat(result.defers().stream().map(DeferSuggestion::skillCode).toList())
                .as(id)
                .isEqualTo(expected.get("defers"));
        assertThat(result.expansions()).as(id).isEqualTo(expansions(expected, items));
        assertThat(result.after()).as(id).isEqualTo(estimate(map(expected, "after")));
        assertOneDirection(result);
    }

    @ParameterizedTest(name = "[{index}] required {0} / effective 5000 → {1}")
    @CsvSource({
        "5900, REDUCE",
        "3400, EXPAND",
        "3500, EXPAND",
        "3550, NONE",
        "4500, NONE",
        "4000, NONE"
    })
    void shouldChooseOneDirectionWhenRatioChanges(int requiredMust, String direction) {
        List<TargetItem> items =
                List.of(
                        mustWithRequired("B.MUST", requiredMust),
                        new TargetItem(
                                "B.RESTORE",
                                Priority.SHOULD,
                                5_000,
                                true,
                                new AxisLevels(1, 0, 0, 0),
                                AxisLevels.ZERO,
                                100),
                        new TargetItem(
                                "B.SHOULD",
                                Priority.SHOULD,
                                5_000,
                                false,
                                new AxisLevels(1, 0, 0, 0),
                                AxisLevels.ZERO,
                                100));

        Suggestions result = policy.suggest(items, 5_000);

        switch (direction) {
            case "REDUCE" -> assertThat(result.defers()).isNotEmpty();
            case "EXPAND" -> assertThat(result.expansions()).isNotEmpty();
            default -> {
                assertThat(result.defers()).isEmpty();
                assertThat(result.reductions()).isEmpty();
                assertThat(result.expansions()).isEmpty();
                assertThat(result.after()).isEqualTo(result.current());
            }
        }
        assertOneDirection(result);
    }

    @Test
    void shouldSuggestNothingWhenEffectiveIsZero() {
        Suggestions result = policy.suggest(List.of(mustWithRequired("Z.MUST", 100)), 0);

        assertThat(result.current().ratioBp()).isNull();
        assertThat(result.current().riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(result.defers()).isEmpty();
        assertThat(result.reductions()).isEmpty();
        assertThat(result.expansions()).isEmpty();
    }

    @Test
    void shouldStopExpansionAtBoundaryWhenExpandedRatioExceedsLimit() {
        // requiredMust 3500 (7000bp) → 복원 1000분이면 4500 → 9000 (경계 포함), 1분 더하면 9002
        List<TargetItem> fits =
                List.of(mustWithRequired("E.MUST", 3_500), restorable("E.RESTORE", 8_000, 1_000));
        List<TargetItem> exceeds =
                List.of(mustWithRequired("E.MUST", 3_500), restorable("E.RESTORE", 8_000, 1_001));

        assertThat(policy.suggest(fits, 5_000).expansions()).hasSize(1);
        assertThat(policy.suggest(exceeds, 5_000).expansions()).isEmpty();
    }

    @Test
    void shouldNotReduceTargetBelowThreeWhenReducing() {
        TargetItem lowTargets =
                new TargetItem(
                        "R.LOW",
                        Priority.MUST,
                        1_000,
                        false,
                        new AxisLevels(3, 3, 3, 3),
                        AxisLevels.ZERO,
                        1_000);

        Suggestions result = policy.suggest(List.of(lowTargets), 1_000);

        assertThat(result.current().riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(result.reductions()).isEmpty();
    }

    @Test
    void shouldPickFirstAxisInKiedOrderWhenGapsTie() {
        TargetItem tie =
                new TargetItem(
                        "R.TIE",
                        Priority.MUST,
                        1_000,
                        false,
                        new AxisLevels(5, 5, 5, 5),
                        new AxisLevels(3, 3, 3, 3),
                        1_000);

        Suggestions result = policy.suggest(List.of(tie), 100);

        assertThat(result.reductions()).hasSize(1);
        assertThat(result.reductions().getFirst().axis()).isEqualTo(SkillAxis.KNOWLEDGE);
        assertThat(result.reductions().getFirst().newTarget()).isEqualTo(4);
    }

    @Test
    void shouldNeverRaiseSameSkillTwiceOrAboveFive() {
        TargetItem raisable =
                new TargetItem(
                        "X.RAISE",
                        Priority.MUST,
                        9_000,
                        false,
                        new AxisLevels(5, 4, 5, 5),
                        new AxisLevels(5, 3, 5, 5),
                        10);

        Suggestions result = policy.suggest(List.of(raisable), 100_000);

        assertThat(result.expansions()).hasSize(1);
        assertThat(result.expansions().getFirst().axis()).isEqualTo(SkillAxis.IMPLEMENTATION);
        assertThat(result.expansions().getFirst().newTarget()).isEqualTo(5);
    }

    // --- ADR-070: 0분짜리 제안을 내지 않는다 (AC-38 S4) ---

    /** 상한 `(5,3,5,0)`: I는 3까지, D는 아예 못 센다. */
    private static final AxisLevels CEILING = new AxisLevels(5, 3, 5, 0);

    private final ReplanSuggestionPolicy capped = new ReplanSuggestionPolicy(evaluator, CEILING);

    /**
     * <b>{@code raiseAxis} 수정</b>. Codex 발견: 그 메서드는 planning이 가장 낮은 축을 고르는데, 측정 불가 축은 근거 경로가 없어
     * 자기평가가 없는 사용자에게는 planning이 0이다. 그래서 D가 체계적으로 골라졌고 {@code required}가 D를 세지 않으므로 <b>"여유가 생겼으니 더
     * 공부하라"가 0분짜리 제안</b>이었다. 이제 상한 이내의 축만 고른다.
     */
    @Test
    void shouldNotPickAnAxisWhoseTargetAlreadySitsAtItsCeiling() {
        TargetItem item =
                new TargetItem(
                        "X.PICK",
                        Priority.MUST,
                        9_000,
                        false,
                        new AxisLevels(4, 3, 5, 2), // D 목표 2 — 상한 0 위. 전에는 planning 0이라 D가 뽑혔다
                        new AxisLevels(3, 3, 5, 0), // K만 현재 목표 미달
                        10);

        Suggestions result = capped.suggest(List.of(item), 100_000);

        assertThat(result.expansions()).hasSize(1);
        // D(상한 0)가 아니라 K다. D를 2 → 3으로 올려도 지금 할 수 있는 공부가 늘지 않는다.
        assertThat(result.expansions().getFirst().axis()).isEqualTo(SkillAxis.KNOWLEDGE);
    }

    /**
     * <b>{@code belowTarget} 수정</b>. 원래 목표로 보면 상한 위 목표가 남은 skill이 <b>영구히 "상향 가능"</b>이 되어, 측정 가능한 축을
     * 모두 채운 뒤에도 0분짜리 후보로 남았다. 현재 판정 목표로 보면 후보에서 빠진다.
     */
    @Test
    void shouldNotTreatASkillAsRaisableWhenOnlyCappedTargetsRemain() {
        TargetItem item =
                new TargetItem(
                        "X.DONE",
                        Priority.MUST,
                        9_000,
                        false,
                        new AxisLevels(5, 3, 5, 2), // 남은 것은 상한 위의 D 목표뿐이다
                        new AxisLevels(5, 3, 5, 0), // 측정 가능한 축은 모두 현재 목표에 닿았다
                        10);

        Suggestions result = capped.suggest(List.of(item), 100_000);

        assertThat(result.expansions()).isEmpty();
    }

    /** 상한 <b>이내</b>로 올릴 여지가 있으면 그대로 제안한다 — 상한이 확장을 전부 막는 것이 아니다. */
    @Test
    void shouldStillRaiseWithinTheCeiling() {
        TargetItem item =
                new TargetItem(
                        "X.ROOM",
                        Priority.MUST,
                        9_000,
                        false,
                        new AxisLevels(4, 2, 5, 0), // I는 2 → 3으로 올릴 여지가 있다 (상한 3)
                        new AxisLevels(3, 2, 5, 0), // K가 현재 목표 미달이라 후보가 된다
                        10);

        Suggestions result = capped.suggest(List.of(item), 100_000);

        assertThat(result.expansions()).hasSize(1);
        // planning이 가장 낮은 축은 I(2)다. 상한 3 이내이므로 실제로 공부가 는다.
        assertThat(result.expansions().getFirst().axis()).isEqualTo(SkillAxis.IMPLEMENTATION);
        assertThat(result.expansions().getFirst().newTarget()).isEqualTo(3);
    }

    /**
     * <b>{@code reduction} 수정</b>. 상한 위의 목표를 낮춰 봐야 <b>지금 필요한 시간이 줄지 않는다.</b> I 목표 5를 4로 낮춰도 상한이 3이면
     * 0분 절감이다.
     */
    @Test
    void shouldNotReduceATargetThatSitsAboveTheCeiling() {
        TargetItem item =
                new TargetItem(
                        "X.ABOVE",
                        Priority.MUST,
                        9_000,
                        false,
                        new AxisLevels(3, 5, 3, 0), // I 목표 5, 상한 3
                        new AxisLevels(3, 3, 3, 0), // I는 상한까지 이미 닿았다
                        10_000);

        Suggestions result = capped.suggest(List.of(item), 1);

        assertThat(result.reductions()).noneMatch(r -> r.axis() == SkillAxis.IMPLEMENTATION);
    }

    private static void assertOneDirection(Suggestions result) {
        boolean reducing = !result.defers().isEmpty() || !result.reductions().isEmpty();
        boolean expanding = !result.expansions().isEmpty();
        assertThat(reducing && expanding).as("reduce and expand are exclusive").isFalse();
    }

    /** I축 gap 1짜리 MUST 항목의 필요 시간이 {@code minutes}가 되게 한다: step을 찾는다. */
    private TargetItem mustWithRequired(String code, int minutes) {
        return withRequired(code, Priority.MUST, 5_000, false, minutes);
    }

    private TargetItem restorable(String code, int importanceBp, int minutes) {
        return withRequired(code, Priority.SHOULD, importanceBp, true, minutes);
    }

    private TargetItem withRequired(
            String code, Priority priority, int importanceBp, boolean deferred, int minutes) {
        List<AxisLevels> shapes =
                List.of(
                        new AxisLevels(0, 1, 0, 0),
                        new AxisLevels(1, 0, 0, 0),
                        new AxisLevels(0, 0, 0, 1),
                        new AxisLevels(0, 0, 1, 0));
        for (AxisLevels target : shapes) {
            for (int step = 1; step < 100_000; step++) {
                if (evaluator.requiredMinutes(target, AxisLevels.ZERO, step) == minutes) {
                    return new TargetItem(
                            code, priority, importanceBp, deferred, target, AxisLevels.ZERO, step);
                }
            }
        }
        throw new IllegalArgumentException("no step gives " + minutes + " minutes");
    }

    static Stream<Arguments> vectors() {
        return VectorLoader.yamlRows(VECTOR_FILE).stream()
                .map(
                        row ->
                                Arguments.of(
                                        row.get("id"),
                                        row.get("effective"),
                                        items(row),
                                        map(row, "expected")));
    }

    private static List<TargetItem> items(Map<String, Object> row) {
        List<TargetItem> items = new ArrayList<>();
        for (Object value : (List<?>) row.get("items")) {
            Map<String, Object> item = cast(value);
            items.add(
                    new TargetItem(
                            (String) item.get("code"),
                            Priority.valueOf((String) item.get("priority")),
                            (Integer) item.get("importanceBp"),
                            (Boolean) item.get("deferred"),
                            DeadlineRiskEvaluatorTest.levels((String) item.get("target")),
                            DeadlineRiskEvaluatorTest.levels((String) item.get("planning")),
                            (Integer) item.get("step")));
        }
        return items;
    }

    private static List<TargetReduction> reductions(Map<String, Object> expected) {
        List<TargetReduction> reductions = new ArrayList<>();
        for (Object value : (List<?>) expected.get("reductions")) {
            Map<String, Object> reduction = cast(value);
            reductions.add(
                    new TargetReduction(
                            (String) reduction.get("code"),
                            SkillAxis.valueOf((String) reduction.get("axis")),
                            (Integer) reduction.get("currentTarget"),
                            (Integer) reduction.get("newTarget"),
                            (Integer) reduction.get("planningLevel"),
                            (Integer) reduction.get("savedMinutes")));
        }
        return reductions;
    }

    private static List<Expansion> expansions(
            Map<String, Object> expected, List<TargetItem> items) {
        List<Expansion> expansions = new ArrayList<>();
        for (Object value : (List<?>) expected.get("expansions")) {
            Map<String, Object> expansion = cast(value);
            String code = (String) expansion.get("code");
            int importance =
                    items.stream()
                            .filter(item -> item.skillCode().equals(code))
                            .findFirst()
                            .orElseThrow()
                            .practicalImportanceBp();
            String axis = (String) expansion.get("axis");
            expansions.add(
                    new Expansion(
                            ExpansionKind.valueOf((String) expansion.get("kind")),
                            code,
                            Priority.valueOf((String) expansion.get("priority")),
                            importance,
                            axis == null ? null : SkillAxis.valueOf(axis),
                            (Integer) expansion.get("currentTarget"),
                            (Integer) expansion.get("newTarget"),
                            (Integer) expansion.get("addedMinutes")));
        }
        return expansions;
    }

    private static RiskEstimate estimate(Map<String, Object> values) {
        return new RiskEstimate(
                integer(values, "requiredMust"),
                integer(values, "requiredShould"),
                integer(values, "ratioBp"),
                RiskLevel.valueOf((String) values.get("risk")),
                0);
    }

    private static Integer integer(Map<String, Object> values, String key) {
        return (Integer) values.get(key);
    }

    private static Map<String, Object> map(Map<String, Object> values, String key) {
        return cast(values.get(key));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }
}
