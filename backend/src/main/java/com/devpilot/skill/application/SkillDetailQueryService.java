package com.devpilot.skill.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.application.SkillDetailView.LearningStageView;
import com.devpilot.skill.domain.LearningStageEvaluator;
import com.devpilot.skill.domain.LearningStageEvaluator.StageCompletion;
import com.devpilot.skill.domain.PlanningLevelPolicy;
import com.devpilot.skill.domain.Skill;
import com.devpilot.skill.domain.UserSkillState;
import com.devpilot.skill.infrastructure.SkillPrerequisiteRepository;
import com.devpilot.skill.infrastructure.SkillRepository;
import com.devpilot.skill.infrastructure.UserSkillStateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /skills/{skillId}} (docs/05 §6.4, BL-STG-02).
 *
 * <p>학습 단계 6칸은 <b>저장하지 않는다</b>(ADR-042). 요청 시점에 그 사용자·그 skill의 학습 이벤트로 계산한다 — 단계 컬럼을 두면 기록과 어긋난
 * 순간부터 고칠 방법이 없어지기 때문이다.
 *
 * <p>비활성 skill도 조회된다. 지난 과제와 복습 카드가 그 skill을 가리키고 있어서, 열리지 않으면 지난 기록에서 막다른 길이 생긴다.
 */
@Service
@Transactional(readOnly = true)
public class SkillDetailQueryService {

    private final SkillRepository skillRepository;
    private final SkillPrerequisiteRepository skillPrerequisiteRepository;
    private final UserSkillStateRepository userSkillStateRepository;
    private final PlanSkillTargetProvider planSkillTargetProvider;
    private final SkillWhyItMattersProvider whyItMattersProvider;
    private final LearningStageQueryService learningStageQueryService;
    private final PlanningLevelPolicy planningLevelPolicy;

    public SkillDetailQueryService(
            SkillRepository skillRepository,
            SkillPrerequisiteRepository skillPrerequisiteRepository,
            UserSkillStateRepository userSkillStateRepository,
            PlanSkillTargetProvider planSkillTargetProvider,
            SkillWhyItMattersProvider whyItMattersProvider,
            LearningStageQueryService learningStageQueryService,
            DevPilotProperties properties) {
        this.skillRepository = skillRepository;
        this.skillPrerequisiteRepository = skillPrerequisiteRepository;
        this.userSkillStateRepository = userSkillStateRepository;
        this.planSkillTargetProvider = planSkillTargetProvider;
        this.whyItMattersProvider = whyItMattersProvider;
        this.learningStageQueryService = learningStageQueryService;
        this.planningLevelPolicy = new PlanningLevelPolicy(properties.skill().selfAssessmentCap());
    }

    public SkillDetailView detail(UUID userId, UUID skillId) {
        Skill skill =
                skillRepository
                        .findById(skillId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "skill not found"));
        UserSkillState state =
                userSkillStateRepository.findByUserId(userId).stream()
                        .filter(row -> row.getSkillId().equals(skillId))
                        .findFirst()
                        .orElse(null);
        AxisLevels evidence = state == null ? AxisLevels.ZERO : state.getEvidenceLevels();
        AxisLevels planning =
                state == null
                        ? planningLevelPolicy.planningLevels(AxisLevels.ZERO, null, true)
                        : planningLevelPolicy.planningLevels(
                                evidence,
                                state.getSelfAssessedLevel(),
                                state.isSelfAssessmentActive());
        return new SkillDetailView(
                new SkillRef(skill.getId(), skill.getCode(), skill.getName(), skill.getCategory()),
                parentCode(skill),
                skill.getDescription(),
                whyItMattersProvider.whyItMatters(skill.getCode()).orElse(null),
                skill.getMinutesPerLevelStep(),
                prerequisiteCodes(skillId),
                evidence,
                planning,
                planSkillTargetProvider.activePlanTargets(userId).get(skillId),
                stages(userId, skillId, skill));
    }

    /**
     * ST-6: 판정 대상은 활성 skill뿐이다. retire된 skill은 여섯 칸을 모두 미완료로 보인다 — 없던 일로 만들지 않되, 다시 돌라고 권하지도 않는다.
     */
    private List<LearningStageView> stages(UUID userId, UUID skillId, Skill skill) {
        List<StageCompletion> stages =
                skill.isActive()
                        ? learningStageQueryService.stages(userId, skillId)
                        : new LearningStageEvaluator().evaluate(List.of());
        List<LearningStageView> views = new ArrayList<>();
        for (StageCompletion stage : stages) {
            views.add(new LearningStageView(stage.stage(), stage.completed(), stage.completedAt()));
        }
        return List.copyOf(views);
    }

    private @Nullable String parentCode(Skill skill) {
        UUID parentId = skill.getParentId();
        return parentId == null
                ? null
                : skillRepository.findById(parentId).map(Skill::getCode).orElse(null);
    }

    /** code ASC (docs/05 §6.4). 없어진 선행 skill은 빠진다. */
    private List<String> prerequisiteCodes(UUID skillId) {
        Set<String> codes = new TreeSet<>();
        for (Skill required :
                skillRepository.findAllById(
                        skillPrerequisiteRepository.findPrerequisiteIds(skillId))) {
            codes.add(required.getCode());
        }
        return List.copyOf(codes);
    }
}
