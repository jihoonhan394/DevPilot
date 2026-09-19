package com.devpilot.common.error;

/** {@link DevPilotException} — 상태 코드는 전달한 {@link ErrorCode}가 정한다 (docs/05 §1.3). */
public class ConflictException extends DevPilotException {

    private static final long serialVersionUID = 1L;

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ConflictException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
