package com.devpilot.skill.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 학습 단계 6칸 판정 (docs/06 §5.11, ADR-042).
 *
 * <p>한 skill을 <b>만들고 → 개념을 읽고 → 실제 코드를 읽고 → 설명하고 → 복습하고 → AI 없이 다시 만들기</b>까지 한 바퀴 돌아야 그 skill을 할 수
 * 있다고 본다. 만들기가 먼저인 이유는 무엇을 만들다 막혀 봐야 읽을 이유가 생기기 때문이다.
 *
 * <p>순수 규칙이다 — 저장소를 모르고 시계도 없다(ST-1: 저장하지 않는다). 입력은 그 사용자·그 skill의 학습 이벤트뿐이다.
 *
 * <p>순서는 <b>표시 순서</b>이고 선행 조건이 아니다(ST-2). 3번을 건너뛰고 4번을 채울 수 있고, 그래도 4번은 완료로 본다.
 */
public final class LearningStageEvaluator {

    /** 여섯 칸을 여섯 칸으로 나눈 micro 값. bp/micro 정수만 쓴다 (docs/06 §1). */
    private static final int FULL_GAP = 1_000_000;

    private static final int STAGE_COUNT = 6;

    /**
     * 선언 순서 6칸. 완료가 아니면 {@code completedAt}이 null이다.
     *
     * @param events 그 사용자·그 skill의 학습 이벤트. 순서는 상관없다 — 단계마다 가장 이른 것을 고른다(ST-3)
     */
    public List<StageCompletion> evaluate(List<StageEvent> events) {
        Map<LearningStage, Instant> earliest = new EnumMap<>(LearningStage.class);
        for (StageEvent event : events) {
            stageOf(event)
                    .ifPresent(
                            stage ->
                                    earliest.merge(
                                            stage,
                                            event.occurredAt(),
                                            (left, right) -> left.isBefore(right) ? left : right));
        }
        List<StageCompletion> stages = new ArrayList<>(STAGE_COUNT);
        for (LearningStage stage : LearningStage.values()) {
            Instant completedAt = earliest.get(stage);
            stages.add(new StageCompletion(stage, completedAt != null, completedAt));
        }
        return List.copyOf(stages);
    }

    /**
     * ST-5: {@code floorDiv((6 − 완료 수) × 1_000_000, 6)}. 0칸이면 {@code 1_000_000}, 6칸이면 {@code 0}이다.
     *
     * <p>정수 나눗셈이다 — 비율에 {@code double}을 쓰지 않는다 (docs/06 §1).
     */
    public static int stageGap(List<StageCompletion> stages) {
        long completed = stages.stream().filter(StageCompletion::completed).count();
        return Math.toIntExact(Math.floorDiv((STAGE_COUNT - completed) * FULL_GAP, STAGE_COUNT));
    }

    /** 아직 아무것도 하지 않은 skill의 값. 기록을 읽지 못했을 때가 아니라 <b>기록이 없을 때</b> 쓴다. */
    public static int emptyStageGap() {
        return FULL_GAP;
    }

    /**
     * 이 이벤트가 채우는 단계. 채우지 않으면 empty다.
     *
     * <p>{@code REDO_COMPLETED}인데 {@code withoutAi = false}이면 아무 단계도 채우지 않는다 — AI를 보고 다시 만든 것은 혼자
     * 만든 증거가 아니다(RE-8). 같은 이유로 {@code EXPLAIN} 과제는 {@code explainedToPerson = true}일 때만 센다.
     */
    private static Optional<LearningStage> stageOf(StageEvent event) {
        return switch (event.type()) {
            case RUBBER_DUCK_COMPLETED -> Optional.of(LearningStage.EXPLAIN);
            case REVIEW_ANSWERED -> Optional.of(LearningStage.REVIEW);
            case REDO_COMPLETED ->
                    Boolean.TRUE.equals(event.withoutAi())
                            ? Optional.of(LearningStage.REDO)
                            : Optional.empty();
            case TASK_COMPLETED -> stageOfTask(event);
        };
    }

    private static Optional<LearningStage> stageOfTask(StageEvent event) {
        String taskType = event.taskType();
        if (taskType == null) {
            return Optional.empty();
        }
        return switch (taskType) {
            case "CHALLENGE", "PROJECT_TASK" -> Optional.of(LearningStage.BUILD);
            case "READING" -> Optional.of(LearningStage.READ_CONCEPT);
            case "READ_CODE" -> Optional.of(LearningStage.READ_CODE);
            case "EXPLAIN" ->
                    Boolean.TRUE.equals(event.explainedToPerson())
                            ? Optional.of(LearningStage.EXPLAIN)
                            : Optional.empty();
            default -> Optional.empty();
        };
    }

    /** 판정이 보는 이벤트 종류 (docs/06 §5.11의 "보는 이벤트" 표). 나머지 이벤트는 단계와 무관하다. */
    public enum StageEventType {
        TASK_COMPLETED,
        RUBBER_DUCK_COMPLETED,
        REVIEW_ANSWERED,
        REDO_COMPLETED
    }

    /**
     * 판정 입력 1건.
     *
     * @param taskType {@code TASK_COMPLETED}에만 있는 값
     * @param explainedToPerson {@code EXPLAIN} 과제에만 있는 값
     * @param withoutAi {@code REDO_COMPLETED}에만 있는 값
     */
    public record StageEvent(
            StageEventType type,
            Instant occurredAt,
            @Nullable String taskType,
            @Nullable Boolean explainedToPerson,
            @Nullable Boolean withoutAi) {

        public static StageEvent task(
                Instant occurredAt, String taskType, @Nullable Boolean explainedToPerson) {
            return new StageEvent(
                    StageEventType.TASK_COMPLETED, occurredAt, taskType, explainedToPerson, null);
        }

        public static StageEvent of(StageEventType type, Instant occurredAt) {
            return new StageEvent(type, occurredAt, null, null, null);
        }

        public static StageEvent redo(Instant occurredAt, boolean withoutAi) {
            return new StageEvent(StageEventType.REDO_COMPLETED, occurredAt, null, null, withoutAi);
        }
    }

    /**
     * 한 칸.
     *
     * @param completedAt 그 칸을 채운 가장 이른 기록의 시각 (ST-3). 완료가 아니면 null
     */
    public record StageCompletion(
            LearningStage stage, boolean completed, @Nullable Instant completedAt) {}
}
