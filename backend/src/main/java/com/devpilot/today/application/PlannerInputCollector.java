package com.devpilot.today.application;

import com.devpilot.common.config.TrackDefaults;
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
import com.devpilot.today.domain.LearningTask;
import com.devpilot.today.domain.PlannerScoring;
import com.devpilot.today.domain.PlannerScoring.MilestoneSpan;
import com.devpilot.today.domain.PlannerScoring.RecentMain;
import com.devpilot.today.domain.PlannerScoring.SkillProfile;
import com.devpilot.today.domain.PlannerScoring.SkillTarget;
import com.devpilot.today.domain.TaskProposalPolicy.SideProjectRef;
import com.devpilot.today.domain.TaskType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /**
     * 과제 유형 단조로움을 보는 창 (docs/06 §5.5 4번). {@code [today − 5, today − 1]}이고, 어제·그제 main({@code
     * CONTINUATION}·{@code FATIGUE_*})도 이 조회에서 같이 나온다.
     */
    private static final int RECENT_MAIN_DAYS = PlannerScoring.MONOTONY_STRONG_DAYS;

    /** 학습 목표가 없을 때 쓰는 트랙 (docs/06 §5.1). */
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
        RecentMains recentMains = recentMains(userId, today, codes);
        return new Inputs(
                today,
                context,
                learningGoalQueryService.trackDefaults(userId),
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
                recentMains.yesterday(),
                recentMains.dayBefore(),
                recentMains.taskTypes(),
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

    /**
     * 최근 plan-day의 main 과제 (docs/05 §8.1 선택 규칙: 활성 main, 없으면 sort_order가 가장 큰 main). 어제·그제
     * main(§5.5 3a·3b)과 유형 목록(§5.5 4번)을 한 번에 모은다. 유형 목록은 main 과제가 없는 plan-day에서 끊는다.
     */
    private RecentMains recentMains(UUID userId, LocalDate today, Map<UUID, String> codes) {
        @Nullable RecentMain yesterday = null;
        @Nullable RecentMain dayBefore = null;
        List<TaskType> taskTypes = new ArrayList<>();
        boolean unbroken = true;
        for (int back = 1; back <= RECENT_MAIN_DAYS; back++) {
            Optional<LearningTask> main = todayQueryService.mainOf(userId, today.minusDays(back));
            if (back == 1) {
                yesterday = toRecentMain(main, codes);
            } else if (back == 2) {
                dayBefore = toRecentMain(main, codes);
            }
            if (unbroken && main.isPresent()) {
                taskTypes.add(main.get().getTaskType());
            } else {
                unbroken = false;
            }
            if (!unbroken && back >= 2) {
                // 유형이 이미 끊겼고 어제·그제도 읽었다 — 더 거슬러 올라갈 이유가 없다
                break;
            }
        }
        return new RecentMains(yesterday, dayBefore, List.copyOf(taskTypes));
    }

    private static @Nullable RecentMain toRecentMain(
            Optional<LearningTask> main, Map<UUID, String> codes) {
        return main.filter(
                        task -> task.getSkillId() != null && codes.containsKey(task.getSkillId()))
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
                                        milestone.sortOrder(),
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

    /** 최근 main 과제 (docs/06 §5.5 3a·3b·4번). */
    private record RecentMains(
            @Nullable RecentMain yesterday,
            @Nullable RecentMain dayBefore,
            List<TaskType> taskTypes) {}

    /**
     * 모은 입력.
     *
     * @param recentMainTaskTypes 최근 main 과제 유형 (가장 최근이 앞, docs/06 §5.5 4번)
     */
    record Inputs(
            LocalDate today,
            Context context,
            TrackDefaults trackDefaults,
            Map<String, SkillProfile> profiles,
            Map<String, SkillTarget> targets,
            List<MilestoneSpan> milestones,
            Map<String, Integer> overdueByCode,
            Set<String> leechCodes,
            Set<String> recentAgainCodes,
            Set<String> focusCodes,
            @Nullable RecentMain yesterday,
            @Nullable RecentMain dayBefore,
            List<TaskType> recentMainTaskTypes,
            @Nullable SideProjectRef sideProject,
            Map<String, UUID> skillIdsByCode) {}
}
