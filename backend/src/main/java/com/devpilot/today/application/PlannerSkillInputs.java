package com.devpilot.today.application;

import com.devpilot.plan.application.PlanSkillTargetView;
import com.devpilot.plan.application.PlanView;
import com.devpilot.skill.application.MeasurableAxes;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillInfo;
import com.devpilot.today.domain.PlannerScoring.SkillProfile;
import com.devpilot.today.domain.PlannerScoring.SkillTarget;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * planner에 들어가는 <b>skill 쪽 입력</b>만 모은다 — catalog, 사용자 레벨, 계획 목표.
 *
 * <p>{@link PlannerInputCollector}에서 갈라낸 이유는 {@link
 * com.devpilot.dashboard.application.DashboardProgressAssembler}와 같다: 생성자 인자가 상한에 닿았고, 이 셋은 "지금 이
 * 사람의 skill이 어디쯤인가"라는 한 가지 질문에 함께 답한다.
 */
@Component
public class PlannerSkillInputs {

    private final SkillCatalogQueryService skillCatalogQueryService;
    private final SkillProfiles skillProfiles;
    private final MeasurableAxes measurableAxes;

    PlannerSkillInputs(
            SkillCatalogQueryService skillCatalogQueryService,
            SkillProfiles skillProfiles,
            MeasurableAxes measurableAxes) {
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.skillProfiles = skillProfiles;
        this.measurableAxes = measurableAxes;
    }

    /** 활성 skill 상세 (id → 이름·code·category). */
    Map<UUID, SkillInfo> activeSkillDetails() {
        return skillCatalogQueryService.activeSkillDetails();
    }

    /** 사용자 레벨 (docs/06 §7.5 planning level 포함). */
    Map<String, SkillProfile> profiles(
            UUID userId, Map<UUID, SkillInfo> skills, Map<UUID, String> codes) {
        return skillProfiles.of(userId, skills, codes);
    }

    /** 진도 판정에 쓸 목표. 아직 잴 수 없는 축은 빼고 본다(docs/06 §7.6) — 남겨 두면 어떤 milestone도 끝나지 않아 후보가 첫 단계에 갇힌다. */
    Map<String, SkillTarget> targets(PlanView plan) {
        Map<String, SkillTarget> targets = new HashMap<>();
        for (PlanSkillTargetView target : plan.skillTargets()) {
            targets.put(
                    target.skill().code(),
                    new SkillTarget(
                            target.priority(),
                            target.practicalImportanceBp(),
                            measurableAxes.forProgress(target.targets()),
                            target.deferred()));
        }
        return targets;
    }
}
