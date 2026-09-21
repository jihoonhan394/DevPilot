package com.devpilot.today.application;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.review.application.ReviewQueryService;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.today.application.TodayView.MainTaskView;
import com.devpilot.today.application.TodayView.ReasonView;
import com.devpilot.today.application.TodayView.ReviewTaskView;
import com.devpilot.today.domain.DailyPlan;
import com.devpilot.today.domain.LearningTask;
import com.devpilot.today.domain.ReasonTemplates;
import com.devpilot.today.domain.ReasonTemplates.ReasonParams;
import com.devpilot.today.domain.ScoreBreakdown;
import com.devpilot.today.domain.TaskStatus;
import com.devpilot.today.domain.TaskType;
import com.devpilot.today.infrastructure.DailyPlanRepository;
import com.devpilot.today.infrastructure.LearningTaskRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘 계획 조회 (docs/05 §8.3, BL-TDY-08)와 다른 모듈(dashboard)에 공개하는 오늘 요약. 조회 시점의 plan-day로 daily plan을
 * 찾는다 — {@code dayStartHour}가 지나면 전날 계획은 돌려주지 않는다(AC-17). reason 문구는 저장된 변수로 응답 때 채운다.
 *
 * <p>{@code earlierMainTasks}에는 {@code mainTask}를 뺀 그날의 다른 학습 과제가 모두 들어간다(docs/05 §8.1) — 재생성이 남긴
 * 지난 main과 §5.6의 추가 과제다. {@code REVIEW}만 {@code reviewTask}로 따로 나간다.
 */
@Service
@Transactional(readOnly = true)
public class TodayQueryService {

    private final DailyPlanRepository dailyPlanRepository;
    private final LearningTaskRepository learningTaskRepository;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final ReviewQueryService reviewQueryService;
    private final Clock clock;

