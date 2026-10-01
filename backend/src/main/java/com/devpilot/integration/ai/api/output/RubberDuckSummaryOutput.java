package com.devpilot.integration.ai.api.output;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * {@code RUBBER_DUCK_SUMMARY} 출력 (docs/17 §4.11). {@code gaps}는 복습 카드가 되고, 가드 전 {@code gaps}가 0개이고
 * 턴이 3 이상이면 설명 증거다(RD-5).
 */
public record RubberDuckSummaryOutput(
        @NotNull @Size(max = 3) @Valid List<RubberDuckGap> gaps,
        @NotNull @Size(max = 5) List<@NotBlank @Size(max = 200) String> confirmed,
        @NotBlank @Size(max = 600) String overallNote) {}
