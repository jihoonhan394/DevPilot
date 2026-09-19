package com.devpilot.common.web;

import java.util.regex.Pattern;

/**
 * 로그·ProblemDetail {@code instance}에 쓰는 요청 경로에서 비밀 값을 가린다 (docs/03 §5.5, docs/07 §4.6). 캘린더 피드 경로의
 * 토큰은 {@code /api/v1/calendar/****.ics}로 바꾼다.
 */
public final class SensitivePathMasker {

    private static final Pattern CALENDAR_TOKEN =
            Pattern.compile("^(/api/v1/calendar/)[^/]*\\.ics$");

    private SensitivePathMasker() {}

    public static String mask(String path) {
        return CALENDAR_TOKEN.matcher(path).replaceFirst("$1****.ics");
    }
}
