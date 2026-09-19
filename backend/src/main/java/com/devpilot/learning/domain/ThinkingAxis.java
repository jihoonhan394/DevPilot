package com.devpilot.learning.domain;

/**
 * 코드 리뷰 사고 축 (docs/04 §3). coach finding category·thinking pattern observation 축이다(S4). Dashboard
 * 약한 축 목록(docs/05 §13.1 {@code weakThinkingAxes}, S5)의 타입으로도 쓴다.
 */
public enum ThinkingAxis {
    CORRECTNESS,
    NULL_BOUNDARY,
    RESOURCE_LIFECYCLE,
    EXCEPTION_STRATEGY,
    SECURITY,
    PERFORMANCE,
    CONCURRENCY,
    OBSERVABILITY,
    MAINTAINABILITY,
    TRANSACTION_DATA_CONSISTENCY
}
