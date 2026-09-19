package com.devpilot.integration.ai.api.output;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** {@code REQUIREMENT_EXTRACT} 출력 (docs/17 §4.9). 준비 상태 분류는 서버가 한다. */
public record RequirementExtractOutput(
        @NotNull @Size(max = 40) List<@NotNull @Valid RequirementItemOutput> requirements) {}
