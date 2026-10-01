package com.devpilot.today.domain;

import java.util.List;

/**
 * 코드 읽기 단위 ({@code content/curated-repos.yaml#readings[]}, docs/19 §3.8). 파일 1개·줄 범위 1개다(RC-2). 은퇴한
 * 단위({@code retired = true})도 조회는 되고 planner만 새로 제안하지 않는다(docs/19 §8.2).
 *
 * @param path {@code repo.subPath} 기준 상대 경로
 * @param skillCodes 대상 skill code (1~4개)
 */
public record CuratedReading(
        String key,
        CuratedRepo repo,
        String path,
        int startLine,
        int endLine,
        List<String> skillCodes,
        int estimatedMinutes,
        String question,
        List<String> lookFor,
        boolean retired) {

    public CuratedReading {
        skillCodes = List.copyOf(skillCodes);
        lookFor = List.copyOf(lookFor);
    }
}
