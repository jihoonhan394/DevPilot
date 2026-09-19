package com.devpilot.testsupport;

import java.util.UUID;

/**
 * 테스트 사용자 (docs/09 §4.1 {@code TestUsers}). 매 호출마다 새 {@code sub}이라 같은 이메일로 여러 사용자를 만들 수 있다 — {@code
 * app_user}에는 이메일 컬럼이 없다. 테스트는 테이블을 비우지 않고 자기 사용자 범위만 확인한다(docs/09 §4.2).
 */
public record TestUser(UUID sub, String email) {

    /** allowlist 이메일 (test profile). */
    public static TestUser owner() {
        return new TestUser(UUID.randomUUID(), "owner@devpilot.test");
    }

    /** allowlist 이메일 (test profile). */
    public static TestUser invited() {
        return new TestUser(UUID.randomUUID(), "invited@devpilot.test");
    }

    /** allowlist 밖 이메일. */
    public static TestUser stranger() {
        return new TestUser(UUID.randomUUID(), "stranger@devpilot.test");
    }
}
