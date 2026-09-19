package com.devpilot.common.error;

/**
 * 도메인 field error code (docs/05 §1.2.3). {@code BusinessValidationException}의 {@code errors[].code}
 * 값이다. Bean Validation code는 annotation 단순 이름을 그대로 쓴다.
 */
public final class FieldErrorCodes {

    public static final String TYPE_MISMATCH = "TYPE_MISMATCH";
    public static final String TIMEZONE_INVALID = "TIMEZONE_INVALID";
    public static final String SKILL_CODE_UNKNOWN = "SKILL_CODE_UNKNOWN";
    public static final String DATE_ORDER_INVALID = "DATE_ORDER_INVALID";
    public static final String DATE_OUT_OF_RANGE = "DATE_OUT_OF_RANGE";
    public static final String DUPLICATE_VALUE = "DUPLICATE_VALUE";
    public static final String ONE_OF_REQUIRED = "ONE_OF_REQUIRED";
    public static final String MUTUALLY_EXCLUSIVE = "MUTUALLY_EXCLUSIVE";
    public static final String VALUE_NOT_ALLOWED = "VALUE_NOT_ALLOWED";
    public static final String MILESTONE_NOT_IN_PLAN = "MILESTONE_NOT_IN_PLAN";
    public static final String REFERENCE_NOT_FOUND = "REFERENCE_NOT_FOUND";
    public static final String NOT_BLANK_IF_PRESENT = "NOT_BLANK_IF_PRESENT";
    public static final String SKILL_NOT_IN_PLAN = "SKILL_NOT_IN_PLAN";
    public static final String TARGET_NOT_REDUCED = "TARGET_NOT_REDUCED";
    public static final String TARGET_NOT_RAISED = "TARGET_NOT_RAISED";
    public static final String ACTUAL_MINUTES_EXCEEDS_ELAPSED = "ACTUAL_MINUTES_EXCEEDS_ELAPSED";
    public static final String DATE_RANGE_TOO_LONG = "DATE_RANGE_TOO_LONG";

    /** 코드를 보냈는데 언어가 없다 (docs/05 §10.9 1단계). */
    public static final String LANGUAGE_REQUIRED = "LANGUAGE_REQUIRED";

    /** Hibernate Validator {@code @URL}과 같은 code. URL 필드 도메인 검사(docs/07 §5.5)가 쓴다. */
    public static final String URL = "URL";

    /**
     * Bean Validation {@code @Pattern}과 같은 code. {@code Idempotency-Key} 형식 오류가 쓴다(docs/05 §1.7).
     */
    public static final String PATTERN = "Pattern";

    private FieldErrorCodes() {}
}
