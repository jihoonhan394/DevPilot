package com.devpilot.skill.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.skill.domain.LearningStageEvaluator.StageCompletion;
import com.devpilot.skill.domain.LearningStageEvaluator.StageEvent;
import com.devpilot.skill.domain.LearningStageEvaluator.StageEventType;
import com.devpilot.testsupport.UnitTest;
import com.devpilot.testsupport.VectorLoader;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** docs/06 §5.11의 test vector 전부 (ST-V1~ST-V12). */
@UnitTest
class LearningStageEvaluatorTest {

    private static final String VECTOR_FILE = "06-05-learning-stage.yaml";

    /** vector의 {@code at}(1 = 하루째)을 옮기는 기준. 절대 시각은 판정에 쓰이지 않는다 — 순서만 본다. */
    private static final Instant DAY_ZERO = Instant.parse("2026-01-01T00:00:00Z");

    /** docs/06 §5.11 vectors: {@code devpilot.planner.weights.stage-gap = 0.005}. */
    private static final int STAGE_GAP_WEIGHT_MICRO = 5_000;

    private final LearningStageEvaluator evaluator = new LearningStageEvaluator();

    @ParameterizedTest(name = "[{index}] {0} {1}")
    @MethodSource("vectors")
    void shouldMatchVectorWhenStagesAreEvaluated(
            String id, String caseName, Map<String, Object> row) {
        List<StageCompletion> stages = evaluator.evaluate(events(row));

        assertThat(stages).as(id + " 항상 6칸이다").hasSize(6);
        assertThat(stages.stream().map(StageCompletion::stage))
                .as(id + " 선언 순서")
                .containsExactly(LearningStage.values());
        assertThat(completedNames(stages))
                .as(id + " " + caseName)
                .isEqualTo(strings(row, "completed"));
        assertThat(LearningStageEvaluator.stageGap(stages))
                .as(id + " stageGap")
                .isEqualTo(row.get("stageGap"));
        assertThat(stageGapBonus(stages)).as(id + " stageGapBonus").isEqualTo(row.get("bonus"));
    }

    /** ST-3: 같은 칸을 여러 번 채워도 가장 이른 기록의 시각이 남는다. */
    @Test
    void shouldKeepTheEarliestRecordOfAStage() {
        Instant early = DAY_ZERO.plus(Duration.ofDays(1));
        Instant late = DAY_ZERO.plus(Duration.ofDays(9));

        List<StageCompletion> stages =
                evaluator.evaluate(
                        List.of(
                                StageEvent.task(late, "CHALLENGE", null),
                                StageEvent.task(early, "PROJECT_TASK", null)));

        assertThat(stages.getFirst().completedAt()).isEqualTo(early);
    }

    /** ST-1: 완료가 아닌 칸은 시각을 지어내지 않는다. */
    @Test
    void shouldLeaveCompletedAtNullForAnUnfinishedStage() {
        List<StageCompletion> stages = evaluator.evaluate(List.of());

        assertThat(stages).allMatch(stage -> !stage.completed() && stage.completedAt() == null);
    }

    static Stream<Arguments> vectors() {
        return VectorLoader.yamlRows(VECTOR_FILE).stream()
                .map(row -> Arguments.of(row.get("id"), row.get("case"), withBonus(row)));
    }

    /** yaml의 {@code stageGapBonus}를 assertion이 읽는 이름으로 옮긴다. */
    private static Map<String, Object> withBonus(Map<String, Object> row) {
        row.put("bonus", row.get("stageGapBonus"));
        return row;
    }

    /** docs/06 §5.4: {@code floorDiv(stageGap × weight, 1_000_000)}. */
    private static int stageGapBonus(List<StageCompletion> stages) {
        return Math.toIntExact(
                Math.floorDiv(
                        (long) LearningStageEvaluator.stageGap(stages) * STAGE_GAP_WEIGHT_MICRO,
                        1_000_000));
    }

    private static List<String> completedNames(List<StageCompletion> stages) {
        return stages.stream()
                .filter(StageCompletion::completed)
                .map(stage -> stage.stage().name())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<StageEvent> events(Map<String, Object> row) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) row.get("events");
        return rows == null
                ? List.of()
                : rows.stream().map(LearningStageEvaluatorTest::event).toList();
    }

    private static StageEvent event(Map<String, Object> row) {
        StageEventType type = StageEventType.valueOf((String) row.get("type"));
        Instant at = DAY_ZERO.plus(Duration.ofDays(((Number) row.get("at")).longValue()));
        return switch (type) {
            case TASK_COMPLETED ->
                    StageEvent.task(
                            at,
                            (String) row.get("taskType"),
                            (Boolean) row.get("explainedToPerson"));
            case REDO_COMPLETED -> StageEvent.redo(at, Boolean.TRUE.equals(row.get("withoutAi")));
            default -> StageEvent.of(type, at);
        };
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Map<String, Object> row, String key) {
        List<String> values = (List<String>) row.get(key);
        return values == null ? List.of() : List.copyOf(values);
    }
}
