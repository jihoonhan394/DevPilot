package com.devpilot.review.presentation;

import com.devpilot.review.application.ReviewItemService.PatchCommand;
import com.devpilot.review.domain.ReviewItemStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** 카드 상태·문항 수정 (docs/05 §11.6). rubric은 이 API로 바꾸지 않는다. 빈 문자열은 {@code NOT_BLANK_IF_PRESENT}다. */
public record ReviewItemPatchRequest(
        @Nullable ReviewItemStatus status,
        @Size(min = 1, max = 2000) @Nullable String prompt,
        @Size(min = 1, max = 3000) @Nullable String expectedAnswer,
        @NotNull Long version) {

    /** 서비스 입력으로 바꾼다. */
    public PatchCommand toCommand() {
        return new PatchCommand(status, prompt, expectedAnswer, version == null ? 0L : version);
    }
}
