package com.devpilot.integration.ai.api.output;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * {@code LESSON_REEXPLAIN} 출력 (docs/17 §4.12). 저장하지 않고 응답으로만 쓴다(ADR-047).
 *
 * @param analogy 비유 한 문단. 억지로 만들게 하지 않으므로 없을 수 있다
 */
public record LessonReexplainOutput(
        @NotBlank @Size(max = 1200) String explanation,
        @Nullable @Size(max = 400) String analogy) {}
