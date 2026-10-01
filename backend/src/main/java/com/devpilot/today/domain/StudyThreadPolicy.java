package com.devpilot.today.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 학습 묶음 — 어제의 다음 걸음 (docs/06 §5.13 TH-1·TH-2·TH-4).
 *
 * <p><b>배울 때는 한 주제를 이어서, 꺼낼 때는 섞어서.</b> 복습은 섞어 내는 것이 맞지만(§6.5 교차 학습), 처음 배우는 구간까지 매일 다른 주제로 바꾸면 무엇
 * 하나 끝나지 않는다. 그래서 개념 노트를 떼는 동안은 점수 경쟁에서 빼고 같은 skill을 이어 간다.
 *
 * <p>묶음은 저장하지 않는다(TH-1) — 최근 main 과제와 노트 진행만 보면 지금 무엇을 하던 중인지 계산된다.
 */
public final class StudyThreadPolicy {

    private final int maxConsecutiveDays;

    /**
     * @param maxConsecutiveDays 한 skill을 이어 갈 수 있는 최대 plan-day (TH-4). 넘으면 묶음을 닫는다
     */
    public StudyThreadPolicy(int maxConsecutiveDays) {
        if (maxConsecutiveDays < 1) {
            throw new IllegalArgumentException("study-thread.max-consecutive-days must be >= 1");
        }
        this.maxConsecutiveDays = maxConsecutiveDays;
    }

    /**
     * 오늘 이어갈 묶음의 skill (TH-2). 없으면 기존 점수로 고른다(TH-3).
     *
     * <p>네 가지를 모두 만족해야 이어 간다 — 최근에 main이었고, 오늘도 후보이고, 그 노트에 남은 단위가 있고, 너무 오래 붙들고 있지 않았다.
     */
    public Optional<String> continuing(ThreadInput input) {
        List<String> recent = input.recentMainSkillCodes();
        if (recent.isEmpty()) {
            return Optional.empty();
        }
        String code = recent.getFirst();
        if (!input.candidates().contains(code)
                || !input.codesWithRemainingLesson().contains(code)) {
            return Optional.empty();
        }
        // TH-4: 막혀서 못 나가는 것을 막는다. 상한에 닿으면 묶음을 닫고 다음 skill로 간다
        return consecutiveDays(recent, code) >= maxConsecutiveDays
                ? Optional.empty()
                : Optional.of(code);
    }

    /**
     * 가장 최근부터 같은 skill이 이어진 plan-day 수.
     *
     * <p>main이 없던 날은 목록에 없다 — 주말에 쉬어도 묶음이 끊기지 않는다. 쉰 날을 끊김으로 보면 평일에만 공부하는 사람은 묶음을 영영 못 만든다.
     */
    private static int consecutiveDays(List<String> recent, String code) {
        int days = 0;
        for (String recentCode : recent) {
            if (!code.equals(recentCode)) {
                break;
            }
            days++;
        }
        return days;
    }

    /**
     * 묶음 판정 입력.
     *
     * @param recentMainSkillCodes 최근 plan-day의 main skill, <b>최근이 앞</b>. main이 없던 날은 빠져 있다
     * @param candidates 오늘의 후보 skill (§5.2)
     * @param codesWithRemainingLesson 노트에 아직 안 푼 단위가 남은 skill
     */
    public record ThreadInput(
            List<String> recentMainSkillCodes,
            Set<String> candidates,
            Set<String> codesWithRemainingLesson) {

        public ThreadInput {
            recentMainSkillCodes = List.copyOf(recentMainSkillCodes);
            candidates = Set.copyOf(candidates);
            codesWithRemainingLesson = Set.copyOf(codesWithRemainingLesson);
        }
    }
}
