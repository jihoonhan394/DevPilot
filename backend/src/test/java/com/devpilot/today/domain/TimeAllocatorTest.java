package com.devpilot.today.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.testsupport.TestRuleSettings;
import com.devpilot.testsupport.UnitTest;
import com.devpilot.testsupport.VectorLoader;
import com.devpilot.today.domain.TaskProposalPolicy.ChallengeOption;
import com.devpilot.today.domain.TaskProposalPolicy.Proposal;
import com.devpilot.today.domain.TaskProposalPolicy.ReadingOption;
import com.devpilot.today.domain.TaskProposalPolicy.SkillContext;
import com.devpilot.today.domain.TimeAllocator.Allocation;
import com.devpilot.today.domain.TimeAllocator.ExtraCandidate;
import com.devpilot.today.domain.TimeAllocator.FittedExtra;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * docs/06 §5.6 vector ({@code 06-05-time-allocation.csv} 6행, {@code 06-05-extra-tasks.yaml} 5행)와 과제
 * 조정 규칙, AC-02 S1·S2·S3.
 */
@UnitTest
class TimeAllocatorTest {

    private static final String EXTRA_VECTOR_FILE = "06-05-extra-tasks.yaml";

    private static final SkillContext SKILL =
            new SkillContext("S", "Skill", "설명", AxisLevels.ZERO, false);

