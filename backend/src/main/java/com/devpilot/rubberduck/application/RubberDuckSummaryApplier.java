package com.devpilot.rubberduck.application;

import com.devpilot.common.domain.ContentOrigin;
import com.devpilot.integration.ai.api.output.RubberDuckGap;
import com.devpilot.integration.ai.api.output.RubberDuckSummaryOutput;
import com.devpilot.learning.application.LearningEventQueryService;
import com.devpilot.learning.application.LearningEventRecorder;
import com.devpilot.learning.application.LearningEventRecorder.NewLearningEvent;
import com.devpilot.learning.domain.EventSourceType;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.domain.RubberDuckCompletedPayload;
import com.devpilot.review.application.ReviewItemService;
import com.devpilot.review.application.ReviewItemService.NewReviewItem;
import com.devpilot.review.application.ReviewItemService.UpsertResult;
import com.devpilot.review.domain.ReviewItemSourceType;
import com.devpilot.review.domain.ReviewType;
import com.devpilot.review.domain.RubricItem;
import com.devpilot.rubberduck.domain.RubberDuckSession;
import com.devpilot.rubberduck.domain.RubberDuckSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 정리 결과 반영 (docs/05 §9.8 6단계): gap마다 복습 카드를 만들거나 기존 카드의 due를 당기고, 세션 skill이 있으면 {@code
 * RUBBER_DUCK_COMPLETED}를 남긴다(RD-7). 호출자(tx2) 트랜잭션에서 실행한다.
 */
@Component
class RubberDuckSummaryApplier {

    /** gap rubric은 1개다 (docs/05 §9.8). */
    private static final String RUBRIC_ID = "G1";

    private final ReviewItemService reviewItemService;
    private final LearningEventRecorder learningEventRecorder;
    private final LearningEventQueryService learningEventQueryService;
    private final RubberDuckTargetResolver targetResolver;

    RubberDuckSummaryApplier(
            ReviewItemService reviewItemService,
            LearningEventRecorder learningEventRecorder,
            LearningEventQueryService learningEventQueryService,
            RubberDuckTargetResolver targetResolver) {
        this.reviewItemService = reviewItemService;
        this.learningEventRecorder = learningEventRecorder;
        this.learningEventQueryService = learningEventQueryService;
        this.targetResolver = targetResolver;
    }

    /** 카드 생성·이벤트 기록. 저장할 {@code summary_json}과 새로 만든 카드 수를 돌려준다. */
    Applied apply(
            RubberDuckSession session,
            RubberDuckSummaryOutput output,
            int rawGapCount,
            String promptVersion,
            LocalDate planDate,
            Instant now) {
        List<RubberDuckSummary.Gap> gaps = new ArrayList<>();
        int created = 0;
        for (RubberDuckGap gap : output.gaps()) {
            Optional<UpsertResult> upsert = upsert(session, gap);
            created += upsert.filter(UpsertResult::created).isPresent() ? 1 : 0;
            gaps.add(
                    new RubberDuckSummary.Gap(
                            gap.conceptKey(),
                            gap.whatWasMissed(),
                            gap.whyItMatters(),
                            gap.reviewQuestion(),
                            upsert.map(UpsertResult::reviewItemId).orElse(null)));
        }
        recordEvent(session, rawGapCount, planDate, now);
        return new Applied(
                new RubberDuckSummary(
                        gaps, output.confirmed(), output.overallNote(), rawGapCount, promptVersion),
                created);
    }

    /** 카드를 만들 skill이 없으면 빈 값 — 그 gap은 카드 없이 남는다(docs/05 §9.8). */
    private Optional<UpsertResult> upsert(RubberDuckSession session, RubberDuckGap gap) {
        UUID skillId = session.getSkillId();
        if (skillId == null) {
            skillId = targetResolver.skillIdForConceptKey(gap.conceptKey()).orElse(null);
        }
        if (skillId == null) {
            return Optional.empty();
        }
        return Optional.of(
                reviewItemService.upsert(
                        new NewReviewItem(
                                session.getUserId(),
                                skillId,
                                ContentOrigin.AI_GENERATED,
                                ReviewItemSourceType.RUBBER_DUCK,
                                session.getId(),
                                gap.conceptKey(),
                                ReviewType.EXPLAIN,
                                gap.reviewQuestion(),
                                expectedAnswer(gap),
                                List.of(new RubricItem(RUBRIC_ID, gap.whatWasMissed())))));
    }

    /** 답이 아니라 답이 다뤄야 할 것 (docs/05 §9.8, NA-4). */
    private static String expectedAnswer(RubberDuckGap gap) {
        return "- " + gap.whatWasMissed() + "\n- " + gap.whyItMatters();
    }

    /** RD-7: 세션 skill이 없으면 이벤트를 남기지 않는다. */
    private void recordEvent(
            RubberDuckSession session, int rawGapCount, LocalDate planDate, Instant now) {
        UUID skillId = session.getSkillId();
        if (skillId == null) {
            return;
        }
        learningEventRecorder.record(
                new NewLearningEvent(
                        session.getUserId(),
                        skillId,
                        session.getLearningSessionId(),
                        LearningEventType.RUBBER_DUCK_COMPLETED,
                        EventSourceType.RUBBER_DUCK_SESSION,
                        session.getId(),
                        planDate,
                        new RubberDuckCompletedPayload(
                                session.getId(),
                                session.getTurnCount(),
                                rawGapCount,
                                session.getTargetType().name(),
                                hintDisclosed(session)),
                        "RUBBER_DUCK:" + session.getId() + ":" + skillId,
                        now));
    }

    /** 세션 시작 이후 같은 대상에 {@code HINT_DISCLOSED}가 있었는가 (docs/06 §7.2 독립 판정). */
    private boolean hintDisclosed(RubberDuckSession session) {
        UUID targetId = session.getTargetId();
        return targetId != null
                && learningEventQueryService.hasEventForSourceSince(
                        session.getUserId(),
                        LearningEventType.HINT_DISCLOSED,
                        targetId,
                        session.getStartedAt());
    }

    /** 반영 결과. */
    record Applied(RubberDuckSummary summary, int createdReviewItemCount) {}
}
