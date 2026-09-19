package com.devpilot.plan.application;

import com.devpilot.goal.application.LearningGoalQueryService;
import com.devpilot.integration.ai.masking.SecretMasker;
import com.devpilot.skill.application.RoleSkillTargetView;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillDetailView;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.domain.TargetRole;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * replan이 쓰는 바깥 입력: skill catalog, 학습 목표의 target role, 자유 텍스트 마스킹(docs/05 §1.11). {@link
 * ReplanService}가 이 하나만 의존하게 모아 둔다.
 */
@Component
class ReplanInputs {

    /** {@code SECRET_BLOCKED} 감사 source (docs/07 §6.3). */
    static final String MASKING_SOURCE = "LEARNING_PLAN";

    private final SkillCatalogQueryService skillCatalogQueryService;
    private final LearningGoalQueryService learningGoalQueryService;
    private final SecretMasker secretMasker;

    ReplanInputs(
            SkillCatalogQueryService skillCatalogQueryService,
            LearningGoalQueryService learningGoalQueryService,
            SecretMasker secretMasker) {
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.learningGoalQueryService = learningGoalQueryService;
        this.secretMasker = secretMasker;
    }

    Map<String, SkillRef> findActiveByCodes(Collection<String> codes) {
        return skillCatalogQueryService.findActiveByCodes(codes);
    }

    List<RoleSkillTargetView> roleTargets(TargetRole role) {
        return skillCatalogQueryService.roleTargets(role);
    }

    Map<UUID, SkillDetailView> activeSkillDetails() {
        return skillCatalogQueryService.activeSkillDetails();
    }

    @Nullable TargetRole targetRole(UUID userId) {
        return learningGoalQueryService.findTargetRole(userId).orElse(null);
    }

    /**
     * 자유 텍스트 마스킹 (docs/05 §1.11). private key가 있으면 422이고, 아무것도 읽거나 바꾸기 전이다. preview도 확정과 같은 입력이라 같은
     * 결과를 미리 돌려준다.
     */
    ReplanCommand masked(UUID userId, ReplanCommand command) {
        List<ReplanCommand.MilestoneInput> milestones = new ArrayList<>();
        for (ReplanCommand.MilestoneInput input : command.milestones()) {
            milestones.add(
                    new ReplanCommand.MilestoneInput(
                            input.id(),
                            secretMasker.maskOrReject(userId, MASKING_SOURCE, input.title()),
                            secretMasker.maskOrRejectNullable(
                                    userId, MASKING_SOURCE, input.description()),
                            input.startDate(),
                            input.endDate(),
                            input.priority(),
                            input.status(),
                            input.sortOrder(),
                            input.skillCodes()));
        }
        return new ReplanCommand(
                secretMasker.maskOrRejectNullable(userId, MASKING_SOURCE, command.reason()),
                command.version(),
                milestones,
                command.acceptedDeferrals(),
                command.acceptedTargetReductions(),
                command.restoredDeferrals(),
                command.acceptedTargetRaises());
    }

    /** milestone in-place 수정의 {@code description} (docs/05 §7.6). */
    @Nullable String maskedDescription(UUID userId, @Nullable String description) {
        return secretMasker.maskOrRejectNullable(userId, MASKING_SOURCE, description);
    }
}
