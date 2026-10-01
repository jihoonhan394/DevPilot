package com.devpilot.today.domain;

import java.util.List;

/**
 * 과제 카드의 시작 전·끝내기 전 확인 목록 (docs/19 §3.11, docs/05 §8.1).
 *
 * <p><b>저장하지 않는다.</b> 과제 카드를 만들 때 콘텐츠 최신본을 읽어 붙인다 — 체크 여부도 남기지 않는다. 스스로 대조하는 목록이지 제출물이 아니다.
 *
 * <p>{@code after}가 <b>"언제 끝인가"</b>에 답한다(ADR-048). 만들기 과제에 그 답이 없으면 다 만들었는지 알 수 없다.
 *
 * @param taskTypes 이 목록이 붙는 과제 유형 (1~8개)
 * @param skillCodes 이 목록이 붙는 skill (1~4개)
 * @param before 시작 전 확인 (3~5개, 질문형)
 * @param after 끝내기 전 확인 (3~5개, 질문형)
 */
public record Checklist(
        String key,
        List<TaskType> taskTypes,
        List<String> skillCodes,
        List<String> before,
        List<String> after) {

    public Checklist {
        taskTypes = List.copyOf(taskTypes);
        skillCodes = List.copyOf(skillCodes);
        before = List.copyOf(before);
        after = List.copyOf(after);
    }

    /** 이 과제에 붙는가 (docs/19 §3.11 붙이는 규칙). */
    public boolean matches(TaskType taskType, String skillCode) {
        return taskTypes.contains(taskType) && skillCodes.contains(skillCode);
    }
}
