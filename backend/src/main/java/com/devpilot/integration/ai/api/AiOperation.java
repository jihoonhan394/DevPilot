package com.devpilot.integration.ai.api;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * AI operation (docs/04 §3, docs/17 §3). {@code ai_call_log.operation} CHECK와 같은 값이고, operation ↔
 * prompt id는 1:1 고정이다(docs/17 §9.1). mode·timeout·재시도 같은 호출 설정은 {@code
 * devpilot.ai.operations}(docs/03 §9)가 정한다.
 */
public enum AiOperation {
    COACH_REVIEW("coach.review"),
    COACH_RESPONSE_FEEDBACK("coach.response-feedback"),
    CHALLENGE_GENERATE("challenge.generate"),
    CHALLENGE_EVALUATE("challenge.evaluate"),
    HINT_GENERATE("hint.generate"),
    REVIEW_VARIANT("review.variant"),
    REVIEW_EVALUATE("review.evaluate"),
    EVIDENCE_DRAFT("evidence.draft"),
    REQUIREMENT_EXTRACT("requirement.extract"),
    RUBBER_DUCK("rubber.duck"),
    RUBBER_DUCK_SUMMARY("rubber.duck.summary");

    private final String promptId;

    AiOperation(String promptId) {
        this.promptId = promptId;
    }

    /** 고정 prompt id (예: {@code coach.review}). */
    public String promptId() {
        return promptId;
    }

    /** 설정 키({@code COACH_REVIEW}, {@code coach-review} 등 relaxed binding 결과)로 찾는다. 영문자·숫자만 비교한다. */
    public static Optional<AiOperation> fromSettingKey(String key) {
        String normalized = normalize(key);
        return Arrays.stream(values())
                .filter(op -> normalize(op.name()).equals(normalized))
                .findFirst();
    }

    private static String normalize(String key) {
        return key.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
