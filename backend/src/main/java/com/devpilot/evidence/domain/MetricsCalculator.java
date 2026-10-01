package com.devpilot.evidence.domain;

import com.devpilot.common.math.FixedPointMath;
import com.devpilot.learning.domain.ThinkingAxis;
import com.devpilot.plan.domain.RiskLevel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * 주간 지표 (docs/06 §12). 순수 규칙이다 — 저장소도 시계도 모르고, 세는 일은 호출자가 한다.
 *
 * <p>비율은 모두 bp·milli 정수다(§1 N-1). 분모가 0이면 <b>0이 아니라 null</b>이다 — "0%"와 "잴 것이 없었다"는 다른 말이고, 0으로 적으면
 * 아무것도 안 한 주가 실패한 주로 보인다.
 *
 * <p>§12의 네 줄({@code learnedUnitCount}·{@code completedLessonCount}·{@code
 * taughtBeforeTestedBp}·{@code topicSwitchesPerWeekMilli})은 <b>"이 도구를 쓰면 실제로 느는가"</b>를 재려고 둔 것이다.
 * 나머지는 대부분 AI 평가 결과의 비율이라 문제를 안 풀면 값이 아예 없고, 배우는 구간이 통째로 안 보인다.
 */
public final class MetricsCalculator {

    /** 기본 기간 (docs/06 §12). */
    public static final int DEFAULT_DAYS = 28;

    /** 약한 축으로 꼽는 최대 개수 (§12). */
    private static final int WEAK_AXIS_LIMIT = 3;

    /** 그보다 적게 본 축은 꼽지 않는다 — 두 번 보고 "약하다"고 말할 수 없다. */
    private static final int WEAK_AXIS_MIN_OBSERVATIONS = 3;

    private static final int DAYS_PER_WEEK = 7;
    private static final int MILLI = 1_000;

    public Metrics calculate(MetricsInput input) {
        return new Metrics(
                input.completedSessions(),
                input.studyMinutes(),
                ratioBp(input.independentSolveCount(), input.ratedAttemptCount()),
                averageMilli(input.hintOrdinals()),
                ratioBp(input.recallGoodOrEasyCount(), input.reviewAnswerCount()),
                input.selfFoundRiskCount(),
                input.acceptedEvidenceCount(),
                input.completedRubberDuckSessions(),
                input.projectNoteCount(),
                input.independentRedoCount(),
                input.solvedUnits().size(),
                input.completedLessonCount(),
                ratioBp(input.taughtBeforeTestedSkills(), input.testedSkillsWithLesson()),
                topicSwitchesPerWeekMilli(input.mainSkillCodesByDay(), input.days()),
                input.riskLevel(),
                input.ratioBp(),
                weakAxes(input.axisObservations()),
                input.requirementCoverageBp());
    }

    /** {@code floorDiv(count × 10_000, total)}. 분모가 0이면 null. */
    static @Nullable Integer ratioBp(int count, int total) {
        if (total <= 0) {
            return null;
        }
        return Math.toIntExact(
                FixedPointMath.floorDiv(
                        Math.multiplyExact((long) count, FixedPointMath.BP_SCALE), total));
    }

    /** {@code floorDiv(Σ ordinal × 1000, n)}. 빈 목록이면 null. */
    static @Nullable Integer averageMilli(List<Integer> ordinals) {
        if (ordinals.isEmpty()) {
            return null;
        }
        long sum = 0;
        for (int ordinal : ordinals) {
            sum = Math.addExact(sum, ordinal);
        }
        return Math.toIntExact(
                FixedPointMath.floorDiv(Math.multiplyExact(sum, MILLI), ordinals.size()));
    }

    /**
     * 주제가 바뀐 횟수 ÷ 주 수 (§12, §5.13). 낮을수록 한 주제를 끝까지 간 것이다.
     *
     * <p>{@code main}이 없던 plan-day는 <b>목록에서 빠져 있고</b>, 건너뛴 채로 앞뒤를 잇는다 — 쉰 날이 주제를 바꾼 것으로 세어지면 안 된다.
     *
     * <p>단위가 milli인 이유: 정수 "회/주"로 자르면 5회를 4주로 나눈 1회와 8회를 나눈 2회가 같은 칸에 들어가 추세가 안 보인다. {@code
     * averageHintLevelMilli}와 같은 규칙이다(§1 N-6).
     */
    static int topicSwitchesPerWeekMilli(List<String> mainSkillCodesByDay, int days) {
        int switches = 0;
        for (int index = 1; index < mainSkillCodesByDay.size(); index++) {
            if (!mainSkillCodesByDay.get(index).equals(mainSkillCodesByDay.get(index - 1))) {
                switches++;
            }
        }
        // 며칠짜리 기간도 한 주로 센다 — 0으로 나누지 않고, 사흘에 2번은 "주 2번"보다 잦다
        int weeks = Math.max(1, ceilDiv(Math.max(days, 1), DAYS_PER_WEEK));
        return Math.toIntExact(
                FixedPointMath.floorDiv(Math.multiplyExact((long) switches, MILLI), weeks));
    }

