package com.devpilot.rubberduck.domain;

/** 러버덕 세션 상태 (docs/04 §3·§4). {@code IN_PROGRESS → COMPLETED | ABANDONED}, 그 뒤 전이는 없다. */
public enum RubberDuckStatus {
    IN_PROGRESS,
    COMPLETED,
    ABANDONED
}
