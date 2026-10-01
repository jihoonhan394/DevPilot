package com.devpilot.today.application;

import com.devpilot.common.security.CurrentUser;
import com.devpilot.goal.application.LearningGoalQueryService;
import com.devpilot.plan.application.PlanQueryService;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.today.domain.DailyTip;
import com.devpilot.today.domain.DailyTipSelector.TipInput;
import com.devpilot.today.domain.LearningTask;
import com.devpilot.today.infrastructure.LearningTaskRepository;
import com.devpilot.today.infrastructure.UserDailyTipRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 팁 선택의 입력 모으기 (docs/06 §5.12 묶음 1~3). 저장소를 읽는 자리를 규칙에서 떼어 둔다 — {@code DailyTipSelector}는 순수 규칙이라
 * 저장소를 몰라야 한다(ARCH-12).
 */
@Component
class TipSelectionInputs {

    private final TodayQueryService todayQueryService;
    private final LearningTaskRepository learningTaskRepository;
    private final UserDailyTipRepository userDailyTipRepository;
    private final PlanQueryService planQueryService;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final LearningGoalQueryService learningGoalQueryService;

    TipSelectionInputs(
            TodayQueryService todayQueryService,
            LearningTaskRepository learningTaskRepository,
            UserDailyTipRepository userDailyTipRepository,
            PlanQueryService planQueryService,
            SkillCatalogQueryService skillCatalogQueryService,
            LearningGoalQueryService learningGoalQueryService) {
        this.todayQueryService = todayQueryService;
        this.learningTaskRepository = learningTaskRepository;
        this.userDailyTipRepository = userDailyTipRepository;
        this.planQueryService = planQueryService;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.learningGoalQueryService = learningGoalQueryService;
    }

    TipInput inputs(CurrentUser user, LocalDate today, List<DailyTip> tips) {
        UUID userId = user.userId();
        Set<UUID> todayMainSkillIds = new HashSet<>();
        todayQueryService
                .mainOf(userId, today)
                .map(LearningTask::getSkillId)
                .ifPresent(todayMainSkillIds::add);
        Set<UUID> recentSkillIds =
                new HashSet<>(
                        learningTaskRepository.findRecentlyCompletedSkillIds(
                                userId, DailyTipService.recentFrom(today), today));
        Set<UUID> planSkillIds = planQueryService.activePlanTargets(userId).keySet();

        Map<UUID, SkillRef> refs =
                skillCatalogQueryService.findRefs(
                        union(todayMainSkillIds, recentSkillIds, planSkillIds));
        return new TipInput(
                tips,
                Set.copyOf(userDailyTipRepository.findShownTipKeys(userId)),
                codes(todayMainSkillIds, refs),
                codes(recentSkillIds, refs),
                codes(planSkillIds, refs),
                learningGoalQueryService.trackDefaults(userId).basicTipsFirst());
    }

    @SafeVarargs
    private static Set<UUID> union(Set<UUID>... sets) {
        Set<UUID> all = new HashSet<>();
        for (Set<UUID> set : sets) {
            all.addAll(set);
        }
        return all;
    }

    /** id 집합 → 활성 skill code 집합. 비활성이거나 없는 id는 빠진다. */
    private static Set<String> codes(Set<UUID> skillIds, Map<UUID, SkillRef> refs) {
        Set<String> codes = new HashSet<>();
        for (UUID id : skillIds) {
            SkillRef ref = refs.get(id);
            if (ref != null) {
                codes.add(ref.code());
            }
        }
        return Set.copyOf(codes);
    }
}
