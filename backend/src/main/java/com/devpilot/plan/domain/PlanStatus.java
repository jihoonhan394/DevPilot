package com.devpilot.plan.domain;

/** 계획 버전 상태 (docs/04 §3, §4.4). 사용자당 ACTIVE는 1개(I-02). */
public enum PlanStatus {
    ACTIVE,
    SUPERSEDED,
    ARCHIVED
}
