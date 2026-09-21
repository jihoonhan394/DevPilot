package com.devpilot.today.domain;

import org.jspecify.annotations.Nullable;

/**
 * 학습 단위의 예제 (docs/19 §3.14). 돌아가는 가장 작은 코드다.
 *
 * @param output 실제로 확인한 실행 결과. 없을 수 있다
 * @param note 줄별 설명(마크다운). 없을 수 있다
 */
public record LessonExample(
        String language, String code, @Nullable String output, @Nullable String note) {}
