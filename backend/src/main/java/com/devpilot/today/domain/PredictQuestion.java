package com.devpilot.today.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 출력 예측 문항 (docs/19 §3.14). 예제를 한 군데만 바꾼 코드의 결과를 맞힌다.
 *
 * @param code 바꾼 코드. 설명만으로 충분하면 없을 수 있다
 * @param choices 2~4개. 비어 있으면 한 줄 정확 일치다
 */
public record PredictQuestion(
        String question,
        @Nullable String code,
        List<String> choices,
        String answer,
        String explanation) {

    public PredictQuestion {
        choices = List.copyOf(choices);
    }

    /** 고르는 문항인가. 아니면 한 줄을 직접 쓴다. */
    public boolean multipleChoice() {
        return !choices.isEmpty();
    }
}
