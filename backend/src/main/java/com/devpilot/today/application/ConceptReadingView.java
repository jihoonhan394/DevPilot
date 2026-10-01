package com.devpilot.today.application;

import com.devpilot.today.domain.ConceptReading;
import java.time.LocalDate;
import java.util.List;

/**
 * {@link ReadingView}의 개념 읽기 부분 (docs/05 §19.7, {@code kind = CONCEPT}). 문서 본문은 없다 — 제목과 링크만 준다.
 * 사용자가 {@code url}을 새 탭으로 연다(docs/02 SCR-TODAY).
 *
 * @param title 문서 제목 그대로. 카드의 "자료" 줄
 * @param whyRead 이 skill에서 무엇을 할 수 있게 되는지 (40~400자)
 * @param checkPoints 읽고 스스로 답할 것 정확히 3개 (docs/06 §5.3 "핵심 3가지")
 * @param verifiedAt 사람이 이 링크를 열어 확인한 날 (docs/19 §3.13)
 */
public record ConceptReadingView(
        String title,
        String url,
        String publisher,
        String versionScope,
        String whyRead,
        List<String> checkPoints,
        LocalDate verifiedAt) {

    public ConceptReadingView {
        checkPoints = List.copyOf(checkPoints);
    }

    static ConceptReadingView of(ConceptReading reading) {
        return new ConceptReadingView(
                reading.title(),
                reading.url(),
                reading.publisher(),
                reading.versionScope(),
                reading.whyRead(),
                reading.checkPoints(),
                reading.verifiedAt());
    }
}
