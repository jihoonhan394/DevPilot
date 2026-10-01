package com.devpilot.review.presentation;

import com.devpilot.review.application.ReviewItemService.CreateCommand;
import com.devpilot.review.domain.ReviewType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 수동 카드 생성 (docs/05 §11.5). rubric은 최소 1개다 — 복습 화면의 힌트가 첫 항목을 공개한다. */
public record ReviewItemCreateRequest(
        @NotBlank @Size(max = 100) String skillCode,
        @NotNull @Pattern(regexp = "^[A-Z0-9_.:-]{3,150}$") String conceptKey,
        @NotNull ReviewType reviewType,
        @NotBlank @Size(max = 2000) String prompt,
        @NotBlank @Size(max = 3000) String expectedAnswer,
        @NotNull @Size(min = 1, max = 6) List<@NotBlank @Size(max = 500) String> rubric) {

    /** 서비스 입력으로 바꾼다. */
    public CreateCommand toCommand() {
        return new CreateCommand(skillCode, conceptKey, reviewType, prompt, expectedAnswer, rubric);
    }
}
