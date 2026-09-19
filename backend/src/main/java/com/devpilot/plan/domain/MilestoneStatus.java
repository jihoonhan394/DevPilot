package com.devpilot.plan.domain;

/** milestone 진행 상태 (docs/04 §3, §4.6). 모든 상태 사이 전이를 허용한다. */
public enum MilestoneStatus {
    PLANNED,
    IN_PROGRESS,
    DONE,
    DEFERRED,
    DROPPED
}
