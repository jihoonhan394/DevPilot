package com.devpilot.today.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.plan.domain.RiskLevel;
import com.devpilot.skill.domain.Priority;
import com.devpilot.testsupport.TestRuleSettings;
import com.devpilot.testsupport.UnitTest;
import com.devpilot.testsupport.VectorLoader;
import com.devpilot.today.domain.PlannerScoring.CandidateInput;
import com.devpilot.today.domain.PlannerScoring.Context;
import com.devpilot.today.domain.PlannerScoring.FactorInput;
import com.devpilot.today.domain.PlannerScoring.Factors;
import com.devpilot.today.domain.PlannerScoring.MilestoneContext;
import com.devpilot.today.domain.PlannerScoring.MilestoneSpan;
import com.devpilot.today.domain.PlannerScoring.RecentMain;
import com.devpilot.today.domain.PlannerScoring.ScoreInput;
import com.devpilot.today.domain.PlannerScoring.ScoredCandidate;
import com.devpilot.today.domain.PlannerScoring.SkillProfile;
import com.devpilot.today.domain.PlannerScoring.SkillTarget;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** docs/06 §5.2·§5.4·§5.5 규칙과 §5.7 vector ({@code 06-05-planner-score.yaml}, 10행), AC-02 S2. */
@UnitTest
class PlannerScoringTest {

    private static final String VECTOR_FILE = "06-05-planner-score.yaml";
    private static final LocalDate TODAY = LocalDate.parse("2026-10-10");
    private static final UUID MILESTONE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final PlannerScoring scoring = new PlannerScoring(TestRuleSettings.planner());

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("vectors")
    void shouldMatchVectorWhenCandidatesAreScored(
            String id,
            RiskLevel risk,
            EnergyLevel energy,
            @Nullable RecentMain yesterday,
            @Nullable RecentMain dayBefore,
            List<TaskType> recentMainTypes,
            int[] aProposal,
            int[] bProposal,
            long expectedA,
            long expectedB,
            String selected) {
        Context context = new Context(risk, energy, false, recentMainTypes);
        ScoredCandidate a =
                scoring.score(
                        new ScoreInput(
                                "A",
                                Priority.MUST,
                                factorsOfA(),
                                TaskType.EXPLAIN,
                                aProposal[0],
                                aProposal[1],
                                null,
                                yesterday,
                                dayBefore),
                        context);
        ScoredCandidate b =
                scoring.score(
                        new ScoreInput(
                                "B",
                                Priority.SHOULD,
                                factorsOfB(),
                                TaskType.CHALLENGE,
                                bProposal[0],
                                bProposal[1],
                                null,
                                yesterday,
                                dayBefore),
                        context);

        assertThat(a.finalScore()).as(id).isEqualTo(expectedA);
        assertThat(b.finalScore()).as(id).isEqualTo(expectedB);
        assertThat(scoring.rank(List.of(b, a)).getFirst().input().skillCode())
                .as(id)
                .isEqualTo(selected);
    }

    @Test
    void shouldComputeVectorFactorsWhenInputsAreGiven() {
        Factors a = factorsOfA();
        Factors b = factorsOfB();

        assertThat(a).isEqualTo(new Factors(900_000, 600_000, 0, 500_000, 0, 1_000_000));
        assertThat(b).isEqualTo(new Factors(500_000, 800_000, 500_000, 0, 0, 1_000_000));
        assertThat(scoring.baseScore(a)).isEqualTo(520_000);
        assertThat(scoring.baseScore(b)).isEqualTo(485_000);
    }

    @Test
    void shouldCapReviewUrgencyAndUseLeechValue() {
        Factors overdue =
                scoring.factors(
                        new FactorInput(null, AxisLevels.ZERO, 30, false, 0, false, 1_000_000));
        Factors leech =
                scoring.factors(
                        new FactorInput(null, AxisLevels.ZERO, null, true, 0, false, 1_000_000));

        assertThat(overdue.reviewUrgency()).isEqualTo(1_000_000);
        assertThat(overdue.practicalImportance()).isEqualTo(300_000);
        assertThat(overdue.skillGap()).isZero();
        assertThat(leech.reviewUrgency()).isEqualTo(1_000_000);
    }

