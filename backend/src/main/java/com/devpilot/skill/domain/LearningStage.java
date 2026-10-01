package com.devpilot.skill.domain;

/**
 * 기술별 학습 단계 6칸 (docs/04 §3, ADR-042). <b>만들기가 먼저다</b> — 이 선언 순서가 화면의 6칸 순서다(docs/02
 * SCR-SKILL-DETAIL).
 *
 * <p>저장하지 않는다. {@code GET /skills/{skillId}}의 {@code learningStages[]}를 만들 때 기존 기록(과제 완료, 러버덕 세션,
 * 복습 답변, 재현 과제)에서 파생 계산한다(docs/06 §5.11).
 */
public enum LearningStage {
    BUILD,
    READ_CONCEPT,
    READ_CODE,
    EXPLAIN,
    REVIEW,
    REDO
}
