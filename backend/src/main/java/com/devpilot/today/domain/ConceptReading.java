package com.devpilot.today.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * 개념 읽기 단위 ({@code content/concept-readings.yaml#conceptReadings[]}, docs/19 §3.13). {@code
 * READING} 과제가 읽을 공식 문서 페이지 1개다 — 코드가 아니다(코드 읽기는 {@link CuratedReading}). 서버는 {@code url}을 요청하지
 * 않는다(docs/07 §5.5). 은퇴한 단위({@code retired = true})도 조회는 되고 planner만 새로 제안하지 않는다(docs/19 §8.2).
 *
 * @param skillCodes 대상 skill code (1~4개)
 * @param estimatedMinutes {@code learning_task.estimated_minutes}에 그대로 들어간다 (docs/06 §5.3)
 * @param whyRead 이 skill에서 무엇을 할 수 있게 되는지 (40~400자)
 * @param checkPoints 읽고 스스로 답할 것 정확히 3개 (docs/06 §5.3 "핵심 3가지")
 * @param verifiedAt 사람이 이 링크를 열어 확인한 날
 */
public record ConceptReading(
        String key,
        String title,
        String url,
        String publisher,
        String versionScope,
        List<String> skillCodes,
        int estimatedMinutes,
        String whyRead,
        List<String> checkPoints,
        LocalDate verifiedAt,
        boolean retired) {

    public ConceptReading {
        skillCodes = List.copyOf(skillCodes);
        checkPoints = List.copyOf(checkPoints);
    }
}
