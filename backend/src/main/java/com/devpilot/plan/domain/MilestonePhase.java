package com.devpilot.plan.domain;

/**
 * plan template milestone 단계 (docs/19 §3.4). 저장하지 않고 날짜 배치(docs/19 §5.2)의 창을 고르는 데만 쓴다.
 * PREPARATION이 모두 CONSOLIDATION보다 앞에 온다.
 */
public enum MilestonePhase {
    PREPARATION,
    CONSOLIDATION
}
