package com.devpilot.today.application;

import com.devpilot.skill.application.SkillRef;
import com.devpilot.today.domain.ReadingKind;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@code GET /readings/{readingKey}} 응답 (docs/05 §19.7). 코드 읽기와 개념 읽기를 한 endpoint가 돌려준다 — {@code
 * learning_task.reading_key} 한 칸에 둘 다 들어가기 때문이다. 어느 쪽인지는 {@link #kind()}가 알려 준다.
 *
 * <p>본문은 없다. {@code CODE}는 파일 경로와 줄 범위만, {@code CONCEPT}은 제목과 링크만 준다 — 서버는 어느 URL도 요청하지 않는다(docs/07
 * §5.5).
 *
 * @param skills {@code skillCodes}를 활성 skill로 해석한 것. 없는 code는 뺀다
 * @param retired 은퇴한 단위(docs/19 §8.2). true여도 조회는 된다
 * @param code {@code kind = CODE}일 때만. 그 밖에는 null
 * @param concept {@code kind = CONCEPT}일 때만. 그 밖에는 null
 */
public record ReadingView(
        String key,
        ReadingKind kind,
        List<SkillRef> skills,
        @Nullable Integer estimatedMinutes,
        boolean retired,
        @Nullable CodeReadingView code,
        @Nullable ConceptReadingView concept) {

    public ReadingView {
        skills = List.copyOf(skills);
    }
}
