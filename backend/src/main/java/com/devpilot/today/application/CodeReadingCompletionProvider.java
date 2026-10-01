package com.devpilot.today.application;

import java.util.UUID;

/**
 * port (docs/03 §2.2): {@code READ_CODE} 과제의 완료 조건(RC-1, docs/06 §9.5)을 확인한다. {@code rubberduck}
 * 모듈이 구현한다 — today는 rubberduck에 의존하지 않는다.
 */
public interface CodeReadingCompletionProvider {

    /** 그 과제를 대상으로 한 {@code COMPLETED} 러버덕 세션이 1개 이상 있는가. */
    boolean hasCompletedRubberDuck(UUID userId, UUID learningTaskId);
}
