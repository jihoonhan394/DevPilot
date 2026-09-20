package com.devpilot.review.application;

import com.devpilot.learning.application.LearningSessionQueryService;
import com.devpilot.plan.application.PlanQueryService;
import com.devpilot.skill.application.SkillTargetView;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * due 복습 선정이 쓰는 바깥 입력 (docs/06 §6.5): 활성 plan의 skill 우선순위(정렬 기준)와 복귀 모드 여부(상한). {@link
 * ReviewQueryService}가 이 하나만 의존하게 모아 둔다.
 */
@Component
class DueReviewInputs {

    private final PlanQueryService planQueryService;
    private final LearningSessionQueryService learningSessionQueryService;

    DueReviewInputs(
            PlanQueryService planQueryService,
            LearningSessionQueryService learningSessionQueryService) {
        this.planQueryService = planQueryService;
        this.learningSessionQueryService = learningSessionQueryService;
    }

    /** 활성 plan의 skill target — {@code null} priority는 우선순위 없음이다. */
    Map<UUID, SkillTargetView> activePlanTargets(UUID userId) {
        return planQueryService.activePlanTargets(userId);
    }

    /** 복귀 모드면 상한이 {@code comebackMaxPerDay}로 낮아진다 (docs/06 §5.6). */
    boolean isComebackMode(UUID userId, LocalDate today) {
        return learningSessionQueryService.isComebackMode(userId, today);
    }
}
