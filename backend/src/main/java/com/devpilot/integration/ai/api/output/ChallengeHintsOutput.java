package com.devpilot.integration.ai.api.output;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code CHALLENGE_GENERATE.hints} — 1~3단계 (docs/17 §4.3). */
public record ChallengeHintsOutput(
        @JsonProperty("QUESTION_ONLY") @NotBlank @Size(max = 1000) String questionOnly,
        @JsonProperty("CONCEPT_HINT") @NotBlank @Size(max = 1000) String conceptHint,
        @JsonProperty("DIRECTION") @NotBlank @Size(max = 1000) String direction) {}
