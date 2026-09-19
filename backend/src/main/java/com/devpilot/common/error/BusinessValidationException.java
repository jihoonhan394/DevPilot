package com.devpilot.common.error;

import java.util.List;

/**
 * 400 오류. 기본은 도메인 검사 실패(400 {@code VALIDATION_FAILED} + 도메인 field code, docs/05 §1.2.3)이고, field
 * error가 없는 다른 400 코드(예: {@code INVALID_CURSOR})도 이 타입으로 던진다.
 */
public class BusinessValidationException extends DevPilotException {

    private static final long serialVersionUID = 1L;
    private static final int HTTP_BAD_REQUEST = 400;

    public BusinessValidationException(String message, List<ApiFieldError> errors) {
        super(ErrorCode.VALIDATION_FAILED, message, errors);
    }

    public BusinessValidationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public BusinessValidationException(ErrorCode errorCode, String message) {
        super(requireBadRequest(errorCode), message);
    }

    private static ErrorCode requireBadRequest(ErrorCode errorCode) {
        if (errorCode.status() != HTTP_BAD_REQUEST) {
            throw new IllegalArgumentException("not a 400 error code: " + errorCode);
        }
        return errorCode;
    }
}
