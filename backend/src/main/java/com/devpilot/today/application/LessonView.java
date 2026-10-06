package com.devpilot.today.application;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.domain.Priority;
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
        @Nullable LessonRequirementView requirement,
        boolean retired) {

    /**
     * 이 기술을 어디까지 알아야 하는가 (docs/05 §21.1·§21.2, ADR-069).
     *
     * <p>노트를 읽는 사람이 가장 먼저 묻는 것이 <b>"이걸 외워야 하나, 이 정도 알면 프로젝트를 만들어도 되나"</b>다. 계획은 그 답을 이미 들고
     * 있는데(우선순위와 축별 목표) 노트 화면에 없었다.
     *
     * @param priority 활성 plan의 우선순위. plan에 이 skill 목표가 없으면 null
     * @param targets 축별 목표 레벨
     * @param planningLevels 지금 수준 (docs/06 §7.5). 목표와 나란히 두면 남은 거리가 읽힌다
     */
    public record LessonRequirementView(
            @Nullable Priority priority, AxisLevels targets, AxisLevels planningLevels) {}

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
