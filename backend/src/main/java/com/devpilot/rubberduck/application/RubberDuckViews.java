package com.devpilot.rubberduck.application;

import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.common.web.AiMeta;
import com.devpilot.rubberduck.domain.RubberDuckStatus;
import com.devpilot.rubberduck.domain.RubberDuckTargetType;
import com.devpilot.skill.application.SkillRef;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 러버덕 응답 DTO (docs/05 §9.5~§9.10). 한 파일에 모아 둔다 — 서로만 참조하는 작은 record들이다. */
public final class RubberDuckViews {

    private RubberDuckViews() {}

    /**
     * 턴 1개 (docs/05 §9.5).
     *
     * @param userText 마스킹본 (RD-6) — 원문은 저장하지 않는다
     * @param learnerStuck 서버가 판정한 "모르겠다" (docs/06 §9.5 RD-3, AI 출력이 아니다)
     */
    public record RubberDuckTurnView(
            int turnNo,
            String userText,
            @Nullable String question,
            boolean learnerStuck,
            Instant createdAt,
            @Nullable AiMeta aiMeta) {}

    /**
     * 정리가 짚은 빈틈 (docs/05 §9.5).
     *
     * @param reviewItemId 만든 카드 또는 due를 당긴 기존 카드. 카드를 만들지 못했으면 null
     */
    public record RubberDuckGapView(
            String conceptKey,
            String whatWasMissed,
            String whyItMatters,
            String reviewQuestion,
            @Nullable UUID reviewItemId) {}

    /** 세션 정리 (docs/05 §9.5). */
    public record RubberDuckSummaryView(
            List<RubberDuckGapView> gaps, List<String> confirmed, String overallNote) {

        public RubberDuckSummaryView {
            gaps = List.copyOf(gaps);
            confirmed = List.copyOf(confirmed);
        }
    }

    /**
     * 세션 (docs/05 §9.5).
     *
     * @param suggestHint RD-3: 마지막 {@code stuck-turns-before-hint}턴이 모두 "모르겠다"
     * @param targetTitle 대상을 알아볼 문구. 대상이 지워졌으면 null
     */
    public record RubberDuckSessionView(
            UUID id,
            RubberDuckTargetType targetType,
            @Nullable UUID targetId,
            @Nullable String conceptKey,
            @Nullable String readingKey,
            @Nullable String targetTitle,
            @Nullable SkillRef skill,
            RubberDuckStatus status,
            int turnCount,
            int maxTurns,
            boolean suggestHint,
            List<RubberDuckTurnView> turns,
            @Nullable RubberDuckSummaryView summary,
            @Nullable AsyncFailureCode summarySkippedReason,
            @Nullable UUID learningSessionId,
            Instant startedAt,
            @Nullable Instant completedAt,
            long version) {

        public RubberDuckSessionView {
            turns = List.copyOf(turns);
        }
    }

    /** {@code POST /rubber-duck} 응답 (docs/05 §9.6). */
    public record RubberDuckStartResponse(
            RubberDuckSessionView session, @Nullable UUID abandonedSessionId) {}

    /** {@code POST /rubber-duck/{sessionId}/turns} 응답 (docs/05 §9.7). */
    public record RubberDuckTurnResponse(
            int turnNo,
            String question,
            boolean suggestHint,
            int remainingTurns,
            AiMeta aiMeta,
            long version) {}

    /** {@code POST /rubber-duck/{sessionId}/complete} 응답 (docs/05 §9.8). */
    public record RubberDuckCompleteResponse(
            UUID sessionId,
            RubberDuckStatus status,
            List<RubberDuckGapView> gaps,
            List<String> confirmed,
            @Nullable String overallNote,
            int createdReviewItemCount,
            @Nullable AsyncFailureCode summarySkippedReason,
            @Nullable AiMeta aiMeta,
            long version) {

        public RubberDuckCompleteResponse {
            gaps = List.copyOf(gaps);
            confirmed = List.copyOf(confirmed);
        }
    }
}
