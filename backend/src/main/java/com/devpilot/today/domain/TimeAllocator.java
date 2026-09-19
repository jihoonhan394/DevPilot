package com.devpilot.today.domain;

import com.devpilot.common.math.FixedPointMath;
import com.devpilot.today.domain.TaskProposalPolicy.ChallengeOption;
import com.devpilot.today.domain.TaskProposalPolicy.Proposal;
import com.devpilot.today.domain.TaskProposalPolicy.ReadingOption;
import com.devpilot.today.domain.TaskProposalPolicy.SkillContext;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 오늘 시간 배분 (docs/06 §5.6, BL-TDY-04). 순수 규칙 클래스다(ARCH-12).
 *
 * <pre>
 * cap           = comebackMode ? 10 : 20
 * dueCount      = min(due 수, cap)
 * reviewMinutes = dueCount == 0 ? 0 : max(0, min(ceilDiv(dueCount × 1.5), max(5, floorDiv(available × 2_500, 10_000)), available − 5))
 * mainBudget    = available − reviewMinutes
 * limit         = floorDiv(mainBudget × 11_000, 10_000)
 * </pre>
 *
 * 1위 후보를 고른 뒤 그 후보의 과제만 조정한다: 예상 시간이 limit을 넘으면 CHALLENGE는 난이도를 낮추고, READ_CODE는 다음 reading을 찾고, 없으면
 * EXPLAIN(15) → RECALL(min(10, mainBudget)) 순서로 내린다. {@code mainBudget < 10}이면 제안과 무관하게
 * RECALL(mainBudget)이다.
 */
public final class TimeAllocator {

    private final Settings settings;

    public TimeAllocator(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** 복습 상한 (docs/06 §5.6 첫 줄, §6.5 2단계). */
    public int cap(boolean comebackMode) {
        return comebackMode ? settings.comebackMaxPerDay() : settings.maxPerDay();
    }

    /** 복습 시간과 main 예산. */
    public Allocation allocate(int availableMinutes, int dueReviewCount, boolean comebackMode) {
        int cap = cap(comebackMode);
        int dueCount = Math.min(dueReviewCount, cap);
        int reviewMinutes = 0;
        if (dueCount > 0) {
            long perCards =
                    FixedPointMath.ceilDiv(
                            (long) dueCount * settings.reviewMinutesPerCardBp(),
                            FixedPointMath.BP_SCALE);
            long share =
                    Math.max(
                            settings.minAvailableMinutes(),
                            FixedPointMath.floorDiv(
                                    (long) availableMinutes * settings.reviewMaxShareBp(),
                                    FixedPointMath.BP_SCALE));
            long remaining = availableMinutes - (long) settings.minAvailableMinutes();
            reviewMinutes = (int) Math.max(0, Math.min(perCards, Math.min(share, remaining)));
        }
        int mainBudget = availableMinutes - reviewMinutes;
        int limit =
                (int)
                        FixedPointMath.floorDiv(
                                (long) mainBudget * settings.overrunToleranceBp(),
                                FixedPointMath.BP_SCALE);
        return new Allocation(cap, dueCount, reviewMinutes, mainBudget, limit);
    }

    /**
     * 선택된 제안을 예산에 맞춘다.
     *
     * @param challenges 같은 skill의 challenge 후보 (S2는 빈 목록)
     * @param readings 같은 skill의 reading 후보 (S2는 빈 목록)
     */
    public Proposal fit(
            Proposal proposal,
            Allocation allocation,
            SkillContext skill,
            List<ChallengeOption> challenges,
            List<ReadingOption> readings) {
        int mainBudget = allocation.mainBudget();
        if (mainBudget < settings.minMainTaskMinutes()) {
            return TaskProposalPolicy.recall(skill, mainBudget);
        }
        int limit = allocation.limit();
        if (proposal.estimatedMinutes() <= limit) {
            return proposal;
        }
        Optional<Proposal> replacement = sameKindWithin(proposal, challenges, readings, limit);
        if (replacement.isPresent()) {
            return replacement.get();
        }
        Proposal explain = TaskProposalPolicy.explain(skill);
        if (explain.estimatedMinutes() <= limit) {
            return explain;
        }
        return TaskProposalPolicy.recall(
                skill, Math.min(settings.minMainTaskMinutes(), mainBudget));
    }

    private static Optional<Proposal> sameKindWithin(
            Proposal proposal,
            List<ChallengeOption> challenges,
            List<ReadingOption> readings,
            int limit) {
        if (proposal.taskType() == TaskType.CHALLENGE) {
            return TaskProposalPolicy.challengeWithin(challenges, proposal.difficulty() - 1, limit)
                    .map(TaskProposalPolicy::challenge);
        }
        if (proposal.taskType() == TaskType.READ_CODE) {
            // 줄 범위가 고정이라 줄일 수 없다(RC-2). 다음 reading을 key ASC로 이어 본다
            return readings.stream()
                    .filter(reading -> reading.estimatedMinutes() <= limit)
                    .min(Comparator.comparing(ReadingOption::key))
                    .map(TaskProposalPolicy::readCode);
        }
        return Optional.empty();
    }

    /**
     * {@code devpilot.planner.*}·{@code devpilot.review.*}를 정수로 바꾼 값.
     *
     * @param reviewMinutesPerCardBp {@code review-minutes-per-card} (15_000)
     * @param reviewMaxShareBp {@code review-max-share} (2_500)
     * @param minAvailableMinutes {@code min-available-minutes} (5)
     * @param overrunToleranceBp {@code overrun-tolerance} (11_000)
     * @param minMainTaskMinutes {@code min-main-task-minutes} (10)
     * @param maxPerDay {@code review.max-per-day} (20)
     * @param comebackMaxPerDay {@code review.comeback-max-per-day} (10)
     */
    public record Settings(
            int reviewMinutesPerCardBp,
            int reviewMaxShareBp,
            int minAvailableMinutes,
            int overrunToleranceBp,
            int minMainTaskMinutes,
            int maxPerDay,
            int comebackMaxPerDay) {}

    /**
     * 배분 결과.
     *
     * @param dueCount 상한을 적용한 due 수
     */
    public record Allocation(int cap, int dueCount, int reviewMinutes, int mainBudget, int limit) {}
}
