package com.devpilot.review.application;

import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.common.time.UserTimeSettingsProvider;
import com.devpilot.common.time.UserTimeSettingsProvider.UserTimeSettings;
import com.devpilot.review.domain.ReviewItem;
import com.devpilot.review.domain.ReviewItemSourceType;
import com.devpilot.review.domain.RubricItem;
import com.devpilot.review.infrastructure.ReviewItemRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI가 짚은 빈틈으로 복습 카드를 만든다 (docs/06 §6.3, docs/05 §9.8). 러버덕 정리의 {@code gaps[]}가 첫 사용처이고 coach
 * finding·challenge 실패도 같은 경로를 쓴다. 첫 due는 {@code planDayStart(today + 1)}이고, **같은 {@code
 * concept_key}의 카드가 이미 있으면 새로 만들지 않고 due를 당긴다**(I-06). 호출자 트랜잭션에 참여한다.
 */
@Service
public class ReviewItemService {

    private final ReviewItemRepository reviewItemRepository;
    private final UserTimeSettingsProvider userTimeSettingsProvider;
    private final Clock clock;

    public ReviewItemService(
            ReviewItemRepository reviewItemRepository,
            UserTimeSettingsProvider userTimeSettingsProvider,
            Clock clock) {
        this.reviewItemRepository = reviewItemRepository;
        this.userTimeSettingsProvider = userTimeSettingsProvider;
        this.clock = clock;
    }

    /** 카드 1장 upsert. 새로 만들었으면 {@code created = true}. */
    @Transactional
    public UpsertResult upsert(NewReviewItem command) {
        Instant now = clock.instant();
        UserTimeSettings time = userTimeSettingsProvider.timeSettings(command.userId());
        LocalDate today = PlanDayCalculator.planDate(now, time.zoneId(), time.dayStartHour());
        Instant dueAt =
                PlanDayCalculator.planDayStart(
                        today.plusDays(1), time.zoneId(), time.dayStartHour());
        Optional<ReviewItem> existing =
                reviewItemRepository.findByUserIdAndConceptKey(
                        command.userId(), command.conceptKey());
        if (existing.isPresent()) {
            existing.get().pullDueForward(dueAt);
            return new UpsertResult(existing.get().getId(), false);
        }
        ReviewItem created =
                reviewItemRepository.save(
                        ReviewItem.fromGap(
                                new ReviewItem.GapValues(
                                        command.userId(),
                                        command.skillId(),
                                        command.sourceType(),
                                        command.sourceId(),
                                        command.conceptKey(),
                                        command.prompt(),
                                        command.expectedAnswer(),
                                        command.rubric()),
                                dueAt,
                                now));
        return new UpsertResult(created.getId(), true);
    }

    /**
     * 카드 입력 (docs/05 §9.8).
     *
     * @param expectedAnswer 답이 아니라 답이 다뤄야 할 것 (러버덕은 답을 만들지 않는다, NA-4)
     */
    public record NewReviewItem(
            UUID userId,
            UUID skillId,
            ReviewItemSourceType sourceType,
            @Nullable UUID sourceId,
            String conceptKey,
            String prompt,
            String expectedAnswer,
            List<RubricItem> rubric) {

        public NewReviewItem {
            rubric = List.copyOf(rubric);
        }
    }

    /**
     * upsert 결과.
     *
     * @param created false면 기존 카드의 due를 당겼다 ({@code createdReviewItemCount}에 세지 않는다)
     */
    public record UpsertResult(UUID reviewItemId, boolean created) {}
}