    @Test
    void shouldUseFloorAndNextMilestoneUrgency() {
        MilestoneSpan ending =
                new MilestoneSpan(
                        MILESTONE_ID,
                        "끝나 가는 milestone",
                        LocalDate.parse("2026-10-01"),
                        LocalDate.parse("2026-10-10"),
                        Set.of("A"));
        MilestoneSpan starting =
                new MilestoneSpan(
                        UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        "다음 milestone",
                        LocalDate.parse("2026-10-11"),
                        LocalDate.parse("2026-10-30"),
                        Set.of("B"));
        MilestoneSpan beginning =
                new MilestoneSpan(
                        UUID.fromString("00000000-0000-0000-0000-000000000003"),
                        "막 시작한 milestone",
                        LocalDate.parse("2026-10-10"),
                        LocalDate.parse("2026-12-31"),
                        Set.of("C"));

        MilestoneContext a = scoring.milestoneContext(TODAY, List.of(ending, starting), "A");
        MilestoneContext b = scoring.milestoneContext(TODAY, List.of(ending, starting), "B");
        MilestoneContext c = scoring.milestoneContext(TODAY, List.of(beginning, starting), "C");

        assertThat(a.urgency()).isEqualTo(1_000_000);
        assertThat(a.current()).isEqualTo(ending);
        assertThat(b.urgency()).isEqualTo(100_000);
        assertThat(b.next()).isEqualTo(starting);
        assertThat(c.urgency()).isEqualTo(200_000);
    }

    @Test
    void shouldExcludeDeferredMetAndLaterUnderHighRiskWhenSelectingCandidates() {
        AxisLevels target = new AxisLevels(3, 3, 3, 3);
        Map<String, SkillTarget> targets =
                Map.of(
                        "S.MUST", new SkillTarget(Priority.MUST, 9_000, target, false),
                        "S.DEFERRED", new SkillTarget(Priority.MUST, 9_000, target, true),
                        "S.MET", new SkillTarget(Priority.SHOULD, 5_000, target, false),
                        "S.LATER", new SkillTarget(Priority.LATER, 3_000, target, false));
        Map<String, SkillProfile> skills =
                Map.of(
                        "S.MUST", profile("S.MUST", AxisLevels.ZERO),
                        "S.DEFERRED", profile("S.DEFERRED", AxisLevels.ZERO),
                        "S.MET", profile("S.MET", target),
                        "S.LATER", profile("S.LATER", AxisLevels.ZERO));

        List<String> low =
                scoring.selectCandidates(
                        new CandidateInput(
                                RiskLevel.LOW,
                                Set.of("S.LATER"),
                                Set.of(),
                                Set.of(),
                                targets,
                                skills));
        List<String> high =
                scoring.selectCandidates(
                        new CandidateInput(
                                RiskLevel.HIGH,
                                Set.of("S.LATER"),
                                Set.of(),
                                Set.of(),
                                targets,
                                skills));
        List<String> metWithDue =
                scoring.selectCandidates(
                        new CandidateInput(
                                RiskLevel.LOW,
                                Set.of(),
                                Set.of(),
                                Set.of("S.MET"),
                                targets,
                                skills));

        assertThat(low).containsExactly("S.LATER", "S.MUST");
        assertThat(high).containsExactly("S.MUST");
        assertThat(metWithDue).contains("S.MET");
    }

    @Test
    void shouldReplaceSkillWithWeakestPrerequisiteWhenReadinessIsLow() {
        Map<String, SkillTarget> targets =
                Map.of(
                        "S.ADVANCED",
                        new SkillTarget(Priority.MUST, 9_000, new AxisLevels(3, 3, 3, 3), false));
        Map<String, SkillProfile> skills =
                Map.of(
                        "S.ADVANCED",
                        new SkillProfile(
                                "S.ADVANCED",
                                "고급",
                                "설명",
                                AxisLevels.ZERO,
                                null,
                                List.of("S.BASE1", "S.BASE2", "S.BASE3")),
                        "S.BASE1",
                        profile("S.BASE1", new AxisLevels(0, 1, 0, 0)),
                        "S.BASE2",
                        profile("S.BASE2", new AxisLevels(0, 0, 0, 0)),
                        "S.BASE3",
                        profile("S.BASE3", new AxisLevels(0, 2, 0, 0)));

        List<String> candidates =
                scoring.selectCandidates(
                        new CandidateInput(
                                RiskLevel.LOW, Set.of(), Set.of(), Set.of(), targets, skills));

        assertThat(PlannerScoring.prerequisiteReadiness(skills.get("S.ADVANCED"), skills))
                .isEqualTo(333_333);
        assertThat(candidates).containsExactly("S.BASE2");
    }

