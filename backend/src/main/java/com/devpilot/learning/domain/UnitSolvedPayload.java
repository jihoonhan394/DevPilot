package com.devpilot.learning.domain;

import org.jspecify.annotations.Nullable;

/**
 * {@code UNIT_SOLVED} payload (docs/04 §6, docs/05 §21.7). 개념 노트의 학습 단위를 한 바퀴 마쳤을 때 남긴다.
 *
 * <p>사용자가 쓴 답은 담지 않는다 — 서버로 보내지도 않는다(docs/05 §21.6). 서버가 채점하지 않으므로 맞았는지도 모른다. 남는 것은 어떤 도움을 썼는지와 모범
 * 답안과 견준 개수뿐이고, 그 둘은 복습 일정에만 쓴다(docs/01 원칙 4).
 *
 * @param helpLevel {@code today.domain.HelpLevel} 이름. 모듈 경계 때문에 문자열로 담는다(docs/03 §2.2)
 * @param selfChecksMet 모범 답안과 견준 개수. 견주지 않고 넘어갔으면 null
 */
public record UnitSolvedPayload(
        String lessonKey, String unitKey, String helpLevel, @Nullable Integer selfChecksMet) {}
