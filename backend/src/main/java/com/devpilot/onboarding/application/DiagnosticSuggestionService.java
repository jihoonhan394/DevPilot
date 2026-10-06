package com.devpilot.onboarding.application;

import com.devpilot.plan.application.PlanQueryService;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillInfo;
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
 *       없음)는 활성 자기평가가 있는 category 전부, 자기평가 모드는 그 category의 자기평가 최댓값이 {@code
 *       SELF_ASSESSMENT_THRESHOLD} 이상인 것
 *   <li><b>이미 푼 진단 문제만</b> 뺀다(ADR-059). category를 통째로 빼면 자기평가를 고쳐 수준이 올라가도 그 새 주장을 확인할 자리가 없다 — 추정이
 *       반증 가능해야 한다. 같은 문제를 다시 내지도 않는다
 *   <li>후보: {@code VALIDATED} 공용 {@code DIAGNOSTIC} challenge 중 그 category의 활성 자기평가 skill을 가진 것
 *   <li>정렬: <b>난이도 거리</b>(ADR-058) → priority(MUST→SHOULD→LATER→없음) → practicalImportance DESC →
 *       {@code seed_key} ASC
 * </ol>
 *
 * <p>난이도 거리는 주장한 수준을 재기 위한 것이다(ADR-058). 자기평가 모드의 목표는 {@code min(claimedLevel, 3)}이고 같은 난이도가 가장 앞,
 * 없으면 목표보다 낮은 쪽이 먼저다 — 주장보다 어려운 문제를 내면 정직하게 답한 사람이 떨어져 자기평가가 꺼진다. 진단 모드는 {@code difficulty}가 그대로
 * 레벨이 되므로 (docs/06 §7.4) 가장 높은 난이도를 고른다.
 */
@Service
@Transactional(readOnly = true)
public class DiagnosticSuggestionService {

    /** category당 1문제, 최대 5개 (docs/05 §4.2 1단계). */
    static final int MAX_SUGGESTIONS = 5;

    /**
     * 자기평가 모드에서 진단을 제안하는 최소 자기평가 레벨 (docs/05 §4.2 1단계, ADR-058). 1이므로 0만 빠진다 — 0은 확인할 주장이 없고 개념
     * 노트부터 가는 것이 맞다(ADR-057).
     */
    static final int SELF_ASSESSMENT_THRESHOLD = 1;

    /** 자기평가 상한 (docs/06 §7.5 {@code devpilot.skill.self-assessment-cap}과 같은 값). */
    private static final int CLAIM_CAP = 3;

