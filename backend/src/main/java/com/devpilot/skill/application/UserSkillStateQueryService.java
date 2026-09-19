package com.devpilot.skill.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.domain.PlanningLevelPolicy;
import com.devpilot.skill.domain.UserSkillState;
import com.devpilot.skill.infrastructure.UserSkillStateRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /skills/me} (docs/05 §6.2, BL-SKL-02). 활성 skill 전체를 catalog 순서로 돌려준다. state 행이 없는
 * skill은 레벨 0, 자기평가 없음, 활성으로 채우고 행을 만들지 않는다. planning level은 docs/06 §7.5.
 */
@Service
@Transactional(readOnly = true)
public class UserSkillStateQueryService {

    private final SkillCatalogQueryService skillCatalogQueryService;
    private final UserSkillStateRepository userSkillStateRepository;
    private final PlanSkillTargetProvider planSkillTargetProvider;
    private final PlanningLevelPolicy planningLevelPolicy;

    public UserSkillStateQueryService(
            SkillCatalogQueryService skillCatalogQueryService,
            UserSkillStateRepository userSkillStateRepository,
            PlanSkillTargetProvider planSkillTargetProvider,
            DevPilotProperties properties) {
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.userSkillStateRepository = userSkillStateRepository;
        this.planSkillTargetProvider = planSkillTargetProvider;
        this.planningLevelPolicy = new PlanningLevelPolicy(properties.skill().selfAssessmentCap());
    }

    public List<UserSkillStateView> myStates(UUID userId) {
        Map<UUID, UserSkillState> states =
                userSkillStateRepository.findByUserId(userId).stream()
                        .collect(Collectors.toMap(UserSkillState::getSkillId, Function.identity()));
        Map<UUID, SkillTargetView> targets = planSkillTargetProvider.activePlanTargets(userId);
        return skillCatalogQueryService.activeSkills().stream()
                .map(skill -> toView(skill, states.get(skill.id()), targets.get(skill.id())))
                .toList();
    }

    /**
     * skill id → planning level·마지막 연습 시각 (docs/06 §7.5). state 행이 없는 skill은 map에 없다 — 호출자는 레벨 0,
     * 연습 없음으로 본다(자기평가 없음 + 활성 → planning = 증거 레벨 0).
     */
    public Map<UUID, PlanningState> planningStates(UUID userId) {
        return userSkillStateRepository.findByUserId(userId).stream()
                .collect(
                        Collectors.toMap(
                                UserSkillState::getSkillId,
                                state ->
                                        new PlanningState(
                                                planningLevelPolicy.planningLevels(
                                                        state.getEvidenceLevels(),
                                                        state.getSelfAssessedLevel(),
                                                        state.isSelfAssessmentActive()),
                                                state.getLastPracticedAt())));
    }

    /**
     * skill id → 자기평가 레벨 (docs/06 §7.4 {@code claimedLevel}). 자기평가가 없는 skill은 map에 없다.
     */
    public Map<UUID, Integer> selfAssessedLevels(UUID userId) {
        Map<UUID, Integer> levels = new HashMap<>();
        for (UserSkillState state : userSkillStateRepository.findByUserId(userId)) {
            Integer level = state.getSelfAssessedLevel();
            if (level != null) {
                levels.put(state.getSkillId(), level);
            }
        }
        return Map.copyOf(levels);
    }

    /**
     * 규칙 입력용 skill state.
     *
     * @param planning docs/06 §7.5 planning level
     * @param lastPracticedAt 연습 기록이 없으면 null (planner 동점 처리에서 먼저 온다)
     */
    public record PlanningState(AxisLevels planning, @Nullable Instant lastPracticedAt) {}

    private UserSkillStateView toView(
            SkillRef skill, @Nullable UserSkillState state, @Nullable SkillTargetView target) {
        if (state == null) {
            AxisLevels zero = AxisLevels.ZERO;
            return new UserSkillStateView(
                    skill,
                    zero,
                    planningLevelPolicy.planningLevels(zero, null, true),
                    target,
                    null,
                    true,
                    0,
                    null,
                    null);
        }
        AxisLevels evidence = state.getEvidenceLevels();
        return new UserSkillStateView(
                skill,
                evidence,
                planningLevelPolicy.planningLevels(
                        evidence, state.getSelfAssessedLevel(), state.isSelfAssessmentActive()),
                target,
                state.getSelfAssessedLevel(),
                state.isSelfAssessmentActive(),
                state.getEvidenceCount(),
                state.getLastPracticedAt(),
                state.getUpdatedAt());
    }
}
