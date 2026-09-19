package com.devpilot.training.domain;

/**
 * challenge rubric 항목 ({@code challenge.rubric_json}, docs/04 §5.2). weight 합은 정확히 10000이다(I-10).
 *
 * @param id {@code R1..Rn}
 */
public record ChallengeRubricItem(String id, String criterion, int weightBp, RubricAxis axis) {}
