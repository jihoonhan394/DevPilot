package com.devpilot.rubberduck.domain;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code rubber_duck_session.summary_json} (docs/04 §5.9). 가드를 통과한 {@code RUBBER_DUCK_SUMMARY} 출력에
 * 서버가 gap별 {@code reviewItemId}와 {@code rawGapCount}(가드가 gap을 지우기 전의 수, RD-5)를 더한 것이다.
 */
public record RubberDuckSummary(
        List<Gap> gaps,
        List<String> confirmed,
        String overallNote,
        int rawGapCount,
        String promptVersion) {

    public RubberDuckSummary {
        gaps = List.copyOf(gaps);
        confirmed = List.copyOf(confirmed);
    }

    /**
     * 정리가 짚은 빈틈 1개.
     *
     * @param reviewItemId 만든 카드 또는 due를 당긴 기존 카드. 카드를 만들 수 없었으면 null (docs/05 §9.8)
     */
    public record Gap(
            String conceptKey,
            String whatWasMissed,
            String whyItMatters,
            String reviewQuestion,
            @Nullable UUID reviewItemId) {}
}
