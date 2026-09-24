package com.devpilot.learning.application;

import java.util.UUID;

/**
 * port (docs/03 §2.2): 열려 있는 재현 과제가 그 대상의 AI 지원을 막는가 (docs/06 §5.10 RE-5, §9.1 HL-9). {@code today}
 * 모듈이 구현한다.
 *
 * <p>재현 과제는 "며칠 전에 AI와 같이 풀어 낸 것을 이번에는 혼자 만들어 보자"는 약속이다. 그 약속이 열려 있는 동안 같은 대상에 힌트나 러버덕이 열리면 확인 자체가
 * 성립하지 않는다. 그래서 막는 것은 <b>그 대상</b>뿐이고, 다른 문제·복습·기록은 그대로 열려 있다.
 */
public interface RedoLockProvider {

    /** 그 challenge를 원본으로 하는 재현 과제가 열려 있는가. */
    boolean challengeLocked(UUID userId, UUID challengeId);

    /** 그 사이드 프로젝트를 원본으로 하는 재현 과제가 열려 있는가. */
    boolean sideProjectLocked(UUID userId, UUID sideProjectId);
}
