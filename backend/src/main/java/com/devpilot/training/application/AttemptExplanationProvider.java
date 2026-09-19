package com.devpilot.training.application;

import java.util.UUID;

/**
 * port: 이 attempt를 대상으로 한 러버덕 세션에 턴이 있는지 (docs/06 §9.5 "RD-3과 Hint Ladder의 연결", docs/05 §10.8 3단계).
 * training은 rubberduck에 의존할 수 없으므로 training이 정의하고 rubberduck이 구현한다(docs/03 §2.2 규칙 4).
 */
public interface AttemptExplanationProvider {

    /** 턴이 1개 이상이면 자기 설명을 한 것으로 본다. */
    boolean hasRubberDuckTurns(UUID userId, UUID attemptId);
}
