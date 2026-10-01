package com.devpilot.integration.ai.api.output;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** {@code CHALLENGE_EVALUATE} 출력 (docs/17 §4.4). 점수·outcome은 서버가 계산한다. */
public record ChallengeEvaluateOutput(
        @NotNull @Size(min = 2, max = 6) List<@NotNull @Valid RubricJudgementOutput> rubric,
        @NotNull @Size(max = 5) List<@NotBlank @Size(max = 300) String> misconceptions,
        @Size(min = 1, max = 300) @Nullable String followUpQuestion) {}
