package com.devpilot.today.application;

import com.devpilot.skill.application.SkillRef;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@code GET /readings/{readingKey}} 응답 (docs/05 §19.7). 코드 본문은 없다 — 경로와 줄 범위만 준다.
 *
 * @param path {@code repo.subPath} 기준 상대 경로
 * @param skills {@code skillCodes}를 활성 skill로 해석한 것. 없는 code는 뺀다
 * @param retired 은퇴한 단위(docs/19 §8.2). true여도 좌표는 유효하고 planner만 새로 제안하지 않는다
 */
public record CuratedReadingView(
        String key,
        CuratedRepoView repo,
        String path,
        int startLine,
        int endLine,
        List<SkillRef> skills,
        @Nullable Integer estimatedMinutes,
        String question,
        List<String> lookFor,
        boolean retired) {

    public CuratedReadingView {
        skills = List.copyOf(skills);
        lookFor = List.copyOf(lookFor);
    }
}
