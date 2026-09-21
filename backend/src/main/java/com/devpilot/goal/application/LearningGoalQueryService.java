package com.devpilot.goal.application;

import com.devpilot.common.config.TrackDefaults;
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

    /** 학습 목표가 없을 때 쓰는 트랙 (docs/06 §5.1). */
    private static final TargetRole DEFAULT_TRACK = TargetRole.JAVA_BACKEND;

    private final LearningGoalRepository learningGoalRepository;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final ReplanRecommendationProvider replanRecommendationProvider;
    private final TrackDefaultsCheck trackDefaultsCheck;

    public LearningGoalQueryService(
            LearningGoalRepository learningGoalRepository,
            SkillCatalogQueryService skillCatalogQueryService,
            ReplanRecommendationProvider replanRecommendationProvider,
            TrackDefaultsCheck trackDefaultsCheck) {
        this.learningGoalRepository = learningGoalRepository;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.replanRecommendationProvider = replanRecommendationProvider;
        this.trackDefaultsCheck = trackDefaultsCheck;
    }

    /** 없으면 404 {@code LEARNING_GOAL_NOT_FOUND}. */
    public LearningGoalView get(UUID userId) {
        return toView(
                learningGoalRepository
                        .findByUserId(userId)
                        .orElseThrow(LearningGoalQueryService::notFound));
    }

    /** 학습 목표. 없으면 empty (plan·review·today의 horizon·집중 skill 입력, docs/06 §3.1·§5.4). */
    public Optional<LearningGoalView> find(UUID userId) {
        return learningGoalRepository.findByUserId(userId).map(this::toView);
    }

    /** 목표 역할. 목표가 없으면 empty (plan 모듈의 role target 조회용). */
    public Optional<TargetRole> findTargetRole(UUID userId) {
        return learningGoalRepository.findByUserId(userId).map(LearningGoal::getTargetRole);
    }

    /** 학습 목표가 가리키는 학습 트랙의 기본값 (docs/06 §5.1·§5.3). 목표가 없으면 기본 트랙을 쓴다. */
    public TrackDefaults trackDefaults(UUID userId) {
        return trackDefaultsCheck.of(findTargetRole(userId).orElse(DEFAULT_TRACK));
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
                goal.getTargetCompletionDate(),
                focusSkills,
                replanRecommendationProvider.isReplanRecommended(goal.getUserId()),
                Objects.requireNonNull(goal.getCreatedAt(), "createdAt"),
                Objects.requireNonNull(goal.getUpdatedAt(), "updatedAt"),
                goal.getVersion());
    }
}
