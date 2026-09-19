package com.devpilot.goal.application;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.goal.domain.LearningGoal;
import com.devpilot.goal.infrastructure.LearningGoalRepository;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.domain.TargetRole;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학습 목표 조회 (docs/05 §5.1, BL-GOL-01). plan 모듈도 horizon 계산에 쓴다. */
@Service
@Transactional(readOnly = true)
public class LearningGoalQueryService {

    private final LearningGoalRepository learningGoalRepository;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final ReplanRecommendationProvider replanRecommendationProvider;

    public LearningGoalQueryService(
            LearningGoalRepository learningGoalRepository,
            SkillCatalogQueryService skillCatalogQueryService,
            ReplanRecommendationProvider replanRecommendationProvider) {
        this.learningGoalRepository = learningGoalRepository;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.replanRecommendationProvider = replanRecommendationProvider;
    }

    /** 없으면 404 {@code LEARNING_GOAL_NOT_FOUND}. */
    public LearningGoalView get(UUID userId) {
        return toView(
                learningGoalRepository
                        .findByUserId(userId)
                        .orElseThrow(LearningGoalQueryService::notFound));
    }

    /** 목표 역할. 목표가 없으면 empty (plan 모듈의 role target 조회용). */
    public Optional<TargetRole> findTargetRole(UUID userId) {
        return learningGoalRepository.findByUserId(userId).map(LearningGoal::getTargetRole);
    }

    static NotFoundException notFound() {
        return new NotFoundException(ErrorCode.LEARNING_GOAL_NOT_FOUND, "learning goal not found");
    }

    /** entity → view. 같은 모듈의 명령 서비스가 저장 직후 응답을 만들 때 쓴다. */
    public LearningGoalView toView(LearningGoal goal) {
        List<SkillRef> focusSkills =
                skillCatalogQueryService.findRefs(goal.getFocusSkillIds()).values().stream()
                        .sorted(Comparator.comparing(SkillRef::code))
                        .toList();
        return new LearningGoalView(
                goal.getId(),
                goal.getTargetRole(),
                goal.getCheckpointDate(),
                goal.getTargetCompletionDate(),
                focusSkills,
                replanRecommendationProvider.isReplanRecommended(goal.getUserId()),
                Objects.requireNonNull(goal.getCreatedAt(), "createdAt"),
                Objects.requireNonNull(goal.getUpdatedAt(), "updatedAt"),
                goal.getVersion());
    }
}
