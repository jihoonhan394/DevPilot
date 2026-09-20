package com.devpilot.onboarding.application;

import com.devpilot.plan.application.PlanQueryService;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillDetailView;
import com.devpilot.skill.application.SkillTargetView;
import com.devpilot.skill.application.UserSkillStateQueryService;
import com.devpilot.skill.application.UserSkillStateQueryService.AssessmentState;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.SkillCategory;
import com.devpilot.training.application.ChallengeQueryService;
import com.devpilot.training.application.ChallengeQueryService.DiagnosticCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진단 challenge 제안 (docs/05 §4.2, BL-TRN-13). 결정적이고 아무것도 저장하지 않는다.
 *
 * <ol>
 *   <li>대상 category — {@code SkillCategory} 선언 순서, 최대 {@code MAX_SUGGESTIONS}개. 진단 모드(자기평가가 하나도
 *       없음)는 활성 자기평가가 있는 category 전부, 자기평가 모드는 그 category의 자기평가 최댓값이 3 이상인 것
 *   <li>그 category에 이미 {@code DIAGNOSTIC} attempt가 있으면 category 전체를 제외한다
 *   <li>후보: {@code VALIDATED} 공용 {@code DIAGNOSTIC} challenge 중 그 category의 활성 자기평가 skill을 가진 것
 *   <li>정렬: priority(MUST→SHOULD→LATER→없음) → practicalImportance DESC → {@code seed_key} ASC
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class DiagnosticSuggestionService {

    /** category당 1문제, 최대 5개 (docs/05 §4.2 1단계). */
    static final int MAX_SUGGESTIONS = 5;

    /** 자기평가 모드에서 진단을 제안하는 최소 자기평가 레벨 (docs/05 §4.2 1단계). */
    static final int SELF_ASSESSMENT_THRESHOLD = 3;

    private final ChallengeQueryService challengeQueryService;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final UserSkillStateQueryService userSkillStateQueryService;
    private final PlanQueryService planQueryService;

    public DiagnosticSuggestionService(
            ChallengeQueryService challengeQueryService,
            SkillCatalogQueryService skillCatalogQueryService,
            UserSkillStateQueryService userSkillStateQueryService,
            PlanQueryService planQueryService) {
        this.challengeQueryService = challengeQueryService;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.userSkillStateQueryService = userSkillStateQueryService;
        this.planQueryService = planQueryService;
    }

    /** {@code GET /diagnostics/suggestions}와 온보딩 응답의 {@code suggestedDiagnostics}. */
    public List<DiagnosticSuggestionView> suggest(UUID userId) {
        Map<UUID, AssessmentState> assessments =
                userSkillStateQueryService.assessmentStates(userId);
        if (assessments.isEmpty()) {
            return List.of();
        }
        Map<UUID, SkillDetailView> skills = skillCatalogQueryService.activeSkillDetails();
        boolean diagnosticMode =
                assessments.values().stream().noneMatch(state -> state.selfAssessedLevel() != null);
        Map<SkillCategory, Integer> targetCategories =
                targetCategories(assessments, skills, diagnosticMode);
        if (targetCategories.isEmpty()) {
            return List.of();
        }
        List<DiagnosticCandidate> candidates = challengeQueryService.diagnosticCandidates(userId);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<UUID> attempted =
                challengeQueryService.attemptedChallengeIds(userId, challengeIds(candidates));
        Map<UUID, SkillTargetView> targets = planQueryService.activePlanTargets(userId);
        Set<SkillCategory> excluded = excludedCategories(candidates, attempted, skills);
        List<DiagnosticSuggestionView> suggestions = new ArrayList<>();
        for (Map.Entry<SkillCategory, Integer> entry : targetCategories.entrySet()) {
            SkillCategory category = entry.getKey();
            if (excluded.contains(category)) {
                continue;
            }
            Optional<Selected> selected =
                    select(category, candidates, assessments, skills, targets);
            if (selected.isEmpty()) {
                continue;
            }
            Selected found = selected.get();
            suggestions.add(
                    new DiagnosticSuggestionView(
                            category,
                            diagnosticMode ? null : entry.getValue(),
                            skills.get(found.skillId()).ref(),
                            found.candidate().id(),
                            found.candidate().title(),
                            found.candidate().difficulty(),
                            found.candidate().estimatedMinutes()));
            if (suggestions.size() == MAX_SUGGESTIONS) {
                break;
            }
        }
        return List.copyOf(suggestions);
    }

    /** category → 자기평가 최댓값 ({@code SkillCategory} 선언 순서 유지). */
    private static Map<SkillCategory, Integer> targetCategories(
            Map<UUID, AssessmentState> assessments,
            Map<UUID, SkillDetailView> skills,
            boolean diagnosticMode) {
        Map<SkillCategory, Integer> maxLevels = new EnumMap<>(SkillCategory.class);
        assessments.forEach(
                (skillId, state) -> {
                    SkillDetailView skill = skills.get(skillId);
                    if (skill == null || !state.active()) {
                        return;
                    }
                    maxLevels.merge(
                            skill.category(),
                            Objects.requireNonNullElse(state.selfAssessedLevel(), 0),
                            Math::max);
                });
        if (diagnosticMode) {
            return maxLevels;
        }
        Map<SkillCategory, Integer> filtered = new EnumMap<>(SkillCategory.class);
        maxLevels.forEach(
                (category, level) -> {
                    if (level >= SELF_ASSESSMENT_THRESHOLD) {
                        filtered.put(category, level);
                    }
                });
        return filtered;
    }

    /** docs/05 §4.2 2단계: 이미 attempt한 진단 challenge가 있는 category는 통째로 뺀다. */
    private static Set<SkillCategory> excludedCategories(
            List<DiagnosticCandidate> candidates,
            Set<UUID> attempted,
            Map<UUID, SkillDetailView> skills) {
        Set<SkillCategory> excluded = new HashSet<>();
        for (DiagnosticCandidate candidate : candidates) {
            if (!attempted.contains(candidate.id())) {
                continue;
            }
            for (UUID skillId : candidate.skillIds()) {
                SkillDetailView skill = skills.get(skillId);
                if (skill != null) {
                    excluded.add(skill.category());
                }
            }
        }
        return Set.copyOf(excluded);
    }

    private static Optional<Selected> select(
            SkillCategory category,
            List<DiagnosticCandidate> candidates,
            Map<UUID, AssessmentState> assessments,
            Map<UUID, SkillDetailView> skills,
            Map<UUID, SkillTargetView> targets) {
        List<Selected> ranked = new ArrayList<>();
        for (DiagnosticCandidate candidate : candidates) {
            Optional<UUID> skillId =
                    leadingSkill(candidate, category, assessments, skills, targets);
            skillId.ifPresent(id -> ranked.add(new Selected(candidate, id)));
        }
        return ranked.stream().min(order(skills, targets));
    }

    /** challenge가 여러 skill에 걸치면 정렬에서 가장 앞선 skill (docs/05 §4.2 4단계). */
    private static Optional<UUID> leadingSkill(
            DiagnosticCandidate candidate,
            SkillCategory category,
            Map<UUID, AssessmentState> assessments,
            Map<UUID, SkillDetailView> skills,
            Map<UUID, SkillTargetView> targets) {
        Set<UUID> eligible = new LinkedHashSet<>();
        for (UUID skillId : candidate.skillIds()) {
            SkillDetailView skill = skills.get(skillId);
            AssessmentState state = assessments.get(skillId);
            if (skill != null && skill.category() == category && state != null && state.active()) {
                eligible.add(skillId);
            }
        }
        return eligible.stream().min(skillOrder(skills, targets));
    }

    private static Comparator<UUID> skillOrder(
            Map<UUID, SkillDetailView> skills, Map<UUID, SkillTargetView> targets) {
        return Comparator.<UUID>comparingInt(skillId -> priorityRank(targets.get(skillId)))
                .thenComparing(
                        skillId -> importanceBp(targets.get(skillId)), Comparator.reverseOrder())
                .thenComparing(skillId -> skills.get(skillId).code());
    }

    private static Comparator<Selected> order(
            Map<UUID, SkillDetailView> skills, Map<UUID, SkillTargetView> targets) {
        return Comparator.<Selected>comparingInt(
                        selected -> priorityRank(targets.get(selected.skillId())))
                .thenComparing(
                        selected -> importanceBp(targets.get(selected.skillId())),
                        Comparator.reverseOrder())
                .thenComparing(selected -> seedKey(selected.candidate()));
    }

    /** MUST(0) → SHOULD(1) → LATER(2) → target 없음(3). */
    private static int priorityRank(@Nullable SkillTargetView target) {
        if (target == null) {
            return Priority.values().length;
        }
        return target.priority().ordinal();
    }

    private static int importanceBp(@Nullable SkillTargetView target) {
        return target == null ? 0 : target.practicalImportanceBp();
    }

    private static String seedKey(DiagnosticCandidate candidate) {
        String seedKey = candidate.seedKey();
        return seedKey == null ? candidate.id().toString() : seedKey;
    }

    private static Set<UUID> challengeIds(List<DiagnosticCandidate> candidates) {
        Set<UUID> ids = new LinkedHashSet<>();
        candidates.forEach(candidate -> ids.add(candidate.id()));
        return ids;
    }

    private record Selected(DiagnosticCandidate candidate, UUID skillId) {}
}
