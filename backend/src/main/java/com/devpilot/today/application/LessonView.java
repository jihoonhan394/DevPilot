package com.devpilot.today.application;

import com.devpilot.today.domain.HelpLevel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 개념 노트 응답 (docs/05 §21.1). <b>정답을 담지 않는다</b> — 예측의 답, 빈칸의 답, 모범 답안, 확인 목록은 채점·제출 응답에만 들어간다. 그래서 이
 * 응답을 캐시해도 답이 새지 않는다.
 */
public record LessonView(
        String lessonKey,
        UUID skillId,
        String skillCode,
        String skillName,
        String title,
        String whyItMatters,
        String oneLine,
        List<LessonUnitView> units,
        List<String> commonMistakes,
        String inProject,
        List<LessonSourceView> sources,
        List<LessonSourceView> readMore,
        boolean retired) {

    /** 단위 하나. 문항은 보이는 부분만 담는다. */
    public record LessonUnitView(
            String unitKey,
            String title,
            int minutes,
            boolean core,
            String explain,
            LessonExampleView example,
            LessonQuestionView predict,
            LessonQuestionView complete,
            LessonProblemView problem,
            List<String> prerequisiteUnits,
            @Nullable UnitProgressView progress) {}

    public record LessonExampleView(
            String language, String code, @Nullable String output, @Nullable String note) {}

    /**
     * 문항의 보이는 부분.
     *
     * @param choices 고르는 문항이면 2~4개, 아니면 빈 목록
     * @param blanks 빈칸 수. 예측 문항은 0
     */
    public record LessonQuestionView(
            String question, @Nullable String code, List<String> choices, int blanks) {}

    /** 백지 문제의 보이는 부분. 모범 답안과 확인 목록은 제출해야 온다. */
    public record LessonProblemView(
            String prompt,
            List<String> deliverables,
            @Nullable String starterCode,
            List<String> hints) {}

    public record LessonSourceView(String title, String url, @Nullable String versionScope) {}

    /**
     * 그 사용자의 단위 진행. 기록이 없으면 null이다.
     *
     * @param selfChecksMet 모범 답안과 견준 개수. 견주지 않았으면 null
     */
    public record UnitProgressView(
            boolean solved,
            HelpLevel helpLevel,
            Instant solvedAt,
            @Nullable Integer selfChecksMet) {}
}
