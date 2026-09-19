package com.devpilot.training.presentation;

import com.devpilot.learning.domain.CodeLanguage;
import com.devpilot.training.application.SubmissionService.SubmitCommand;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * 답안 제출 (docs/05 §10.9). {@code code}는 UTF-8 20,000 byte 이하이고 초과하면 413 {@code
 * CONTENT_TOO_LARGE}다(컨트롤러 검사).
 */
public record SubmissionRequest(
        @Size(max = 5000) @Nullable String answerText,
        @Nullable String code,
        @Nullable CodeLanguage language) {

    /** 서비스 입력으로 바꾼다. */
    public SubmitCommand toCommand() {
        return new SubmitCommand(answerText, code, language);
    }
}
