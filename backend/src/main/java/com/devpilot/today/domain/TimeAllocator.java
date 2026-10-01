package com.devpilot.today.domain;

import com.devpilot.common.math.FixedPointMath;
import com.devpilot.today.domain.TaskProposalPolicy.ChallengeOption;
import com.devpilot.today.domain.TaskProposalPolicy.Proposal;
import com.devpilot.today.domain.TaskProposalPolicy.ReadingOption;
import com.devpilot.today.domain.TaskProposalPolicy.SkillContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
 *
 * <p>main을 정하고도 {@code extra-task-min-minutes} 이상이 남으면 2위 후보부터 차례로 추가 과제를 만든다({@link #fitExtras},
 * docs/06 §5.6 "추가 과제"). 같은 skill·같은 재료는 하루에 한 번만 쓰고, 추가 과제는 최대 {@code max-extra-tasks}개다.
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

    // ---------------------------------------------------------------------------------------------
    // docs/06 §5.6 추가 과제
    // ---------------------------------------------------------------------------------------------

    /**
     * main을 뺀 남은 예산을 2위 후보부터 채운다 (docs/06 §5.6 "추가 과제"). 순위 순서대로 훑으며 남은 예산에 들어가는 제안만 고르고, 남은 예산이
     * {@code extra-task-min-minutes} 미만이 되거나 {@code max-extra-tasks}개를 채우면 멈춘다. 같은 skill과 같은
     * 재료(challenge·reading)는 하루에 한 번만 쓴다. RECALL로는 내려가지 않는다 — RECALL은 남는 시간을 메우는 예비 과제이지 추가로 붙일 과제가
     * 아니다.
     *
     * @param main 이미 정해진(조정까지 끝난) main 과제
     * @param ranked main을 뺀 나머지 후보 (docs/06 §5.5 순위 순서)
     * @return 고른 추가 과제 (고른 순서 = sort_order 순서)
     */
    public List<FittedExtra> fitExtras(
            Allocation allocation, Proposal main, List<ExtraCandidate> ranked) {
        int remaining = Math.max(0, allocation.mainBudget() - main.estimatedMinutes());
        if (settings.maxExtraTasks() <= 0) {
            return List.of();
        }
        Set<String> usedMaterials = new LinkedHashSet<>();
        materialKey(main).ifPresent(usedMaterials::add);
        List<FittedExtra> extras = new ArrayList<>();
        for (ExtraCandidate candidate : ranked) {
            if (extras.size() >= settings.maxExtraTasks()
                    || remaining < settings.extraTaskMinMinutes()) {
                break;
            }
            Optional<Proposal> extra = fitExtra(candidate, remaining, usedMaterials);
            if (extra.isEmpty()) {
                continue;
            }
            Proposal chosen = extra.get();
            materialKey(chosen).ifPresent(usedMaterials::add);
            extras.add(new FittedExtra(candidate.skill(), chosen));
            remaining = Math.max(0, remaining - chosen.estimatedMinutes());
        }
        return List.copyOf(extras);
    }

    /** 후보 1개를 남은 예산에 맞춘다. 맞출 수 없으면 empty(그 후보를 건너뛴다). */
    private Optional<Proposal> fitExtra(
            ExtraCandidate candidate, int remaining, Set<String> usedMaterials) {
        int limit =
                (int)
                        FixedPointMath.floorDiv(
                                (long) remaining * settings.overrunToleranceBp(),
                                FixedPointMath.BP_SCALE);
        Proposal proposal = candidate.proposal();
        boolean reusesMaterial = materialKey(proposal).filter(usedMaterials::contains).isPresent();
        if (!reusesMaterial && proposal.estimatedMinutes() <= limit) {
            return Optional.of(proposal);
        }
        Optional<Proposal> sameKind = sameKindForExtra(candidate, limit, usedMaterials);
        if (sameKind.isPresent()) {
            return sameKind;
        }
        Proposal explain = TaskProposalPolicy.explain(candidate.skill());
        return explain.estimatedMinutes() <= limit ? Optional.of(explain) : Optional.empty();
    }

    /** 같은 유형의 다른 재료로 바꿔 본다. 이미 쓴 재료는 뺀다. */
    private static Optional<Proposal> sameKindForExtra(
            ExtraCandidate candidate, int limit, Set<String> usedMaterials) {
        Proposal proposal = candidate.proposal();
        if (proposal.taskType() == TaskType.CHALLENGE) {
            List<ChallengeOption> options =
                    candidate.challenges().stream()
                            .filter(option -> !usedMaterials.contains(challengeKey(option.id())))
                            .toList();
            return TaskProposalPolicy.challengeWithin(options, proposal.difficulty(), limit)
                    .map(TaskProposalPolicy::challenge);
        }
        if (proposal.taskType() == TaskType.READ_CODE) {
            return candidate.readings().stream()
                    .filter(reading -> !usedMaterials.contains(readingKey(reading.key())))
                    .filter(reading -> reading.estimatedMinutes() <= limit)
                    .min(Comparator.comparing(ReadingOption::key))
                    .map(TaskProposalPolicy::readCode);
        }
        return Optional.empty();
    }

    /** 하루 안에서 재료가 겹치는지 보는 키. 재료가 없는 과제는 empty. */
    private static Optional<String> materialKey(Proposal proposal) {
        if (proposal.challengeId() != null) {
            return Optional.of(challengeKey(proposal.challengeId()));
        }
        if (proposal.readingKey() != null) {
            return Optional.of(readingKey(proposal.readingKey()));
        }
        return Optional.empty();
    }

    private static String challengeKey(UUID challengeId) {
        return "CHALLENGE:" + challengeId;
    }

    private static String readingKey(String key) {
        return "READING:" + key;
    }

    /**
     * {@code devpilot.planner.*}·{@code devpilot.review.*}를 정수로 바꾼 값.
     *
     * @param reviewMinutesPerCardBp {@code review-minutes-per-card} (15_000)
     * @param reviewMaxShareBp {@code review-max-share} (2_500)
     * @param minAvailableMinutes {@code min-available-minutes} (5)
     * @param overrunToleranceBp {@code overrun-tolerance} (11_000)
     * @param minMainTaskMinutes {@code min-main-task-minutes} (10)
     * @param extraTaskMinMinutes {@code extra-task-min-minutes} (15)
     * @param maxExtraTasks {@code max-extra-tasks} (3)
     * @param maxPerDay {@code review.max-per-day} (20)
     * @param comebackMaxPerDay {@code review.comeback-max-per-day} (10)
     */
    public record Settings(
            int reviewMinutesPerCardBp,
            int reviewMaxShareBp,
            int minAvailableMinutes,
            int overrunToleranceBp,
            int minMainTaskMinutes,
            int extraTaskMinMinutes,
            int maxExtraTasks,
            int maxPerDay,
            int comebackMaxPerDay) {}

    /**
     * 배분 결과.
     *
     * @param dueCount 상한을 적용한 due 수
     */
    public record Allocation(int cap, int dueCount, int reviewMinutes, int mainBudget, int limit) {}

    /**
     * 추가 과제 후보 (docs/06 §5.6). main을 뺀 나머지 후보를 순위 순서로 넘긴다.
     *
     * @param proposal 그 skill의 §5.3 제안 (조정 전)
     */
    public record ExtraCandidate(
            SkillContext skill,
            Proposal proposal,
            List<ChallengeOption> challenges,
            List<ReadingOption> readings) {

        public ExtraCandidate {
            challenges = List.copyOf(challenges);
            readings = List.copyOf(readings);
        }
    }

    /** 예산에 맞춘 추가 과제와 그 후보 skill (docs/06 §5.6). */
    public record FittedExtra(SkillContext skill, Proposal proposal) {}
}
