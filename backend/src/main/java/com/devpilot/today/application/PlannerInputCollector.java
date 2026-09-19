package com.devpilot.today.application;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.goal.application.LearningGoalQueryService;
import com.devpilot.learning.application.LearningEventQueryService;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.plan.application.MilestoneView;
import com.devpilot.plan.application.PlanSkillTargetView;
import com.devpilot.plan.application.PlanView;
import com.devpilot.plan.domain.RiskLevel;
import com.devpilot.project.application.SideProjectQueryService;
import com.devpilot.review.application.ReviewQueryService;
import com.devpilot.review.application.ReviewQueryService.DueSummary;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillDetailView;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.application.UserSkillStateQueryService;
import com.devpilot.skill.application.UserSkillStateQueryService.PlanningState;
import com.devpilot.today.domain.EnergyLevel;
import com.devpilot.today.domain.PlannerScoring.MilestoneSpan;
import com.devpilot.today.domain.PlannerScoring.RecentMain;
import com.devpilot.today.domain.PlannerScoring.SkillProfile;
import com.devpilot.today.domain.PlannerScoring.SkillTarget;
import com.devpilot.today.domain.TaskProposalPolicy.SideProjectRef;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Today 규칙 입력 수집 (docs/05 §8.2 3단계). planning level, 집중 skill, leech, 최근 복습 실패, 어제·그제 main, ACTIVE
 * 사이드 프로젝트를 모아 skill code 기준으로 바꾼다. 계산은 {@link DailyPlanComposer}가 한다. 호출자 트랜잭션에서 읽는다.
 */
@Component
class PlannerInputCollector {

    /** 최근 7 plan-day (docs/06 §5.8 {@code RECENT_RECALL_FAILURE}): {@code [today − 6, today]}. */
    private static final int RECALL_FAILURE_DAYS = 7;

    /** 최근 1 plan-day의 leech (docs/06 §6.4): 어제 감지된 leech가 오늘 계획에 반영된다. */
    private static final int LEECH_DAYS = 1;

    private final SkillCatalogQueryService skillCatalogQueryService;
    private final UserSkillStateQueryService userSkillStateQueryService;
    private final LearningEventQueryService learningEventQueryService;
    private final ReviewQueryService reviewQueryService;
    private final LearningGoalQueryService learningGoalQueryService;
    private final SideProjectQueryService sideProjectQueryService;
    private final TodayQueryService todayQueryService;

    PlannerInputCollector(
            SkillCatalogQueryService skillCatalogQueryService,
            UserSkillStateQueryService userSkillStateQueryService,
            LearningEventQueryService learningEventQueryService,
            ReviewQueryService reviewQueryService,
            LearningGoalQueryService learningGoalQueryService,
            SideProjectQueryService sideProjectQueryService,
            TodayQueryService todayQueryService) {
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.userSkillStateQueryService = userSkillStateQueryService;
        this.learningEventQueryService = learningEventQueryService;
        this.reviewQueryService = reviewQueryService;
        this.learningGoalQueryService = learningGoalQueryService;
        this.sideProjectQueryService = sideProjectQueryService;
        this.todayQueryService = todayQueryService;
    }

