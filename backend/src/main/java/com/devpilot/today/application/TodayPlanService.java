package com.devpilot.today.application;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.today.application.DailyPlanComposer.Composition;
import com.devpilot.today.domain.DailyPlan;
import com.devpilot.today.domain.EnergyLevel;
import com.devpilot.today.domain.LearningTask;
import com.devpilot.today.domain.ReadingFeedback;
import com.devpilot.today.domain.TaskProposalPolicy;
import com.devpilot.today.domain.TaskStatus;
import com.devpilot.today.domain.TaskType;
import com.devpilot.today.infrastructure.DailyPlanRepository;
import com.devpilot.today.infrastructure.LearningTaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Today 생성·재생성 (docs/05 §8.2, docs/06 §5.9, BL-TDY-07)과 과제 상태 변경 (docs/05 §8.4, BL-TDY-09). 한
 * 트랜잭션에서 처리한다. 재생성은 같은 daily plan의 PLANNED 과제를 지우고 flush한 뒤 새 과제를 넣는다. 동시 요청의 unique 위반({@code
 * unique (user_id, plan_date)}, {@code uq_learning_task_one_active_main})은 409 {@code
 * CONCURRENT_MODIFICATION}이다. AI를 호출하지 않는다.
 */
@Service
public class TodayPlanService {

    private final DailyPlanRepository dailyPlanRepository;
    private final LearningTaskRepository learningTaskRepository;
    private final DailyPlanComposer dailyPlanComposer;
    private final TodayQueryService todayQueryService;
    private final CodeReadingCompletionProvider codeReadingCompletionProvider;
    private final Clock clock;

