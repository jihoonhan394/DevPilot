package com.devpilot.common.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 클라이언트에 전달되는 오류의 공통 상위 타입 (docs/03 §3.1, docs/08 §3.10). 메시지는 영어 내부용이고 사용자 문구는 {@code
 * messages_ko.properties}의 {@code error.<CODE>}에서 온다. {@code args}는 그 문구의 {@code {name}} 자리표시자를
 * 채운다.
 */
public abstract class DevPilotException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final Map<String, Object> args;
    private final List<ApiFieldError> errors;

    protected DevPilotException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of(), List.of());
    }

    /** 원인 예외를 보존한다 (docs/08 §3.10: 원인 예외 유실 금지). */
    protected DevPilotException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.args = Map.of();
        this.errors = List.of();
    }

    protected DevPilotException(ErrorCode errorCode, String message, List<ApiFieldError> errors) {
        this(errorCode, message, Map.of(), errors);
    }

    protected DevPilotException(
            ErrorCode errorCode,
            String message,
            Map<String, Object> args,
            List<ApiFieldError> errors) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.args = Map.copyOf(new LinkedHashMap<>(args));
        this.errors = List.copyOf(errors);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** 사용자 문구 자리표시자 값. 대부분의 코드는 비어 있다. */
    public Map<String, Object> args() {
        return args;
    }

    public List<ApiFieldError> errors() {
        return errors;
    }
}