    /**
     * {@code MISSED} 비율 상위 3개 (§12). 관찰이 {@value #WEAK_AXIS_MIN_OBSERVATIONS}개 미만인 축은 빼고, 동률은
     * {@code ThinkingAxis} 선언 순서로 가른다 — 같은 입력이면 같은 목록이어야 한다.
     */
    static List<ThinkingAxis> weakAxes(Map<ThinkingAxis, AxisObservation> observations) {
        List<Map.Entry<ThinkingAxis, AxisObservation>> ranked =
                new ArrayList<>(observations.entrySet());
        ranked.removeIf(entry -> entry.getValue().total() < WEAK_AXIS_MIN_OBSERVATIONS);
        ranked.sort(
                Comparator.<Map.Entry<ThinkingAxis, AxisObservation>>comparingInt(
                                entry -> -missedBp(entry.getValue()))
                        .thenComparing(Map.Entry::getKey));
        List<ThinkingAxis> axes = new ArrayList<>();
        for (Map.Entry<ThinkingAxis, AxisObservation> entry :
                ranked.subList(0, Math.min(WEAK_AXIS_LIMIT, ranked.size()))) {
            axes.add(entry.getKey());
        }
        return List.copyOf(axes);
    }

    private static int missedBp(AxisObservation observation) {
        Integer ratio = ratioBp(observation.missed(), observation.total());
        return ratio == null ? 0 : ratio;
    }

    private static int ceilDiv(int value, int divisor) {
        return Math.toIntExact(Math.ceilDiv((long) value, divisor));
    }

    /** 한 축의 관찰 (§12 {@code weakThinkingAxes}). */
    public record AxisObservation(int missed, int total) {}

    /**
     * 세어 온 값 (docs/06 §12). 세는 일은 모듈마다 다르므로 호출자가 맡고, 이 record는 <b>계산에 필요한 것만</b> 받는다.
     *
     * @param days 기간 plan-day 수 (기본 {@value #DEFAULT_DAYS})
     * @param hintOrdinals 평가 완료 attempt·close된 finding·review_answer의 {@code maxHintLevel} ordinal
     * @param solvedUnits 기간 내 {@code UNIT_SOLVED}의 서로 다른 {@code (lessonKey, unitKey)}
     * @param completedLessonCount 모든 단위를 한 번 이상 마친 노트 수. 기간 누적이 아니라 <b>시점 값</b>이다
     * @param testedSkillsWithLesson 기간 내 제출이 있고 노트도 있는 skill 수 (노트가 없으면 가르칠 것이 없었다 — 분모에서 뺀다)
     * @param taughtBeforeTestedSkills 그중 <b>첫 제출보다 앞선</b> 같은 skill 노트 기록이 있는 skill 수
     * @param mainSkillCodesByDay main이 있던 plan-day의 skill code, 시간순. 없던 날은 빠져 있다
     * @param requirementCoverageBp 로드맵 비교가 없으면 null (S7 전)
     */
    public record MetricsInput(
            int days,
            int completedSessions,
            long studyMinutes,
            int independentSolveCount,
            int ratedAttemptCount,
            List<Integer> hintOrdinals,
            int recallGoodOrEasyCount,
            int reviewAnswerCount,
            int selfFoundRiskCount,
            int acceptedEvidenceCount,
            int completedRubberDuckSessions,
            int projectNoteCount,
            int independentRedoCount,
            Set<UnitRef> solvedUnits,
            int completedLessonCount,
            int taughtBeforeTestedSkills,
            int testedSkillsWithLesson,
            List<String> mainSkillCodesByDay,
            @Nullable RiskLevel riskLevel,
            @Nullable Integer ratioBp,
            Map<ThinkingAxis, AxisObservation> axisObservations,
            @Nullable Integer requirementCoverageBp) {

        public MetricsInput {
            hintOrdinals = List.copyOf(hintOrdinals);
            solvedUnits = Set.copyOf(solvedUnits);
            mainSkillCodesByDay = List.copyOf(mainSkillCodesByDay);
            axisObservations = Map.copyOf(axisObservations);
        }
    }

    /** 노트의 학습 단위 하나 (docs/04 §6 {@code UNIT_SOLVED}). 같은 단위를 여러 번 풀어도 하나다. */
    public record UnitRef(String lessonKey, String unitKey) {}

    /**
     * 계산 결과 ({@code weekly_review.metrics_json}, docs/04 §5.7).
     *
     * @param independentSolveRateBp 분모가 0이면 null
     * @param averageHintLevelMilli 볼 기록이 없으면 null
     * @param recallSuccessRateBp 복습 답변이 없으면 null
     * @param taughtBeforeTestedBp 노트가 있는 skill을 하나도 풀지 않았으면 null. <b>100%가 목표가 아니다</b> — 이미 아는
     *     skill은 노트를 건너뛰는 것이 맞고(TH-5), 낮아지는 추세가 신호다
     * @param topicSwitchesPerWeekMilli 높으면 매일 주제가 바뀌고 있다 (§5.13)
     */
    public record Metrics(
            int completedSessions,
            long studyMinutes,
            @Nullable Integer independentSolveRateBp,
            @Nullable Integer averageHintLevelMilli,
            @Nullable Integer recallSuccessRateBp,
            int selfFoundRiskCount,
            int acceptedEvidenceCount,
            int completedRubberDuckSessions,
            int projectNoteCount,
            int independentRedoCount,
            int learnedUnitCount,
            int completedLessonCount,
            @Nullable Integer taughtBeforeTestedBp,
            int topicSwitchesPerWeekMilli,
            @Nullable RiskLevel riskLevel,
            @Nullable Integer ratioBp,
            List<ThinkingAxis> weakThinkingAxes,
            @Nullable Integer requirementCoverageBp) {

        public Metrics {
            weakThinkingAxes = List.copyOf(weakThinkingAxes);
        }
    }
}
