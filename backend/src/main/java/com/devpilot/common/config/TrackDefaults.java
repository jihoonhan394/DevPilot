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
 * @param lessonMaxKnowledge 개념 노트를 먼저 내는 planning KNOWLEDGE 상한 (docs/06 §5.13 TH-5, ADR-057). 이 값
 *     <b>미만</b>이면 남은 단위를 다 뗄 때까지 CHALLENGE·READ_CODE보다 앞선다. 4면 자기평가만으로는 건너뛰지 못한다 — 자기평가 상한이
 *     3이라(docs/06 §7.5) evidence가 4에 닿은 skill만 지나간다
 * @param basicTipsFirst 오늘의 팁 정렬에서 {@code BASIC}을 먼저 두는지 (docs/06 §5.12)
 */
public record TrackDefaults(
        @Min(1) @Max(5) int maxTaskDifficulty,
        @Min(0) @Max(5) int readCodeMinKnowledge,
        @Min(0) @Max(5) int challengeMinKnowledge,
        @Min(0) @Max(5) int lessonMaxKnowledge,
        boolean basicTipsFirst) {}
