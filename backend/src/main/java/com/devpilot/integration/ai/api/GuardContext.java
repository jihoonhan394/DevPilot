package com.devpilot.integration.ai.api;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * 출력 가드가 쓰는 호출 측 정보 (docs/03 §3.3, docs/17 §5.1). {@code integration.ai}는 skill catalog를 조회하지 않으므로
 * 호출 모듈이 채운다.
 *
 * @param contentLineCount {@code COACH_REVIEW}만. 그 외 0
 * @param expectedRubricIds {@code CHALLENGE_EVALUATE}, {@code REVIEW_EVALUATE}. 그 외 빈 집합
 * @param requestedHintLevel {@code HINT_GENERATE}만. 그 외 null
 * @param knownSkillCodes 활성 skill code 전체 ({@code SkillCatalogQueryService})
 */
public record GuardContext(
        int contentLineCount,
        Set<String> expectedRubricIds,
        @Nullable String requestedHintLevel,
        Set<String> knownSkillCodes) {

    public GuardContext {
        expectedRubricIds = Set.copyOf(expectedRubricIds);
        knownSkillCodes = Set.copyOf(knownSkillCodes);
    }

    /** 가드가 skill code 목록만 필요한 operation. */
    public static GuardContext withSkillCodes(Set<String> knownSkillCodes) {
        return new GuardContext(0, Set.of(), null, knownSkillCodes);
    }

    /** 가드 입력이 필요 없는 operation. */
    public static GuardContext empty() {
        return new GuardContext(0, Set.of(), null, Set.of());
    }
}
