package com.devpilot.plan.domain;

import java.util.List;
import java.util.Objects;

/**
 * 위험도가 여러 날 이어지면 replan을 권한다 (docs/06 §11.1, ADR-046). 순수 규칙 클래스다.
 *
 * <p>세는 단위는 <b>스냅샷이 있는 날</b>이다. 앱을 안 연 날은 스냅샷이 없어 자연히 건너뛴다 — 쉰 날을 세어 재촉하지 않는다.
 */
public final class ReplanRecommendationPolicy {

    private ReplanRecommendationPolicy() {}

    /**
     * 권해야 하는가.
     *
     * @param recentNewestFirst 최근 스냅샷의 위험도, <b>최신 순</b>. 호출자가 {@code afterDays}개까지만 읽어 온다
     * @param afterDays 연속으로 위험해야 하는 날 수 (설정값, 1 이상)
     * @return {@code recentNewestFirst}가 {@code afterDays}개 이상이고 그 앞쪽 {@code afterDays}개가 모두 {@code
     *     HIGH} 이상이면 true
     */
    public static boolean shouldRecommend(List<RiskLevel> recentNewestFirst, int afterDays) {
        Objects.requireNonNull(recentNewestFirst, "recentNewestFirst");
        if (afterDays < 1 || recentNewestFirst.size() < afterDays) {
            return false;
        }
        for (int index = 0; index < afterDays; index++) {
            if (recentNewestFirst.get(index).compareTo(RiskLevel.HIGH) < 0) {
                return false;
            }
        }
        return true;
    }
}
