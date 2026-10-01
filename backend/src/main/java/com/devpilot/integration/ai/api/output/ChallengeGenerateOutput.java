package com.devpilot.integration.ai.api.output;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** {@code CHALLENGE_GENERATE} 출력 (docs/17 §4.3). rubric weight 합 10000, id R1부터 연속(I-10). */
public record ChallengeGenerateOutput(
        @NotBlank @Size(max = 200) String title,
        @NotNull @Size(min = 1, max = 3) List<@NotBlank @Size(max = 100) String> targetSkillCodes,
        @NotNull @Min(1) @Max(5) Integer difficulty,
        @NotNull @Min(5) @Max(180) Integer estimatedMinutes,
        @NotBlank @Size(max = 3000) String scenario,
        @NotBlank @Size(max = 4000) String prompt,
        @NotNull @Size(max = 8) List<@NotBlank @Size(max = 300) String> constraints,
        @NotNull @Size(min = 2, max = 8) List<@NotBlank @Size(max = 100) String> expectedConcepts,
        @NotNull @Size(min = 2, max = 6) List<@NotNull @Valid WeightedRubricItemOutput> rubric,
        @NotNull @Size(max = 6) List<@NotBlank @Size(max = 300) String> commonMistakes,
        @NotNull @Size(max = 5) List<@NotBlank @Size(max = 100) String> transferTargets,
        @NotNull @Valid ChallengeHintsOutput hints) {

    /** rubric weight 합 10000 (I-10). */
    @JsonIgnore
    @AssertTrue(message = "rubric weightBp 합은 10000이어야 한다")
    public boolean isRubricWeightSumValid() {
        return rubric == null
                || rubric.stream()
                                .mapToLong(item -> item.weightBp() == null ? 0 : item.weightBp())
                                .sum()
                        == 10_000;
    }

    /** rubric id는 R1부터 연속. */
    @JsonIgnore
    @AssertTrue(message = "rubric id는 R1부터 순서대로 연속이어야 한다")
    public boolean isRubricIdSequenceValid() {
        if (rubric == null) {
            return true;
        }
        for (int i = 0; i < rubric.size(); i++) {
            if (!("R" + (i + 1)).equals(rubric.get(i).id())) {
                return false;
            }
        }
        return true;
    }
}
