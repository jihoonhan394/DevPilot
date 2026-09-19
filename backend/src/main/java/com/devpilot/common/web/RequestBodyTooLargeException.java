package com.devpilot.common.web;

import java.io.IOException;

/**
 * body를 읽는 도중 상한을 넘었다. 메시지 변환기가 {@code HttpMessageNotReadableException}으로 감싸므로
 * GlobalExceptionHandler가 원인 사슬에서 이 예외를 찾아 413 {@code REQUEST_TOO_LARGE}로 바꾼다.
 */
public class RequestBodyTooLargeException extends IOException {

    private static final long serialVersionUID = 1L;

    public RequestBodyTooLargeException(long maxBytes) {
        super("request body exceeded " + maxBytes + " bytes");
    }
}
