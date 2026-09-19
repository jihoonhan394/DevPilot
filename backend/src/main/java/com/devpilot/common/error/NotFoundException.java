package com.devpilot.common.error;

/** {@link DevPilotException} — 상태 코드는 전달한 {@link ErrorCode}가 정한다 (docs/05 §1.3). */
public class NotFoundException extends DevPilotException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
