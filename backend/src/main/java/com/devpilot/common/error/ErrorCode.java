package com.devpilot.common.error;

import java.util.Locale;

/**
 * API 오류 코드 카탈로그. docs/05 §1.3 표와 1:1이다. 사용자 문구는 {@code messages_ko.properties}의 {@code
 * error.<CODE>}에 있다. HTTP status는 Spring 타입 없이 int로 가진다 (docs/08 §3.10).
 */
public enum ErrorCode {
    VALIDATION_FAILED(400, "Validation failed"),
    UNKNOWN_ENUM_VALUE(400, "Unknown enum value"),
    MALFORMED_REQUEST(400, "Malformed request"),
    IDEMPOTENCY_KEY_REQUIRED(400, "Idempotency key required"),
    INVALID_CURSOR(400, "Invalid cursor"),
    AUTHENTICATION_REQUIRED(401, "Authentication required"),
    USER_NOT_ALLOWED(403, "User not allowed"),
    FORBIDDEN(403, "Forbidden"),
    RECENT_LOGIN_REQUIRED(403, "Recent login required"),
    RESOURCE_NOT_FOUND(404, "Resource not found"),
    LEARNING_GOAL_NOT_FOUND(404, "Learning goal not found"),
    PLAN_NOT_FOUND(404, "Plan not found"),
    TODAY_NOT_GENERATED(404, "Today not generated"),
    ONBOARDING_REQUIRED(409, "Onboarding required"),
    ONBOARDING_ALREADY_COMPLETED(409, "Onboarding already completed"),
    ACTIVE_PLAN_EXISTS(409, "Active plan exists"),
    PLAN_NOT_ACTIVE(409, "Plan not active"),
    TODAY_ALREADY_STARTED(409, "Today already started"),
    TODAY_ALREADY_COMPLETED(409, "Today already completed"),
    SELF_EXPLANATION_REQUIRED(409, "Self-explanation required"),
    HINT_CONFIRMATION_REQUIRED(409, "Hint confirmation required"),
    FULL_EXAMPLE_NOT_ALLOWED(409, "Full example not allowed"),
    SUBMISSION_LIMIT_REACHED(409, "Submission limit reached"),
    EVALUATION_IN_PROGRESS(409, "Evaluation in progress"),
    AI_TASK_NOT_RETRYABLE(409, "AI task not retryable"),
    REVIEW_ALREADY_CLOSED(409, "Review already closed"),
    AI_ASSIST_LOCKED_FOR_REDO(409, "AI assist locked for redo"),
    INVALID_STATE_TRANSITION(409, "Invalid state transition"),
    CONCURRENT_MODIFICATION(409, "Concurrent modification"),
    IDEMPOTENCY_IN_PROGRESS(409, "Idempotency in progress"),
    CONTENT_TOO_LARGE(413, "Content too large"),
    REQUEST_TOO_LARGE(413, "Request too large"),
    IDEMPOTENCY_KEY_REUSED(422, "Idempotency key reused"),
    SECRET_DETECTED_BLOCKED(422, "Secret detected"),
    AI_DAILY_LIMIT_EXCEEDED(429, "AI daily limit exceeded"),
    AI_MONTHLY_BUDGET_EXCEEDED(429, "AI monthly budget exceeded"),
    AI_CONCURRENCY_LIMIT(429, "AI concurrency limit"),
    RATE_LIMITED(429, "Rate limited"),
    AI_OUTPUT_INVALID(502, "AI output invalid"),
    AI_REFUSED(502, "AI refused"),
    AI_UNAVAILABLE(503, "AI unavailable"),
    AI_TIMEOUT(504, "AI timeout"),
    INTERNAL_ERROR(500, "Internal error");

    private static final String TYPE_PREFIX = "urn:devpilot:problem:";

    private final int status;
    private final String title;

    ErrorCode(int status, String title) {
        this.status = status;
        this.title = title;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** {@code messages_ko.properties} 키. */
    public String messageKey() {
        return "error." + name();
    }

    /** RFC 9457 {@code type}: {@code urn:devpilot:problem:} + 소문자 kebab-case (docs/05 §1.2.1). */
    public String type() {
        return TYPE_PREFIX + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
