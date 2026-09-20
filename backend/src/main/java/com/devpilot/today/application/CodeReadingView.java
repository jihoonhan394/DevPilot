package com.devpilot.today.application;

import com.devpilot.today.domain.CuratedReading;
import java.util.List;

/**
 * {@link ReadingView}의 코드 읽기 부분 (docs/05 §19.7, {@code kind = CODE}). 코드 본문은 없다 — 경로와 줄 범위만 준다.
 *
 * @param path {@code repo.subPath} 기준 상대 경로
 * @param question 읽고 답할 질문 (러버덕 대상이 된다)
 * @param lookFor 볼 지점 목록
 */
public record CodeReadingView(
        CuratedRepoView repo,
        String path,
        int startLine,
        int endLine,
        String question,
        List<String> lookFor) {

    public CodeReadingView {
        lookFor = List.copyOf(lookFor);
    }

    static CodeReadingView of(CuratedReading reading) {
        return new CodeReadingView(
                CuratedRepoView.of(reading.repo()),
                reading.path(),
                reading.startLine(),
                reading.endLine(),
                reading.question(),
                reading.lookFor());
    }
}