    public TodayQueryService(
            DailyPlanRepository dailyPlanRepository,
            LearningTaskRepository learningTaskRepository,
            SkillCatalogQueryService skillCatalogQueryService,
            ReviewQueryService reviewQueryService,
            Clock clock) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.learningTaskRepository = learningTaskRepository;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.reviewQueryService = reviewQueryService;
        this.clock = clock;
    }

    /** {@code GET /today}. 오늘 계획이 없으면 404 {@code TODAY_NOT_GENERATED}. */
    public TodayView get(CurrentUser user) {
        LocalDate today =
                PlanDayCalculator.planDate(clock.instant(), user.zoneId(), user.dayStartHour());
        DailyPlan plan =
                dailyPlanRepository
                        .findByUserIdAndPlanDate(user.userId(), today)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.TODAY_NOT_GENERATED,
                                                "today is not generated"));
        return toView(plan, user.zoneId(), user.dayStartHour());
    }

    /**
     * 러버덕 {@code CODE_READING} 대상 확인 (docs/05 §9.5 표). {@code READ_CODE}가 아니거나 타 사용자·없는 과제는 빈 값.
     */
    public Optional<ReadCodeTaskRef> findReadCodeTask(UUID userId, UUID learningTaskId) {
        return learningTaskRepository
                .findByIdAndUserId(learningTaskId, userId)
                .filter(task -> task.getTaskType() == TaskType.READ_CODE)
                .map(
                        task ->
                                new ReadCodeTaskRef(
                                        task.getId(),
                                        task.getSkillId(),
                                        task.getReadingKey(),
                                        task.getTitle()));
    }

    /** 오늘 요약 (dashboard, docs/05 §13.1 {@code todaySummary}). 오늘 계획이 없으면 empty. */
    public Optional<TodaySummary> summary(UUID userId, LocalDate today) {
        return dailyPlanRepository
                .findByUserIdAndPlanDate(userId, today)
                .map(
                        plan -> {
                            List<LearningTask> tasks = tasks(plan);
                            LearningTask main =
                                    selectMain(tasks.stream().filter(LearningTask::isMain).toList())
                                            .orElse(null);
                            LearningTask review = reviewTask(tasks).orElse(null);
                            return TodaySummary.of(main, review);
                        });
    }

    /** 응답 view. 같은 모듈의 생성 서비스가 저장 직후 응답을 만들 때도 쓴다. */
    TodayView toView(DailyPlan plan, ZoneId zone, int dayStartHour) {
        List<LearningTask> tasks = tasks(plan);
        List<LearningTask> studyTasks =
                tasks.stream().filter(task -> task.getTaskType() != TaskType.REVIEW).toList();
        LearningTask main =
                selectMain(studyTasks.stream().filter(LearningTask::isMain).toList()).orElse(null);
        Set<UUID> skillIds = new HashSet<>();
        studyTasks.forEach(
                task -> {
                    if (task.getSkillId() != null) {
                        skillIds.add(task.getSkillId());
                    }
                });
        Map<UUID, SkillRef> skills = skillCatalogQueryService.findRefs(skillIds);
        List<MainTaskView> earlier =
                studyTasks.stream()
                        .filter(task -> !task.equals(main))
                        .sorted(Comparator.comparingInt(LearningTask::getSortOrder))
                        .map(task -> toMainView(task, skills))
                        .toList();
        ReviewTaskView review =
                reviewTask(tasks)
                        .map(task -> toReviewView(task, plan, zone, dayStartHour))
                        .orElse(null);
        return new TodayView(
                plan.getId(),
                plan.getPlanDate(),
                plan.getAvailableMinutes(),
                plan.getEnergyLevel(),
                plan.getDeadlineRisk(),
                plan.isComebackMode(),
                plan.getGenerationCount(),
                plan.getGeneratedAt(),
                main == null ? null : toMainView(main, skills),
                review,
                earlier);
    }

    /**
     * docs/05 §8.1 mainTask 선택: PLANNED·IN_PROGRESS main(I-04로 최대 1개), 없으면 sort_order가 가장 큰 main.
     *
     * @param mains 같은 daily plan의 main 과제
     */
    static Optional<LearningTask> selectMain(List<LearningTask> mains) {
        Optional<LearningTask> active =
                mains.stream().filter(LearningTask::isActiveMain).findFirst();
        if (active.isPresent()) {
            return active;
        }
        return mains.stream().max(Comparator.comparingInt(LearningTask::getSortOrder));
    }

    /** plan-day의 main 과제 (§8.1 선택 규칙). 그날 계획이 없으면 empty. planner 입력({@code CONTINUATION})이 쓴다. */
    Optional<LearningTask> mainOf(UUID userId, LocalDate planDate) {
        return dailyPlanRepository
                .findByUserIdAndPlanDate(userId, planDate)
                .flatMap(
                        plan ->
                                selectMain(
                                        learningTaskRepository.findByDailyPlanIdInAndMainTrue(
                                                List.of(plan.getId()))));
    }

    private List<LearningTask> tasks(DailyPlan plan) {
        return learningTaskRepository.findByDailyPlanIdOrderBySortOrderAscIdAsc(plan.getId());
    }

    private static Optional<LearningTask> reviewTask(List<LearningTask> tasks) {
        return tasks.stream()
                .filter(task -> !task.isMain() && task.getTaskType() == TaskType.REVIEW)
                .findFirst();
    }

    private ReviewTaskView toReviewView(
            LearningTask task, DailyPlan plan, ZoneId zone, int dayStartHour) {
        int due =
                reviewQueryService
                        .dueSummary(plan.getUserId(), plan.getPlanDate(), zone, dayStartHour)
                        .totalDue();
        return new ReviewTaskView(
                task.getId(),
                task.getEstimatedMinutes(),
                Math.min(due, reviewQueryService.cap(plan.isComebackMode())),
                task.getStatus(),
                task.getVersion());
    }

    private static MainTaskView toMainView(LearningTask task, Map<UUID, SkillRef> skills) {
        SkillRef skill = task.getSkillId() == null ? null : skills.get(task.getSkillId());
        ScoreBreakdown breakdown = task.getScoreBreakdown();
        ReasonParams params = breakdown == null ? ReasonParams.EMPTY : breakdown.reasonParams();
        List<ReasonView> reasons =
                task.getReasonCodes().stream()
                        .map(code -> new ReasonView(code, ReasonTemplates.text(code, params)))
                        .toList();
        return new MainTaskView(
                task.getId(),
                task.getTaskType(),
                skill == null ? null : skill.code(),
                skill == null ? null : skill.name(),
                task.getMilestoneId(),
                task.getChallengeId(),
                task.getSideProjectId(),
                task.getReadingKey(),
                task.getTitle(),
                task.getDescription(),
                task.getEstimatedMinutes(),
                task.getStatus(),
                reasons,
                task.getCompletedAt(),
                task.getVersion());
    }

    /**
     * 러버덕 {@code CODE_READING} 대상 (docs/05 §9.5).
     *
     * @param readingKey {@code READ_CODE} 과제에는 항상 있다 (I-17)
     */
    public record ReadCodeTaskRef(
            UUID id, @Nullable UUID skillId, @Nullable String readingKey, String title) {}

    /**
     * 오늘 요약.
     *
     * @param mainTask docs/05 §8.1 선택 규칙의 main. 없으면 null
     * @param reviewTask REVIEW 과제. 없으면 null
     */
    public record TodaySummary(
            @Nullable LearningTaskSummary mainTask, @Nullable LearningTaskSummary reviewTask) {

        static TodaySummary of(@Nullable LearningTask main, @Nullable LearningTask review) {
            return new TodaySummary(LearningTaskSummary.of(main), LearningTaskSummary.of(review));
        }
    }

    /** dashboard가 쓰는 과제 요약 (entity를 모듈 밖으로 내보내지 않는다). */
    public record LearningTaskSummary(
            UUID id, TaskType taskType, String title, TaskStatus status, int estimatedMinutes) {

        static @Nullable LearningTaskSummary of(@Nullable LearningTask task) {
            if (task == null) {
                return null;
            }
            return new LearningTaskSummary(
                    task.getId(),
                    task.getTaskType(),
                    task.getTitle(),
                    task.getStatus(),
                    task.getEstimatedMinutes());
        }
    }
}
