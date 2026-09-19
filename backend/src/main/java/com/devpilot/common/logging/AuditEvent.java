package com.devpilot.common.logging;

import java.util.Set;

/**
 * 감사 이벤트 카탈로그 (docs/07 §6.3, docs/05 §1.4.5). 각 값은 허용 필드 이름 집합을 가진다 — {@link AuditLogger}가 호출 시 필드
 * 이름이 정확히 같은지 확인한다. 필드를 바꾸려면 docs/07 §6.3을 먼저 고친다. {@code AI_BALANCE_LOW}·{@code
 * AI_BALANCE_RESTORED}(docs/03 §6)는 필드가 정해지는 S3(BL-AIP-17)에 추가한다.
 */
public enum AuditEvent {
    AUTH_DEVTOKEN_ISSUED(Set.of("emailRef")),
    AUTH_DEVTOKEN_REJECTED(Set.of("emailRef")),
    AUTH_USER_PROVISIONED(Set.of("userRef", "matchedBy")),
    AUTH_USER_REJECTED(Set.of("subjectRef", "existingUser", "reason")),
    ACCOUNT_DELETION_REQUESTED(Set.of("userRef", "requestedAt")),
    ACCOUNT_DELETION_COMPLETED(
            Set.of("userRef", "externalAuthId", "requestedAt", "completedAt", "allowlistRemoval")),
    DATA_EXPORTED(Set.of("userRef", "format", "bytes")),
    AI_BUDGET_WARNING(Set.of("month", "spentMicroUsd", "budgetMicroUsd", "ratioBp")),
    AI_BUDGET_BLOCKED(
            Set.of("userRef", "operation", "reason", "month", "spentMicroUsd", "budgetMicroUsd")),
    SECRET_BLOCKED(Set.of("userRef", "source", "type")),
    PLAN_REPLANNED(
            Set.of(
                    "userRef",
                    "fromPlanId",
                    "toPlanId",
                    "fromVersion",
                    "toVersion",
                    "deferredCount",
                    "reducedCount")),
    CALENDAR_TOKEN_ROTATED(Set.of("userRef", "replacedExisting")),
    JOB_FAILED(Set.of("job", "userRef", "errorType", "durationMs"));

    private final Set<String> fieldNames;

    AuditEvent(Set<String> fieldNames) {
        this.fieldNames = fieldNames;
    }

    /** docs/07 §6.3 표의 필드 이름. 공통 필드(event, traceId, timestamp)는 제외. */
    public Set<String> fieldNames() {
        return fieldNames;
    }

    /** {@code JOB_FAILED}만 WARN, 나머지는 INFO (docs/07 §6.3). */
    public boolean warnLevel() {
        return this == JOB_FAILED;
    }
}
