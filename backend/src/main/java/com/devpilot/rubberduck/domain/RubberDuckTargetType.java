package com.devpilot.rubberduck.domain;

/**
 * 러버덕 세션의 대상 (docs/04 §3, docs/05 §9.5 표). {@code CONCEPT}이면 {@code target_id} 대신 {@code
 * concept_key}를 쓰고, {@code PROJECT_WORK}이면 {@code target_id}가 {@code side_project.id}다.
 */
public enum RubberDuckTargetType {
    CODE_READING,
    CHALLENGE,
    REVIEW_ITEM,
    CONCEPT,
    PROJECT_WORK
}
