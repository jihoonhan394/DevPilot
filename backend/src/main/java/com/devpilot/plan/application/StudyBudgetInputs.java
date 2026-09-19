package com.devpilot.plan.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.time.UserTimeSettingsProvider;
import com.devpilot.common.time.UserTimeSettingsProvider.UserTimeSettings;
import com.devpilot.common.web.AxisLevels;
import com.devpilot.goal.application.LearningGoalQueryService;
import com.devpilot.goal.application.LearningGoalView;
import com.devpilot.plan.application.StudyBudgetService.TargetLine;
import com.devpilot.plan.domain.ReplanSuggestionPolicy.TargetItem;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillDetailView;
import com.devpilot.skill.application.UserSkillStateQueryService;
import com.devpilot.skill.application.UserSkillStateQueryService.PlanningState;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * budget·risk 계산 입력 수집 (docs/06 §3·§4.1). 학습 목표 날짜, 사용자 학습 가능 시간, 최근 완료율 기록(port {@link
 * StudyHistoryProvider}), 활성 skill의 planning level·단계당 분을 읽는다. 계산은 {@link StudyBudgetService}가 한다.
 */
@Component
class StudyBudgetInputs {

    private final LearningGoalQueryService learningGoalQueryService;
    private final UserTimeSettingsProvider userTimeSettingsProvider;
    private final UserSkillStateQueryService userSkillStateQueryService;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final StudyHistoryProvider studyHistoryProvider;
    private final int completionWindowDays;

    StudyBudgetInputs(
            LearningGoalQueryService learningGoalQueryService,
            UserTimeSettingsProvider userTimeSettingsProvider,
            UserSkillStateQueryService userSkillStateQueryService,
            SkillCatalogQueryService skillCatalogQueryService,
            StudyHistoryProvider studyHistoryProvider,
            DevPilotProperties properties) {
        this.learningGoalQueryService = learningGoalQueryService;
        this.userTimeSettingsProvider = userTimeSettingsProvider;
        this.userSkillStateQueryService = userSkillStateQueryService;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.studyHistoryProvider = studyHistoryProvider;
        this.completionWindowDays = properties.budget().completionWindowDays();
    }

    Optional<LearningGoalView> goal(UUID userId) {
        return learningGoalQueryService.find(userId);
    }

    UserTimeSettings timeSettings(UUID userId) {
        return userTimeSettingsProvider.timeSettings(userId);
    }

    /** 완료율 window {@code [today − completionWindowDays, today − 1]} (docs/06 §3.2). */
    StudyHistoryProvider.StudyHistory recentHistory(UUID userId, LocalDate today) {
        return studyHistoryProvider.history(
                userId, today.minusDays(completionWindowDays), today.minusDays(1));
    }

    /** 활성 skill 목표만 규칙 입력으로 바꾼다(비활성 skill 제외, docs/06 §4.1). */
    List<TargetItem> targetItems(UUID userId, List<TargetLine> targets) {
        Map<UUID, SkillDetailView> skills = skillCatalogQueryService.activeSkillDetails();
        Map<UUID, PlanningState> planning = userSkillStateQueryService.planningStates(userId);
        List<TargetItem> items = new ArrayList<>();
        for (TargetLine target : targets) {
            SkillDetailView skill = skills.get(target.skillId());
            if (skill == null) {
                continue;
            }
            PlanningState state = planning.get(target.skillId());
            items.add(
                    new TargetItem(
                            skill.code(),
                            target.priority(),
                            target.practicalImportanceBp(),
                            target.deferred(),
                            target.targets(),
                            state == null ? AxisLevels.ZERO : state.planning(),
                            skill.minutesPerLevelStep()));
        }
        return items;
    }
}
