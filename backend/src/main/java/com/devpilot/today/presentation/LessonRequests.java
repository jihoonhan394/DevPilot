package com.devpilot.today.presentation;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import java.util.List;

/**
 * 개념 노트 요청의 도메인 검사 (docs/05 §21.5·§21.7). 개수는 콘텐츠에 달려 있어 Bean Validation으로 미리 고정할 수 없다 — 그 단위를 찾은
 * 뒤에 검사한다.
 */
final class LessonRequests {

    private LessonRequests() {}

    /** 빈칸 수와 답 개수가 다르다 (docs/05 §21.5). */
    static BusinessValidationException answersSizeMismatch(int blanks) {
        return new BusinessValidationException(
                "answers size must match the number of blanks: " + blanks,
                List.of(ApiFieldError.of("answers", "Size")));
    }

    /** 확인 목록보다 많이 체크했다 (docs/05 §21.7). */
    static BusinessValidationException selfChecksTooMany(int total) {
        return new BusinessValidationException(
                "selfChecksMet must not exceed " + total,
                List.of(ApiFieldError.of("selfChecksMet", "Max")));
    }
}