    private final TimeAllocator allocator = new TimeAllocator(TestRuleSettings.timeAllocation());

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = "/vectors/06-05-time-allocation.csv", numLinesToSkip = 2)
    void shouldMatchVectorWhenTimeIsAllocated(
            String id,
            int available,
            int due,
            boolean comeback,
            int expectedReview,
            int expectedMainBudget,
            int expectedLimit) {
        Allocation allocation = allocator.allocate(available, due, comeback);

        assertThat(allocation.reviewMinutes()).as(id).isEqualTo(expectedReview);
        assertThat(allocation.mainBudget()).as(id).isEqualTo(expectedMainBudget);
        assertThat(allocation.limit()).as(id).isEqualTo(expectedLimit);
    }

    @ParameterizedTest(name = "[{index}] available {0}")
    @CsvSource({"5, 0, 5, 5", "720, 20, 690, 759"})
    void shouldAllocateBoundaryAvailableMinutes(
            int available, int due, int expectedMainBudget, int expectedLimit) {
        Allocation allocation = allocator.allocate(available, due, false);

        assertThat(allocation.mainBudget()).isEqualTo(expectedMainBudget);
        assertThat(allocation.limit()).isEqualTo(expectedLimit);
    }

    @Test
    void shouldUseRecallWithMainBudgetWhenBudgetIsBelowTen() {
        Allocation allocation = allocator.allocate(5, 4, false);

        Proposal fitted =
                allocator.fit(
                        TaskProposalPolicy.explain(SKILL), allocation, SKILL, List.of(), List.of());

        assertThat(fitted.taskType()).isEqualTo(TaskType.RECALL);
        assertThat(fitted.estimatedMinutes()).isEqualTo(5);
    }

    @Test
    void shouldKeepProposalWhenItFitsLimit() {
        Allocation allocation = allocator.allocate(30, 3, false);
        Proposal reading = TaskProposalPolicy.reading(SKILL);

        assertThat(allocator.fit(reading, allocation, SKILL, List.of(), List.of()))
                .isEqualTo(reading);
    }

    @Test
    void shouldFallBackToExplainThenRecallWhenProposalIsTooLong() {
        Proposal reading = TaskProposalPolicy.reading(SKILL);
        Allocation explainFits = allocator.allocate(15, 0, false);
        Allocation tight = allocator.allocate(12, 0, false);

        assertThat(allocator.fit(reading, explainFits, SKILL, List.of(), List.of()).taskType())
                .isEqualTo(TaskType.EXPLAIN);
        Proposal recall = allocator.fit(reading, tight, SKILL, List.of(), List.of());
        assertThat(recall.taskType()).isEqualTo(TaskType.RECALL);
        assertThat(recall.estimatedMinutes()).isEqualTo(10);
    }

    @Test
    void shouldLowerChallengeDifficultyWhenChallengeIsTooLong() {
        ChallengeOption hard =
                new ChallengeOption(UUID.randomUUID(), "S.HARD", "어려운 문제", null, 3, 40);
        ChallengeOption easier =
                new ChallengeOption(UUID.randomUUID(), "S.EASY", "쉬운 문제", null, 2, 20);
        Allocation allocation = allocator.allocate(30, 0, false);

        Proposal fitted =
                allocator.fit(
                        TaskProposalPolicy.challenge(hard),
                        allocation,
                        SKILL,
                        List.of(hard, easier),
                        List.of());

        assertThat(fitted.challengeId()).isEqualTo(easier.id());
        assertThat(fitted.estimatedMinutes()).isEqualTo(20);
    }

    @Test
    void shouldTryNextReadingWhenReadingIsTooLong() {
        ReadingOption longReading =
                new ReadingOption("READ.R.A.001", "Repo", "A.java", 1, 200, 40, "질문");
        ReadingOption shortReading =
                new ReadingOption("READ.R.B.001", "Repo", "B.java", 1, 50, 15, "질문");
        Allocation allocation = allocator.allocate(20, 0, false);

        Proposal fitted =
                allocator.fit(
                        TaskProposalPolicy.readCode(longReading),
                        allocation,
                        SKILL,
                        List.of(),
                        List.of(longReading, shortReading));

        assertThat(fitted.readingKey()).isEqualTo("READ.R.B.001");
    }

    @Test
    void shouldCapDueCountByComebackMode() {
        assertThat(allocator.allocate(60, 40, true).dueCount()).isEqualTo(10);
        assertThat(allocator.allocate(60, 40, false).dueCount()).isEqualTo(20);
        assertThat(allocator.cap(true)).isEqualTo(10);
    }

    @ParameterizedTest(name = "[{index}] {0} {1}")
    @MethodSource("extraVectors")
    void shouldMatchVectorWhenExtraTasksFillTheDay(
            String id, String caseName, Map<String, Object> row) {
        String description = id + " " + caseName;
        Allocation allocation =
                allocator.allocate(
                        (Integer) row.get("available"),
                        (Integer) row.get("due"),
                        (Boolean) row.get("comeback"));
        Proposal main = proposal(skill("MAIN"), map(row.get("main")));
        List<ExtraCandidate> candidates = new ArrayList<>();
        for (Object item : (List<?>) row.get("candidates")) {
            candidates.add(candidate(map(item)));
        }

        List<FittedExtra> extras = allocator.fitExtras(allocation, main, candidates);

        List<Map<String, Object>> expected = new ArrayList<>();
        for (Object item : (List<?>) row.get("expected")) {
            expected.add(map(item));
        }
        assertThat(extras).as(description).hasSameSizeAs(expected);
        for (int index = 0; index < expected.size(); index++) {
            Map<String, Object> want = expected.get(index);
            FittedExtra got = extras.get(index);
            assertThat(got.skill().code()).as(description).isEqualTo(want.get("skill"));
            assertThat(got.proposal().taskType())
                    .as(description)
                    .isEqualTo(TaskType.valueOf((String) want.get("type")));
            assertThat(got.proposal().estimatedMinutes())
                    .as(description)
                    .isEqualTo(want.get("estimated"));
            if (want.containsKey("readingKey")) {
                assertThat(got.proposal().readingKey())
                        .as(description)
                        .isEqualTo(want.get("readingKey"));
            }
            if (want.containsKey("challengeSeedKey")) {
                assertThat(got.proposal().challengeId())
                        .as(description)
                        .isEqualTo(challengeId((String) want.get("challengeSeedKey")));
            }
        }
    }

    @Test
    void shouldNotUseTheSameSkillTwiceInOneDay() {
        Allocation allocation = allocator.allocate(180, 0, false);
        SkillContext skill = skill("B");
        List<ExtraCandidate> candidates =
                List.of(
                        new ExtraCandidate(
                                skill, TaskProposalPolicy.explain(skill), List.of(), List.of()));

        List<FittedExtra> extras =
                allocator.fitExtras(allocation, TaskProposalPolicy.explain(SKILL), candidates);

        assertThat(extras).hasSize(1);
    }

    static Stream<Arguments> extraVectors() {
        return VectorLoader.yamlRows(EXTRA_VECTOR_FILE).stream()
                .map(row -> Arguments.of(row.get("id"), row.get("case"), row));
    }

    private static ExtraCandidate candidate(Map<String, Object> row) {
        SkillContext skill = skill((String) row.get("skill"));
        List<ChallengeOption> challenges = new ArrayList<>();
        for (Object item : (List<?>) row.getOrDefault("challenges", List.of())) {
            Map<String, Object> option = map(item);
            String seedKey = (String) option.get("seedKey");
            challenges.add(
                    new ChallengeOption(
                            challengeId(seedKey),
                            seedKey,
                            "문제 " + seedKey,
                            "시나리오",
                            (Integer) option.get("difficulty"),
                            (Integer) option.get("estimated")));
        }
        List<ReadingOption> readings = new ArrayList<>();
        for (Object item : (List<?>) row.getOrDefault("readings", List.of())) {
            Map<String, Object> option = map(item);
            readings.add(reading((String) option.get("key"), (Integer) option.get("estimated")));
        }
        return new ExtraCandidate(skill, proposal(skill, row), challenges, readings);
    }

    /** vector 행의 {@code type}·{@code estimated}·재료 key로 §5.3이 만들었을 제안을 그대로 만든다. */
    private static Proposal proposal(SkillContext skill, Map<String, Object> row) {
        TaskType type = TaskType.valueOf((String) row.get("type"));
        int estimated = (Integer) row.get("estimated");
        String seedKey = (String) row.get("challengeSeedKey");
        String readingKey = (String) row.get("readingKey");
        int difficulty = (Integer) row.getOrDefault("difficulty", 2);
        return new Proposal(
                type,
                estimated,
                difficulty,
                skill.name() + " 과제",
                "설명",
                seedKey == null ? null : challengeId(seedKey),
                readingKey,
                null,
                readingKey == null ? null : "Repo");
    }

    private static SkillContext skill(String code) {
        return new SkillContext(code, "Skill " + code, "설명", AxisLevels.ZERO, false);
    }

    private static ReadingOption reading(String key, int estimatedMinutes) {
        return new ReadingOption(key, "Repo", "src/Main.java", 1, 40, estimatedMinutes, "질문");
    }

    private static UUID challengeId(String seedKey) {
        return UUID.nameUUIDFromBytes(seedKey.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