    Inputs collect(UUID userId, LocalDate today, PlanView plan, Context context, DueSummary due) {
        Map<UUID, SkillDetailView> skills = skillCatalogQueryService.activeSkillDetails();
        Map<UUID, String> codes = new HashMap<>();
        for (SkillDetailView skill : skills.values()) {
            codes.put(skill.id(), skill.code());
        }
        Map<String, Integer> overdueByCode = new HashMap<>();
        due.maxOverdueDaysBySkill()
                .forEach(
                        (skillId, days) -> {
                            String code = codes.get(skillId);
                            if (code != null) {
                                overdueByCode.put(code, days);
                            }
                        });
        return new Inputs(
                today,
                context,
                profiles(userId, skills, codes),
                targets(plan),
                milestones(plan),
                overdueByCode,
                codesOf(
                        learningEventQueryService.skillsWithEventSince(
                                userId,
                                LearningEventType.LEECH_DETECTED,
                                today.minusDays(LEECH_DAYS)),
                        codes),
                codesOf(
                        reviewQueryService.skillsWithRecentAgain(
                                userId, today.minusDays(RECALL_FAILURE_DAYS - 1L)),
                        codes),
                focusCodes(userId),
                recentMain(userId, today.minusDays(1), codes),
                recentMain(userId, today.minusDays(2), codes),
                sideProjectQueryService
                        .findLatestActive(userId)
                        .map(project -> new SideProjectRef(project.id(), project.name()))
                        .orElse(null),
                codes.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey)));
    }

    private Map<String, SkillProfile> profiles(
            UUID userId, Map<UUID, SkillDetailView> skills, Map<UUID, String> codes) {
        Map<UUID, PlanningState> states = userSkillStateQueryService.planningStates(userId);
        Map<String, SkillProfile> profiles = new HashMap<>();
        for (SkillDetailView skill : skills.values()) {
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
                                    .toList()));
        }
        return profiles;
    }

    private static Map<String, SkillTarget> targets(PlanView plan) {
        Map<String, SkillTarget> targets = new HashMap<>();
        for (PlanSkillTargetView target : plan.skillTargets()) {
            targets.put(
                    target.skill().code(),
                    new SkillTarget(
                            target.priority(),
                            target.practicalImportanceBp(),
                            target.targets(),
                            target.deferred()));
        }
        return targets;
    }

    private Set<String> focusCodes(UUID userId) {
        return learningGoalQueryService
                .find(userId)
                .map(
                        goal ->
                                goal.focusSkills().stream()
                                        .map(SkillRef::code)
                                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    /** plan-day의 main 과제 (docs/05 §8.1 선택 규칙: 활성 main, 없으면 sort_order가 가장 큰 main). */
    private @Nullable RecentMain recentMain(
            UUID userId, LocalDate planDate, Map<UUID, String> codes) {
        return todayQueryService
                .mainOf(userId, planDate)
                .filter(task -> task.getSkillId() != null && codes.containsKey(task.getSkillId()))
                .map(task -> new RecentMain(codes.get(task.getSkillId()), task.getStatus()))
                .orElse(null);
    }

    private static List<MilestoneSpan> milestones(PlanView plan) {
        return plan.milestones().stream()
                .sorted(Comparator.comparing(MilestoneView::sortOrder))
                .map(
                        milestone ->
                                new MilestoneSpan(
                                        milestone.id(),
                                        milestone.title(),
                                        milestone.startDate(),
                                        milestone.endDate(),
                                        Set.copyOf(milestone.skillCodes())))
                .toList();
    }

    private static Set<String> codesOf(Set<UUID> skillIds, Map<UUID, String> codes) {
        Set<String> result = new HashSet<>();
        for (UUID skillId : skillIds) {
            String code = codes.get(skillId);
            if (code != null) {
                result.add(code);
            }
        }
        return result;
    }

    /** 규칙에 넘기는 오늘 공통 입력. risk가 없으면 LOW로 본다. */
    record Context(RiskLevel risk, EnergyLevel energy, boolean comebackMode) {}

    /** 모은 입력. */
    record Inputs(
            LocalDate today,
            Context context,
            Map<String, SkillProfile> profiles,
            Map<String, SkillTarget> targets,
            List<MilestoneSpan> milestones,
            Map<String, Integer> overdueByCode,
            Set<String> leechCodes,
            Set<String> recentAgainCodes,
            Set<String> focusCodes,
            @Nullable RecentMain yesterday,
            @Nullable RecentMain dayBefore,
            @Nullable SideProjectRef sideProject,
            Map<String, UUID> skillIdsByCode) {}
}
