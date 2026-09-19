package com.devpilot.today.domain;

/**
 * planner modifier (docs/06 §5.5). 적용 순서는 1(risk) → 2(energy) → 3(이어하기·피로) → 4(복귀 모드)이고, 같은 순서 번호
 * 안에서는 하나만 적용된다. {@code score_breakdown.modifiers[].code}에 이 이름을 저장한다(docs/04 §5.1).
 */
public enum PlannerModifier {
    RISK_HIGH_MUST,
    RISK_HIGH_SHOULD,
    LOW_ENERGY_DEEP_TASK,
    HIGH_ENERGY_HARD_TASK,
    CONTINUATION,
    FATIGUE_TWO_DAYS,
    FATIGUE_ONE_DAY,
    COMEBACK_HARD_TASK
}
