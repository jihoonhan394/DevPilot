package com.devpilot.today.application;

import com.devpilot.plan.domain.RiskLevel;
import com.devpilot.today.domain.EnergyLevel;
import com.devpilot.today.domain.ReasonCode;
import com.devpilot.today.domain.TaskStatus;
import com.devpilot.today.domain.TaskType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 오늘 계획 (docs/05 §8.1 {@code TodayView}).
 *
 * @param deadlineRisk 생성 요청 시점 risk. 학습 목표가 없으면 null
 * @param mainTask 활성 main(PLANNED·IN_PROGRESS), 없으면 sort_order가 가장 큰 main. main이 하나도 없으면 null
 * @param reviewTask REVIEW 과제가 없으면 null
 * @param earlierMainTasks mainTask를 뺀 같은 날의 다른 학습 과제 — 재생성이 남긴 지난 main과 §5.6의 추가 과제 (sortOrder
 *     ASC). REVIEW는 들어가지 않는다
 * @param tipExperiment "해 볼게요"로 표시한 팁의 실험 후보 1건 (docs/06 §5.12 TIP-6). 없으면 null. <b>과제가 아니다</b> —
 *     planner에 들어가지 않는다
 */
public record TodayView(
        UUID dailyPlanId,
        LocalDate planDate,
        int availableMinutes,
        EnergyLevel energyLevel,
        @Nullable RiskLevel deadlineRisk,
        boolean comebackMode,
        int generationCount,
        Instant generatedAt,
        @Nullable MainTaskView mainTask,
        @Nullable ReviewTaskView reviewTask,
        List<MainTaskView> earlierMainTasks,
        @Nullable TipExperimentView tipExperiment) {

    public TodayView {
        earlierMainTasks = List.copyOf(earlierMainTasks);
    }

    /**
     * main 과제.
     *
     * @param challengeId CHALLENGE일 때만
     * @param sideProjectId PROJECT_TASK일 때만 (생성 시점 고정, 프로젝트가 삭제되면 null)
     * @param readingKey READ_CODE일 때만
     * @param checklist 과제의 taskType·skill에 맞는 목록. 맞는 것이 없거나 skill이 없으면 null
     * @param reasons 1~3개 (docs/06 §5.8)
     */
    public record MainTaskView(
            UUID id,
            TaskType taskType,
            @Nullable UUID skillId,
            @Nullable String skillCode,
            @Nullable String skillName,
            @Nullable UUID milestoneId,
            @Nullable UUID challengeId,
            @Nullable UUID sideProjectId,
            @Nullable String readingKey,
            @Nullable UUID redoSourceTaskId,
            @Nullable TaskType redoSourceTaskType,
            @Nullable Boolean redoWithoutAi,
            String title,
            @Nullable String description,
            @Nullable ChecklistView checklist,
            int estimatedMinutes,
            TaskStatus status,
            List<ReasonView> reasons,
            @Nullable Instant completedAt,
            long version) {

        public MainTaskView {
            reasons = List.copyOf(reasons);
        }
    }

    /** reason 1개 (문구는 저장된 변수로 응답 때 채운다). */
    public record ReasonView(ReasonCode code, String text) {}

    /**
     * 과제 체크리스트 (docs/05 §8.1, docs/19 §3.11). 저장하지 않고 응답을 만들 때 콘텐츠 최신본에서 읽는다 — 지난 과제에도 지금 글이 붙는다.
     *
     * @param before 시작 전 확인 3~5개
     * @param after 끝내기 전 확인 3~5개
     */
    public record ChecklistView(String key, List<String> before, List<String> after) {

        public ChecklistView {
            before = List.copyOf(before);
            after = List.copyOf(after);
        }
    }

    /**
     * REVIEW 과제.
     *
     * @param dueReviewCount 조회 시점의 남은 due 수 (docs/06 §6.5 대상, cap 적용)
     */
    public record ReviewTaskView(
            UUID id, int estimatedMinutes, int dueReviewCount, TaskStatus status, long version) {}
}
