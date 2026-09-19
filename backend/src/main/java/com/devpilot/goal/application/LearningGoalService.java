package com.devpilot.goal.application;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.goal.domain.LearningGoal;
import com.devpilot.goal.domain.LearningGoalDatesChanged;
import com.devpilot.goal.infrastructure.LearningGoalRepository;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습 목표 생성(온보딩)·수정 (docs/05 §4.1 처리 4, §5.2, BL-GOL-01). 날짜가 바뀌면 {@link LearningGoalDatesChanged}를
 * 발행하고 plan 모듈이 같은 트랜잭션에서 {@code replan_recommended}를 켠다.
 */
@Service
public class LearningGoalService {

    private static final int MAX_YEARS_AHEAD = 3;
    private static final int MAX_YEARS_BEHIND = 1;

    private final LearningGoalRepository learningGoalRepository;
    private final LearningGoalQueryService learningGoalQueryService;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public LearningGoalService(
            LearningGoalRepository learningGoalRepository,
            LearningGoalQueryService learningGoalQueryService,
            SkillCatalogQueryService skillCatalogQueryService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.learningGoalRepository = learningGoalRepository;
        this.learningGoalQueryService = learningGoalQueryService;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 도메인 검사 (docs/05 §4.1 표, §5.2). {@code today}는 사용자 plan-day. 오류 field는 {@code
     * fieldPrefix}(온보딩은 {@code "learningGoal."}) 뒤에 붙는다. 오류가 없으면 빈 목록.
     */
    @Transactional(readOnly = true)
    public List<ApiFieldError> validate(
            LearningGoalCommand command, LocalDate today, String fieldPrefix) {
        List<ApiFieldError> errors = new ArrayList<>();
        LocalDate target = command.targetCompletionDate();
        if (target.isBefore(today.plusDays(1))
                || target.isAfter(today.plusYears(MAX_YEARS_AHEAD))) {
            errors.add(
                    ApiFieldError.of(
                            fieldPrefix + "targetCompletionDate",
                            FieldErrorCodes.DATE_OUT_OF_RANGE));
        }
        LocalDate checkpoint = command.checkpointDate();
        if (checkpoint != null) {
            if (checkpoint.isBefore(today.minusYears(MAX_YEARS_BEHIND))) {
                errors.add(
                        ApiFieldError.of(
                                fieldPrefix + "checkpointDate", FieldErrorCodes.DATE_OUT_OF_RANGE));
            } else if (checkpoint.isAfter(target)) {
                errors.add(
                        ApiFieldError.of(
                                fieldPrefix + "checkpointDate",
                                FieldErrorCodes.DATE_ORDER_INVALID));
            }
        }
        Map<String, SkillRef> known =
                skillCatalogQueryService.findActiveByCodes(command.focusSkillCodes());
        List<String> codes = command.focusSkillCodes();
        for (int i = 0; i < codes.size(); i++) {
            if (!known.containsKey(codes.get(i))) {
                errors.add(
                        ApiFieldError.of(
                                fieldPrefix + "focusSkillCodes[" + i + "]",
                                FieldErrorCodes.SKILL_CODE_UNKNOWN));
            }
        }
        return errors;
    }

    /**
     * 온보딩 생성 (docs/05 §4.1 처리 4). 호출자가 {@link #validate}를 끝냈다. 같은 사용자의 목표가 이미 있으면(동시 온보딩) 409
     * {@code ONBOARDING_ALREADY_COMPLETED}.
     */
    @Transactional
    public LearningGoalView createForOnboarding(UUID userId, LearningGoalCommand command) {
        if (learningGoalRepository.findByUserId(userId).isPresent()) {
            throw alreadyOnboarded();
        }
        LearningGoal goal = LearningGoal.create(userId, values(command));
        try {
            learningGoalRepository.saveAndFlush(goal);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    ErrorCode.ONBOARDING_ALREADY_COMPLETED,
                    "learning goal already exists",
                    exception);
        }
        return learningGoalQueryService.toView(goal);
    }

    /**
     * {@code PUT /learning-goal} (docs/05 §5.2). 조회(404) → version(409) → 도메인 검사(400) → 전체 교체. 날짜가
     * 바뀌면 이벤트를 발행한다. plan 구조는 바꾸지 않는다.
     */
    @Transactional
    public LearningGoalView update(CurrentUser user, LearningGoalCommand command, long version) {
        UUID userId = user.userId();
        LocalDate today =
                PlanDayCalculator.planDate(clock.instant(), user.zoneId(), user.dayStartHour());
        LearningGoal goal =
                learningGoalRepository
                        .findByUserId(userId)
                        .orElseThrow(LearningGoalQueryService::notFound);
        if (goal.getVersion() != version) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION, "learning goal version does not match");
        }
        List<ApiFieldError> errors = validate(command, today, "");
        if (!errors.isEmpty()) {
            throw new BusinessValidationException("invalid learning goal", errors);
        }
        boolean datesChanged = goal.replace(values(command));
        learningGoalRepository.flush();
        if (datesChanged) {
            eventPublisher.publishEvent(new LearningGoalDatesChanged(userId, goal.getId()));
        }
        return learningGoalQueryService.toView(goal);
    }

    private LearningGoal.GoalValues values(LearningGoalCommand command) {
        Map<String, SkillRef> known =
                skillCatalogQueryService.findActiveByCodes(command.focusSkillCodes());
        Set<UUID> focusSkillIds = new HashSet<>();
        for (String code : command.focusSkillCodes()) {
            SkillRef skill = known.get(code);
            if (skill == null) {
                throw new IllegalStateException("focus skill code was not validated");
            }
            focusSkillIds.add(skill.id());
        }
        return new LearningGoal.GoalValues(
                command.targetRole(),
                command.checkpointDate(),
                command.targetCompletionDate(),
                focusSkillIds);
    }

    private static ConflictException alreadyOnboarded() {
        return new ConflictException(
                ErrorCode.ONBOARDING_ALREADY_COMPLETED, "learning goal already exists");
    }
}
