package com.devpilot.today.application;

import com.devpilot.today.domain.LessonStatus;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 노트 목록 (docs/05 §21.9). 앱을 열었을 때 "오늘 뭘 열지"가 한 화면에 보이게 하는 응답이다.
 *
 * <p>본문은 담지 않는다 — 제목과 한 줄 요약, 그리고 그 사용자의 진행뿐이다. 본문은 §21.2로 따로 받는다.
 */
public record LessonListView(List<LessonSummaryView> lessons) {

    /**
     * 목록의 한 줄.
     *
     * @param solvedUnitCount 그 사용자가 한 번이라도 마친 단위 수
     * @param minutes 단위 {@code minutes} 합
     * @param lastSolvedAt 마지막 {@code UNIT_SOLVED} 시각. 기록이 없으면 null
     */
    public record LessonSummaryView(
            String lessonKey,
            String skillId,
            String skillCode,
            String skillName,
            String title,
            String oneLine,
            int unitCount,
            int solvedUnitCount,
            int minutes,
            LessonStatus status,
            @Nullable Instant lastSolvedAt) {}
}