    @Test
    void shouldBreakTiesByImportanceThenLastPracticedThenCode() {
        Factors same = new Factors(500_000, 500_000, 0, 0, 0, 1_000_000);
        Factors moreImportant = new Factors(600_000, 375_000, 0, 0, 0, 1_000_000);
        Context context = new Context(RiskLevel.LOW, EnergyLevel.NORMAL, false, List.of());
        ScoredCandidate practiced =
                scoring.score(
                        new ScoreInput(
                                "T.A",
                                null,
                                same,
                                TaskType.EXPLAIN,
                                15,
                                2,
                                Instant.parse("2026-10-01T00:00:00Z"),
                                null,
                                null),
                        context);
        ScoredCandidate neverPracticed =
                scoring.score(
                        new ScoreInput(
                                "T.B", null, same, TaskType.EXPLAIN, 15, 2, null, null, null),
                        context);
        ScoredCandidate important =
                scoring.score(
                        new ScoreInput(
                                "T.C",
                                null,
                                moreImportant,
                                TaskType.EXPLAIN,
                                15,
                                2,
                                null,
                                null,
                                null),
                        context);
        ScoredCandidate sameNeverPracticed =
                scoring.score(
                        new ScoreInput(
                                "T.D", null, same, TaskType.EXPLAIN, 15, 2, null, null, null),
                        context);

        assertThat(important.finalScore()).isEqualTo(practiced.finalScore());
        assertThat(
                        scoring
                                .rank(
                                        List.of(
                                                practiced,
                                                sameNeverPracticed,
                                                neverPracticed,
                                                important))
                                .stream()
                                .map(scored -> scored.input().skillCode())
                                .toList())
                .containsExactly("T.C", "T.B", "T.D", "T.A");
    }

    @Test
    void shouldApplyEnergyAndComebackModifiersToHardTasks() {
        Factors factors = new Factors(500_000, 500_000, 0, 0, 0, 1_000_000);
        ScoreInput hard =
                new ScoreInput(
                        "H", Priority.LATER, factors, TaskType.CHALLENGE, 30, 4, null, null, null);

        ScoredCandidate high =
                scoring.score(
                        hard, new Context(RiskLevel.CRITICAL, EnergyLevel.HIGH, false, List.of()));
        ScoredCandidate low =
                scoring.score(hard, new Context(RiskLevel.LOW, EnergyLevel.LOW, true, List.of()));

        assertThat(high.modifiers())
                .extracting(PlannerScoring.AppliedModifier::code)
                .containsExactly(PlannerModifier.HIGH_ENERGY_HARD_TASK);
        assertThat(low.modifiers())
                .extracting(PlannerScoring.AppliedModifier::code)
                .containsExactly(
                        PlannerModifier.LOW_ENERGY_DEEP_TASK, PlannerModifier.COMEBACK_HARD_TASK);
        assertThat(low.finalScore()).isEqualTo(low.baseScore() * 7 / 10 * 7 / 10);
        assertThat(low.applied(PlannerModifier.COMEBACK_HARD_TASK)).isTrue();
    }

