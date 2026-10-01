package com.devpilot.today.domain;

import com.devpilot.common.domain.TipLevel;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 오늘의 팁 고르기 (docs/06 §5.12). 하루에 하나, 결정적이고 AI를 쓰지 않는다.
 *
 * <p>순서는 <b>지금 하는 것에 가까운 순</b>이다 — 오늘 과제의 skill → 최근 한 주에 끝낸 것 → 계획에 있는 것 → 나머지. 앞 묶음에 후보가 하나라도 있으면
 * 뒤 묶음은 보지 않는다. 오늘 하는 일과 상관없는 팁이 먼저 나오면 읽을 이유가 없기 때문이다.
 */
public final class DailyTipSelector {

    /**
     * 오늘 보여 줄 팁 (TIP-1·TIP-2 제외 후 묶음 1~4 순서).
     *
     * @return 네 묶음이 모두 비면 빈 값 — 오늘의 팁은 없다
     */
    public Optional<DailyTip> select(TipInput input) {
        List<DailyTip> pool =
                input.tips().stream()
                        // TIP-1 은퇴, TIP-2 한 번 받은 팁은 다시 제안하지 않는다
                        .filter(tip -> !tip.retired())
                        .filter(tip -> !input.alreadyShownKeys().contains(tip.key()))
                        .toList();
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        for (Set<String> group :
                List.of(
                        input.todayMainSkillCodes(),
                        input.recentlyCompletedSkillCodes(),
                        input.planSkillCodes())) {
            Optional<DailyTip> found = first(pool, group, input.basicTipsFirst());
            if (found.isPresent()) {
                return found;
            }
        }
        return pool.stream().min(order(input.basicTipsFirst()));
    }

    /** 그 묶음에서 정렬 첫 번째. 겹치는 skill이 하나도 없으면 빈 값이다. */
    private static Optional<DailyTip> first(
            List<DailyTip> pool, Set<String> skillCodes, boolean basicTipsFirst) {
        return skillCodes.isEmpty()
                ? Optional.empty()
                : pool.stream()
                        .filter(tip -> tip.skillCodes().stream().anyMatch(skillCodes::contains))
                        .min(order(basicTipsFirst));
    }

    /**
     * 묶음 안 정렬 (docs/06 §5.12).
     *
     * <p>입문 트랙({@code basicTipsFirst})은 {@code BASIC}을 먼저 낸다 — 시작한 사람이 그날 바로 쓸 수 있는 것이 먼저다. 그 밖에는
     * key ASC 하나로 정한다. 어느 쪽이든 같은 입력에 항상 같은 팁이 나온다.
     */
    private static Comparator<DailyTip> order(boolean basicTipsFirst) {
        Comparator<DailyTip> byKey = Comparator.comparing(DailyTip::key);
        return basicTipsFirst
                ? Comparator.comparingInt((DailyTip tip) -> tip.level() == TipLevel.BASIC ? 0 : 1)
                        .thenComparing(byKey)
                : byKey;
    }

    /**
     * 선택 입력.
     *
     * @param alreadyShownKeys 그 사용자의 {@code user_daily_tip}에 이미 있는 key (TIP-2)
     * @param todayMainSkillCodes 오늘 daily plan의 main task skill. 없으면 빈 집합
     * @param recentlyCompletedSkillCodes 최근 7 plan-day에 완료한 과제의 skill
     * @param planSkillCodes 활성 plan의 목표 skill ({@code deferred} 무관)
     * @param basicTipsFirst 트랙 기본값 {@code trackDefaults.basicTipsFirst}
     */
    public record TipInput(
            List<DailyTip> tips,
            Set<String> alreadyShownKeys,
            Set<String> todayMainSkillCodes,
            Set<String> recentlyCompletedSkillCodes,
            Set<String> planSkillCodes,
            boolean basicTipsFirst) {

        public TipInput {
            tips = List.copyOf(tips);
            alreadyShownKeys = Set.copyOf(alreadyShownKeys);
            todayMainSkillCodes = Set.copyOf(todayMainSkillCodes);
            recentlyCompletedSkillCodes = Set.copyOf(recentlyCompletedSkillCodes);
            planSkillCodes = Set.copyOf(planSkillCodes);
        }
    }
}
