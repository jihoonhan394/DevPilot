package com.devpilot.today.application;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.application.LearningStageQueryService;
import com.devpilot.skill.application.SkillInfo;
import com.devpilot.skill.application.UserSkillStateQueryService;
import com.devpilot.skill.application.UserSkillStateQueryService.PlanningState;
import com.devpilot.today.domain.PlannerScoring.SkillProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * planner가 보는 skill별 사용자 정보 (docs/06 §5.3·§5.4).
 *
 * <p>planning level과 학습 단계를 <b>한 번씩만</b> 읽어 code로 접어 준다 — 후보 skill 수만큼 조회하면 계획 한 번에 수십 번이 된다.
 */
@Component
class SkillProfiles {

    private final UserSkillStateQueryService userSkillStateQueryService;
    private final LearningStageQueryService learningStageQueryService;

    SkillProfiles(
            UserSkillStateQueryService userSkillStateQueryService,
            LearningStageQueryService learningStageQueryService) {
        this.userSkillStateQueryService = userSkillStateQueryService;
        this.learningStageQueryService = learningStageQueryService;
    }

    Map<String, SkillProfile> of(
            UUID userId, Map<UUID, SkillInfo> skills, Map<UUID, String> codes) {
        Map<UUID, PlanningState> states = userSkillStateQueryService.planningStates(userId);
        // 학습 단계는 한 번에 읽는다 — 후보 skill 수만큼 조회하면 계획 한 번에 수십 번이 된다 (docs/06 §5.4)
        Map<UUID, Integer> stageGaps = learningStageQueryService.stageGaps(userId, skills.keySet());
        Map<String, SkillProfile> profiles = new HashMap<>();
        for (SkillInfo skill : skills.values()) {
            PlanningState state = states.get(skill.id());
            profiles.put(
                    skill.code(),
                    new SkillProfile(
                            skill.code(),
                            skill.name(),
                            skill.description(),
                            state == null ? AxisLevels.ZERO : state.planning(),
                            state == null ? null : state.lastPracticedAt(),
                            skill.prerequisiteIds().stream()
                                    .map(codes::get)
                                    .filter(code -> code != null)
                                    .toList(),
                            stageGaps.getOrDefault(
                                    skill.id(), LearningStageQueryService.FULL_STAGE_GAP)));
        }
        return profiles;
    }
}
