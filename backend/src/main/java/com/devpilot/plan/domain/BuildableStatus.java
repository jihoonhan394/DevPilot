package com.devpilot.plan.domain;

/** 한 단계를 지금 만들 수 있는가 (docs/06 §11.4, ADR-060). */
public enum BuildableStatus {

    /** 관문 skill이 모두 기록으로 목표에 닿았다 — 지금 만들 수 있다. */
    BUILDABLE,

    /** 아직 닿지 않은 것 중 가장 앞선 단계 — 지금 만들 차례다. 한 계획에 최대 하나다. */
    NEXT,

    /** 그 뒤의 단계. */
    NOT_YET
}