    @Test
    void shouldPressSameTaskTypeOnlyAfterThreeConsecutiveDays() {
        Factors factors = new Factors(500_000, 500_000, 0, 0, 0, 1_000_000);
        ScoreInput explain =
                new ScoreInput("E", null, factors, TaskType.EXPLAIN, 15, 2, null, null, null);

        assertThat(modifiers(explain, List.of(TaskType.EXPLAIN, TaskType.EXPLAIN))).isEmpty();
        assertThat(
                        modifiers(
                                explain,
                                List.of(TaskType.EXPLAIN, TaskType.EXPLAIN, TaskType.EXPLAIN)))
                .containsExactly(PlannerModifier.MONOTONY_THREE_DAYS);
        assertThat(
                        modifiers(
                                explain,
                                List.of(
                                        TaskType.EXPLAIN,
                                        TaskType.EXPLAIN,
                                        TaskType.EXPLAIN,
                                        TaskType.EXPLAIN,
                                        TaskType.EXPLAIN)))
                .containsExactly(PlannerModifier.MONOTONY_FIVE_DAYS);
        assertThat(
                        modifiers(
                                explain,
                                List.of(
                                        TaskType.EXPLAIN,
                                        TaskType.EXPLAIN,
                                        TaskType.READING,
                                        TaskType.EXPLAIN,
                                        TaskType.EXPLAIN)))
                .isEmpty();
        assertThat(
                        modifiers(
                                new ScoreInput(
                                        "C",
                                        null,
                                        factors,
                                        TaskType.CHALLENGE,
                                        20,
                                        2,
                                        null,
                                        null,
                                        null),
                                List.of(TaskType.EXPLAIN, TaskType.EXPLAIN, TaskType.EXPLAIN)))
                .isEmpty();
    }

    private List<PlannerModifier> modifiers(ScoreInput input, List<TaskType> recentMainTypes) {
        return scoring
                .score(
                        input,
                        new Context(RiskLevel.LOW, EnergyLevel.NORMAL, false, recentMainTypes))
                .modifiers()
                .stream()
                .map(PlannerScoring.AppliedModifier::code)
                .toList();
    }

    private Factors factorsOfA() {
        MilestoneSpan milestone =
                new MilestoneSpan(
                        MILESTONE_ID,
                        "Spring/JPA 핵심",
                        LocalDate.parse("2026-10-01"),
                        LocalDate.parse("2026-10-20"),
                        Set.of("A"));
        long urgency = scoring.milestoneContext(TODAY, List.of(milestone), "A").urgency();
        return scoring.factors(
                new FactorInput(
                        new SkillTarget(Priority.MUST, 9_000, AxisLevels.uniform(5), false),
                        AxisLevels.uniform(2),
                        null,
                        false,
                        urgency,
                        false,
                        1_000_000));
    }

    private Factors factorsOfB() {
        return scoring.factors(
                new FactorInput(
                        new SkillTarget(Priority.SHOULD, 5_000, AxisLevels.uniform(5), false),
                        AxisLevels.uniform(1),
                        2,
                        false,
                        0,
                        false,
                        1_000_000));
    }

    private static SkillProfile profile(String code, AxisLevels planning) {
        return new SkillProfile(code, code, "설명", planning, null, List.of());
    }

    static Stream<Arguments> vectors() {
        return VectorLoader.yamlRows(VECTOR_FILE).stream()
                .map(
                        row ->
                                Arguments.of(
                                        row.get("id"),
                                        RiskLevel.valueOf((String) row.get("risk")),
                                        EnergyLevel.valueOf((String) row.get("energy")),
                                        recentMain(row.get("yesterday")),
                                        recentMain(row.get("dayBefore")),
                                        recentMainTypes(row.get("recentMainTypes")),
                                        proposal(row.get("aProposal")),
                                        proposal(row.get("bProposal")),
                                        ((Number) row.get("expectedA")).longValue(),
                                        ((Number) row.get("expectedB")).longValue(),
                                        row.get("selected")));
    }

    private static @Nullable RecentMain recentMain(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) value;
        return new RecentMain(
                (String) map.get("code"), TaskStatus.valueOf((String) map.get("status")));
    }

    private static List<TaskType> recentMainTypes(@Nullable Object value) {
        if (value == null) {
            return List.of();
        }
        return ((List<?>) value).stream().map(type -> TaskType.valueOf((String) type)).toList();
    }

    private static int[] proposal(Object value) {
        Map<?, ?> map = (Map<?, ?>) value;
        return new int[] {(Integer) map.get("estimated"), (Integer) map.get("difficulty")};
    }
}
