package com.devpilot.today.domain;

/**
 * main 과제를 고른 이유 (docs/04 §5.1, docs/06 §5.8). {@code learning_task.reason_codes}에 저장하고 문구는 응답을 만들
 * 때 {@link ReasonTemplates}로 채운다.
 */
public enum ReasonCode {
    MILESTONE_CORE,
    MILESTONE_NEXT,
    HIGH_PRACTICAL_IMPORTANCE,
    LARGE_SKILL_GAP,
    REVIEW_OVERDUE,
    RECENT_RECALL_FAILURE,
    PROJECT_FOCUS,
    READ_REAL_CODE,
    CONTINUE_YESTERDAY,
    DEADLINE_RISK_MUST,
    LOW_ENERGY_LIGHT_TASK,
    COMEBACK_EASY_START
}
