package com.devpilot.common.error;

/**
 * ProblemDetail {@code errors[]} 항목 (docs/05 §1.2.1).
 *
 * @param field JSON 경로(점·index 표기) 또는 query/path/header 이름
 * @param code Bean Validation annotation 단순 이름 또는 도메인 코드 (docs/05 §1.2.3)
 * @param message 사용자용 한국어 문장. 서비스가 비워 두면 {@code GlobalExceptionHandler}가 {@code validation.<code>}
 *     문구로 채운다
 */
public record ApiFieldError(String field, String code, String message) {

    /** 문구는 응답을 만들 때 {@code messages_ko.properties}에서 채운다. */
    public static ApiFieldError of(String field, String code) {
        return new ApiFieldError(field, code, "");
    }
}
