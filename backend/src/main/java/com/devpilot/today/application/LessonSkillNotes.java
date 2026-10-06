package com.devpilot.today.application;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.plan.application.PlanQueryService;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.application.SkillTargetView;
import com.devpilot.skill.application.UserSkillStateQueryService;
import com.devpilot.skill.application.UserSkillStateQueryService.PlanningState;
import com.devpilot.today.application.LessonView.LessonRequirementView;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 개념 노트 화면이 skill 쪽에서 읽어 오는 것들 (docs/03 §2.2 모듈 간 질의).
 *
 * <p>{@link LessonQueryService}에서 떼어 냈다 — 생성자 인자 수 한도 때문이고, 묶이는 이유도 하나다: <b>셋 다 "이 노트가 가리키는 skill"에
 * 대한 질의</b>다.
 */
@Component
class LessonSkillNotes {

    private final SkillCatalogQueryService skills;
    private final PlanQueryService plans;
    private final UserSkillStateQueryService states;

    LessonSkillNotes(
            SkillCatalogQueryService skills,
            PlanQueryService plans,
            UserSkillStateQueryService states) {
        this.skills = skills;
        this.plans = plans;
        this.states = states;
    }

    Map<String, SkillRef> findActiveByCodes(Collection<String> codes) {
        return skills.findActiveByCodes(codes);
    }

    Map<UUID, SkillRef> findRefs(Collection<UUID> skillIds) {
        return skills.findRefs(skillIds);
    }

    /**
     * 이 기술을 어디까지 알아야 하는가 (docs/05 §21.1·§21.2, ADR-069).
     *
     * <p>노트를 읽는 사람이 먼저 묻는 것은 <b>"이걸 외워야 하나, 이 정도면 프로젝트를 만들어도 되나"</b>다. 계획이 그 답을 들고 있는데(우선순위와 축별 목표)
     * 노트 화면에는 없었다 — 2026-10-06 실사용에서 드러났다.
     *
     * @return plan에 이 skill의 목표가 없으면 null. 그때는 화면이 줄을 숨긴다 — 없는 기준을 지어내지 않는다
     */
    @Nullable LessonRequirementView requirement(UUID userId, @Nullable UUID skillId) {
        if (skillId == null) {
            return null;
        }
        SkillTargetView target = plans.activePlanTargets(userId).get(skillId);
        if (target == null) {
            return null;
        }
        PlanningState planning = states.planningStates(userId).get(skillId);
        return new LessonRequirementView(
                target.priority(),
                target.targets(),
                planning == null ? AxisLevels.ZERO : planning.planning());
    }
}
