package com.devpilot.today.domain;

import java.util.List;

/**
 * 빈칸 채우기 문항 (docs/19 §3.14). {@code code}의 빈칸 표시 {@value #BLANK} 개수와 {@code answers} 개수가
 * 같다(CV-128).
 *
 * @param answers 빈칸 순서대로. 안쪽 목록은 그 빈칸의 허용 표기다
 */
public record CompleteQuestion(
        String question, String code, List<List<String>> answers, String explanation) {

    /** 빈칸 표시. 콘텐츠의 코드에 이 글자가 그대로 들어 있다. */
    public static final String BLANK = "___";

    public CompleteQuestion {
        answers = answers.stream().map(List::copyOf).toList();
    }

    /** 빈칸 수. 콘텐츠 검증이 {@code answers.size()}와 같음을 보장한다. */
    public int blanks() {
        return answers.size();
    }
}
