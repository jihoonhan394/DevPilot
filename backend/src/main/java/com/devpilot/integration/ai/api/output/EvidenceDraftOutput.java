package com.devpilot.integration.ai.api.output;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** {@code EVIDENCE_DRAFT} 출력 (docs/17 §4.8). */
public record EvidenceDraftOutput(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 3000) String problem,
        @NotBlank @Size(max = 3000) String analysis,
        @NotBlank @Size(max = 3000) String action,
        @NotBlank @Size(max = 3000) String result,
        @NotNull @Size(max = 8) List<@NotBlank @Size(max = 200) String> explanationTopics) {}
