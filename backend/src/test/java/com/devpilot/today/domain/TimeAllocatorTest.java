package com.devpilot.today.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.testsupport.TestRuleSettings;
import com.devpilot.testsupport.UnitTest;
import com.devpilot.today.domain.TaskProposalPolicy.ChallengeOption;
import com.devpilot.today.domain.TaskProposalPolicy.Proposal;
import com.devpilot.today.domain.TaskProposalPolicy.ReadingOption;
import com.devpilot.today.domain.TaskProposalPolicy.SkillContext;
import com.devpilot.today.domain.TimeAllocator.Allocation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;

/** docs/06 §5.6 vector ({@code 06-05-time-allocation.csv}, 6행)와 과제 조정 규칙, AC-02 S1·S2·S3. */
@UnitTest
class TimeAllocatorTest {

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
}
