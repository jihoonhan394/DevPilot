package com.devpilot.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 학습 트랙 기본값 (docs/03 §9 {@code devpilot.tracks.<트랙>}, docs/06 §5.3). {@link
 * DevPilotProperties#tracks()}의 값이고 키는 {@code TargetRole} 이름이다(docs/04 §3).
 *
 * <p>{@code common}은 {@code skill}·{@code goal}을 의존하지 않으므로(docs/03 §2.2) 여기서는 트랙 이름을 문자열로 받고, 값마다
 * 항목이 있는지는 {@code goal.application}의 기동 검사가 본다.
 *
 * @param maxTaskDifficulty main 과제 난이도 {@code d}의 상한 (docs/06 §5.3)
 * @param readCodeMinKnowledge {@code READ_CODE} 제안의 planning KNOWLEDGE 문턱 (docs/06 RC-3)
 * @param challengeMinKnowledge {@code CHALLENGE} 제안의 planning KNOWLEDGE 문턱 (docs/06 §5.3, ADR-045).
 *     개념을 한 번도 안 본 skill에는 문제를 내지 않는다
 * @param basicTipsFirst 오늘의 팁 정렬에서 {@code BASIC}을 먼저 두는지 (docs/06 §5.12)
 */
public record TrackDefaults(
        @Min(1) @Max(5) int maxTaskDifficulty,
        @Min(0) @Max(5) int readCodeMinKnowledge,
        @Min(0) @Max(5) int challengeMinKnowledge,
        boolean basicTipsFirst) {}