    /**
     * 주장이 없을 때(진단 모드) 재기 시작하는 난이도 (docs/05 §4.2, ADR-068). 가장 어려운 문제를 내면 실패가 기본이 되고, 실패는 레벨을 주지
     * 않는다. 통과하면 레벨 2에서 시작하고, 더 높다고 생각하면 자기평가로 직접 올린다.
     */
    private static final int UNKNOWN_LEVEL_DIFFICULTY = 2;

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
        Map<UUID, SkillInfo> skills = skillCatalogQueryService.activeSkillDetails();
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
        // 진단을 실제로 받은 것(제출·평가)만 제외한다. 시작만 하고 나온 것은 다시 제안해 이어서 풀게 한다
        // (docs/05 §4.2 2단계).
        Set<UUID> diagnosed =
                challengeQueryService.diagnosedChallengeIds(userId, challengeIds(candidates));
        Map<UUID, SkillTargetView> targets = planQueryService.activePlanTargets(userId);
        // ADR-059: 이미 푼 문제만 뺀다. category를 통째로 빼면 주장이 올라가도 그 새 수준을 확인할 자리가 없다.
        List<DiagnosticCandidate> unseen =
                candidates.stream()
                        .filter(candidate -> !diagnosed.contains(candidate.id()))
                        .toList();
        // category마다 한 문제를 고르고, 상한은 그 뒤에 적용한다. 선언 순서로 자르면 트랙이 기대는 분야가
        // 뒤 순번이라는 이유만으로 영영 안 나온다 — 연동 트랙 사용자가 INTEGRATION을 못 받던 이유다.
        List<Picked> picked = new ArrayList<>();
        for (Map.Entry<SkillCategory, Integer> entry : targetCategories.entrySet()) {
            select(
                            entry.getKey(),
                            unseen,
                            assessments,
                            skills,
                            targets,
                            targetDifficulty(diagnosticMode, entry.getValue()))
                    .ifPresent(
                            found ->
                                    picked.add(
                                            new Picked(entry.getKey(), entry.getValue(), found)));
        }
        return picked.stream()
                .sorted(byPlanRelevance(targets))
                .limit(MAX_SUGGESTIONS)
                .map(
                        pick ->
                                new DiagnosticSuggestionView(
                                        pick.category(),
                                        diagnosticMode ? null : pick.claimedLevel(),
                                        skills.get(pick.selected().skillId()).ref(),
                                        pick.selected().candidate().id(),
                                        pick.selected().candidate().title(),
                                        pick.selected().candidate().difficulty(),
                                        pick.selected().candidate().estimatedMinutes(),
                                        challengeQueryService
                                                .findActiveAttemptId(
                                                        userId, pick.selected().candidate().id())
                                                .orElse(null)))
                .toList();
    }

    /**
     * 상한을 넘을 때 <b>무엇을 남길지</b>의 순서 (docs/05 §4.2 1단계). 활성 plan에서 중요한 skill의 category가 먼저다 — 같은 조건이면
     * category 선언 순서로 안정화한다.
     *
     * <p>category 안에서 문제를 고르는 순서({@link #order})와 같은 키를 쓴다. 한쪽은 plan 중요도를 보고 다른 쪽은 선언 순서만 보면, 고르는
     * 기준과 남기는 기준이 어긋난다.
     */
    private static Comparator<Picked> byPlanRelevance(Map<UUID, SkillTargetView> targets) {
        return Comparator.<Picked>comparingInt(
                        pick -> priorityRank(targets.get(pick.selected().skillId())))
                .thenComparing(
                        pick -> importanceBp(targets.get(pick.selected().skillId())),
                        Comparator.reverseOrder())
                .thenComparing(pick -> pick.category().ordinal());
    }

    /** 한 category에서 고른 문제 하나와 그 주장 수준. */
    private record Picked(SkillCategory category, Integer claimedLevel, Selected selected) {}

    /** category → 자기평가 최댓값 ({@code SkillCategory} 선언 순서 유지). */
    private static Map<SkillCategory, Integer> targetCategories(
            Map<UUID, AssessmentState> assessments,
            Map<UUID, SkillInfo> skills,
            boolean diagnosticMode) {
        Map<SkillCategory, Integer> maxLevels = new EnumMap<>(SkillCategory.class);
        assessments.forEach(
                (skillId, state) -> {
                    SkillInfo skill = skills.get(skillId);
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

    private static Optional<Selected> select(
            SkillCategory category,
            List<DiagnosticCandidate> candidates,
            Map<UUID, AssessmentState> assessments,
            Map<UUID, SkillInfo> skills,
            Map<UUID, SkillTargetView> targets,
            @Nullable Integer targetDifficulty) {
        List<Selected> ranked = new ArrayList<>();
        for (DiagnosticCandidate candidate : candidates) {
            Optional<UUID> skillId =
                    leadingSkill(candidate, category, assessments, skills, targets);
            skillId.ifPresent(id -> ranked.add(new Selected(candidate, id)));
        }
        return ranked.stream().min(order(skills, targets, targetDifficulty));
    }

    /**
     * 목표 난이도 (docs/05 §4.2 4단계).
     *
     * <p>자기평가 모드는 주장한 수준에 맞춘다(ADR-058). <b>진단 모드는 주장이 없으므로 {@code UNKNOWN_LEVEL_DIFFICULTY}에서
     * 시작한다</b> — 전에는 "통과하면 가장 높은 레벨을 준다"는 이유로 가장 어려운 문제를 냈는데, 실패하면 아무것도 주지 않으므로(§7.4 {@code
     * DIAGNOSTIC_FAILED}는 레벨을 올리지 않는다) 결과가 <b>전부 아니면 전무</b>였다. 수준을 모르는 사람에게 최고 난이도를 내는 것은 재는 것이 아니라
     * 떨어뜨리는 것이다(ADR-068).
     */
    private static int targetDifficulty(boolean diagnosticMode, int claimedLevel) {
        return diagnosticMode ? UNKNOWN_LEVEL_DIFFICULTY : Math.min(claimedLevel, CLAIM_CAP);
    }

    /**
     * 목표 난이도에서 얼마나 먼지. 같으면 0이고, 같은 거리면 <b>낮은 쪽이 먼저다</b> — 목표보다 어려운 문제는 정직하게 답한 사람을 떨어뜨려 자기평가를 끄기
     * 때문이다(ADR-058). 진단 모드도 이제 목표가 있다(ADR-068).
     */
    private static int difficultyRank(int difficulty, int targetDifficulty) {
        int distance = Math.abs(difficulty - targetDifficulty);
        return distance * 2 + (difficulty > targetDifficulty ? 1 : 0);
    }

    /** challenge가 여러 skill에 걸치면 정렬에서 가장 앞선 skill (docs/05 §4.2 4단계). */
    private static Optional<UUID> leadingSkill(
            DiagnosticCandidate candidate,
            SkillCategory category,
            Map<UUID, AssessmentState> assessments,
            Map<UUID, SkillInfo> skills,
            Map<UUID, SkillTargetView> targets) {
        Set<UUID> eligible = new LinkedHashSet<>();
        for (UUID skillId : candidate.skillIds()) {
            SkillInfo skill = skills.get(skillId);
            AssessmentState state = assessments.get(skillId);
            if (skill != null && skill.category() == category && state != null && state.active()) {
                eligible.add(skillId);
            }
        }
        return eligible.stream().min(skillOrder(skills, targets));
    }

    private static Comparator<UUID> skillOrder(
            Map<UUID, SkillInfo> skills, Map<UUID, SkillTargetView> targets) {
        return Comparator.<UUID>comparingInt(skillId -> priorityRank(targets.get(skillId)))
                .thenComparing(
                        skillId -> importanceBp(targets.get(skillId)), Comparator.reverseOrder())
                .thenComparing(skillId -> skills.get(skillId).code());
    }

    private static Comparator<Selected> order(
            Map<UUID, SkillInfo> skills,
            Map<UUID, SkillTargetView> targets,
            @Nullable Integer targetDifficulty) {
        return Comparator.<Selected>comparingInt(
                        selected ->
                                difficultyRank(selected.candidate().difficulty(), targetDifficulty))
                .thenComparingInt(selected -> priorityRank(targets.get(selected.skillId())))
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
