package com.devpilot.common.error;

/**
 * 422 오류 (docs/05 §1.3): 저장 전 secret masking 차단({@code SECRET_DETECTED_BLOCKED}) 등. 상태 코드는 전달한
 * {@link ErrorCode}가 정한다.
 */
public class UnprocessableContentException extends DevPilotException {

    private static final long serialVersionUID = 1L;

    public UnprocessableContentException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