    public TodayPlanService(
            DailyPlanRepository dailyPlanRepository,
            LearningTaskRepository learningTaskRepository,
            DailyPlanComposer dailyPlanComposer,
            TodayQueryService todayQueryService,
            CodeReadingCompletionProvider codeReadingCompletionProvider,
            Clock clock) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.learningTaskRepository = learningTaskRepository;
        this.dailyPlanComposer = dailyPlanComposer;
        this.todayQueryService = todayQueryService;
        this.codeReadingCompletionProvider = codeReadingCompletionProvider;
        this.clock = clock;
    }

    /**
     * {@code POST /today/generate}. 활성 plan이 없으면 404 {@code PLAN_NOT_FOUND}, 진행 중 main이 있으면 409
     * {@code TODAY_ALREADY_STARTED}, 완료한 main만 있으면 409 {@code TODAY_ALREADY_COMPLETED} ({@code
     * force = true}면 둘 다 진행).
     */
    @Transactional
    public TodayView generate(
            CurrentUser user, int availableMinutes, EnergyLevel energy, boolean force) {
        Instant now = clock.instant();
        LocalDate today = PlanDayCalculator.planDate(now, user.zoneId(), user.dayStartHour());
        Composition composition = dailyPlanComposer.compose(user, today, availableMinutes, energy);
        Optional<DailyPlan> existing =
                dailyPlanRepository.findByUserIdAndPlanDate(user.userId(), today);
        DailyPlan.Inputs inputs =
                new DailyPlan.Inputs(
                        composition.learningPlanId(),
                        availableMinutes,
                        energy,
                        composition.deadlineRisk(),
                        composition.comebackMode());
        DailyPlan plan;
        List<LearningTask> kept;
        if (existing.isPresent()) {
            plan = existing.get();
            kept = clearForRegeneration(plan, force);
            plan.regenerate(inputs, now);
        } else {
            plan = DailyPlan.create(user.userId(), today, inputs, now);
            kept = List.of();
        }
        List<LearningTask> added = newTasks(plan, user.userId(), composition, kept);
        try {
            dailyPlanRepository.saveAndFlush(plan);
            learningTaskRepository.saveAllAndFlush(added);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION, "concurrent today generation", exception);
        }
        return todayQueryService.toView(plan, user.zoneId(), user.dayStartHour());
    }

    /**
     * {@code PATCH /today/tasks/{taskId}} (docs/05 §8.4). 검사 순서: 404 → version 불일치 409 {@code
     * CONCURRENT_MODIFICATION} → 전이표 밖·RC-1 미충족 409 {@code INVALID_STATE_TRANSITION} → {@code
     * readingFeedback} 허용 여부 400 {@code VALUE_NOT_ALLOWED}. {@code SKIPPED → PLANNED} main은 같은
     * daily plan에 활성 main이 없을 때만 된다. {@code READ_CODE}의 {@code IN_PROGRESS → COMPLETED}는 그 과제를 대상으로
     * 한 {@code COMPLETED} 러버덕 세션이 있어야 한다(RC-1, docs/06 §9.5).
     */
    @Transactional
    public TaskStatusView updateTaskStatus(
            UUID userId,
            UUID taskId,
            TaskStatus status,
            @Nullable ReadingFeedback readingFeedback,
            long version) {
        LearningTask task =
                learningTaskRepository
                        .findByIdAndUserId(taskId, userId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "task not found"));
        if (task.getVersion() != version) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION, "task version does not match");
        }
        if (task.isMain()
                && task.getStatus() == TaskStatus.SKIPPED
                && status == TaskStatus.PLANNED) {
            boolean activeMainExists =
                    learningTaskRepository
                            .findByDailyPlanIdOrderBySortOrderAscIdAsc(task.getDailyPlanId())
                            .stream()
                            .anyMatch(LearningTask::isActiveMain);
            if (activeMainExists) {
                throw new ConflictException(
                        ErrorCode.INVALID_STATE_TRANSITION, "another main task is active");
            }
        }
        boolean readCodeCompletion =
                task.getTaskType() == TaskType.READ_CODE && status == TaskStatus.COMPLETED;
        if (readCodeCompletion
                && !codeReadingCompletionProvider.hasCompletedRubberDuck(userId, taskId)) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "code reading needs a completed rubber duck session");
        }
        task.changeStatus(status, clock.instant());
        if (readingFeedback != null && !readCodeCompletion) {
            throw new BusinessValidationException(
                    "reading feedback is not allowed for this task",
                    List.of(
                            ApiFieldError.of(
                                    "readingFeedback", FieldErrorCodes.VALUE_NOT_ALLOWED)));
        }
        task.recordReadingFeedback(readingFeedback);
        try {
            learningTaskRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION, "another main task is active", exception);
        }
        LocalDate planDate =
                dailyPlanRepository
                        .findById(task.getDailyPlanId())
                        .map(DailyPlan::getPlanDate)
                        .orElseThrow(() -> new IllegalStateException("task without daily plan"));
        return TaskStatusView.of(task, planDate);
    }

    /**
     * docs/06 §5.9 표: 진행 중 main → (force) DEFERRED / 완료 main만 → (force) 추가 main / 그 외 → 재생성. 어느 경우든
     * PLANNED 과제를 지우고 flush한다. 남은 과제를 돌려준다.
     */
    private List<LearningTask> clearForRegeneration(DailyPlan plan, boolean force) {
        List<LearningTask> tasks =
                learningTaskRepository.findByDailyPlanIdOrderBySortOrderAscIdAsc(plan.getId());
        Optional<LearningTask> inProgressMain =
                tasks.stream()
                        .filter(task -> task.isMain() && task.getStatus() == TaskStatus.IN_PROGRESS)
                        .findFirst();
        boolean completedMain =
                tasks.stream()
                        .anyMatch(
                                task -> task.isMain() && task.getStatus() == TaskStatus.COMPLETED);
        if (inProgressMain.isPresent()) {
            if (!force) {
                throw new ConflictException(
                        ErrorCode.TODAY_ALREADY_STARTED, "today main task already started");
            }
            inProgressMain.get().deferForRegeneration();
        } else if (completedMain && !force) {
            throw new ConflictException(
                    ErrorCode.TODAY_ALREADY_COMPLETED, "today main task already completed");
        }
        learningTaskRepository.flush();
        learningTaskRepository.deleteByDailyPlanIdAndStatus(plan.getId(), TaskStatus.PLANNED);
        // PLANNED 삭제를 새 과제 INSERT 전에 반영한다 (partial unique index, docs/06 §5.9)
        learningTaskRepository.flush();
        return tasks.stream().filter(task -> task.getStatus() != TaskStatus.PLANNED).toList();
    }

    private static List<LearningTask> newTasks(
            DailyPlan plan, UUID userId, Composition composition, List<LearningTask> kept) {
        List<LearningTask> added = new ArrayList<>();
        LearningTask.TaskValues main = composition.main();
        if (main != null) {
            int sortOrder = kept.stream().mapToInt(LearningTask::getSortOrder).max().orElse(0) + 1;
            added.add(LearningTask.main(plan.getId(), userId, main, sortOrder));
            for (LearningTask.TaskValues extra : composition.extras()) {
                sortOrder++;
                added.add(LearningTask.extra(plan.getId(), userId, extra, sortOrder));
            }
        }
        int reviewMinutes = composition.allocation().reviewMinutes();
        boolean reviewKept =
                kept.stream()
                        .anyMatch(task -> !task.isMain() && task.getTaskType() == TaskType.REVIEW);
        if (reviewMinutes >= 1 && !reviewKept) {
            added.add(
                    LearningTask.review(
                            plan.getId(),
                            userId,
                            TaskProposalPolicy.reviewTitle(composition.allocation().dueCount()),
                            reviewMinutes));
        }
        return added;
    }
}
