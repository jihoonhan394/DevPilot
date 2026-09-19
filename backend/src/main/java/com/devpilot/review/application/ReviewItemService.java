package com.devpilot.review.application;

import com.devpilot.common.domain.ContentOrigin;
import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.common.time.UserTimeSettingsProvider;
import com.devpilot.common.time.UserTimeSettingsProvider.UserTimeSettings;
import com.devpilot.integration.ai.masking.SecretMasker;
import com.devpilot.review.domain.ReviewItem;
import com.devpilot.review.domain.ReviewItemSourceType;
import com.devpilot.review.domain.ReviewItemStatus;
import com.devpilot.review.domain.ReviewType;
import com.devpilot.review.domain.RubricItem;
import com.devpilot.review.infrastructure.ReviewItemRepository;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 복습 카드 생성·수정 (docs/05 §11.5·§11.6, BL-MEM-07)과 다른 모듈이 부르는 카드 upsert (docs/06 §6.3). 첫 due는 {@code
 * planDayStart(today + 1)}이고, **같은 {@code concept_key}의 카드가 이미 있으면 새로 만들지 않고 due를 당긴다**(I-06).
 */
@Service
public class ReviewItemService {

    private static final String MASKING_SOURCE = "REVIEW_ITEM";

    private final ReviewItemRepository reviewItemRepository;
    private final UserTimeSettingsProvider userTimeSettingsProvider;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final SecretMasker secretMasker;
    private final Clock clock;

    public ReviewItemService(
            ReviewItemRepository reviewItemRepository,
            UserTimeSettingsProvider userTimeSettingsProvider,
            SkillCatalogQueryService skillCatalogQueryService,
            SecretMasker secretMasker,
            Clock clock) {
        this.reviewItemRepository = reviewItemRepository;
        this.userTimeSettingsProvider = userTimeSettingsProvider;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.secretMasker = secretMasker;
        this.clock = clock;
    }

    /** 카드 1장 upsert. 새로 만들었으면 {@code created = true}. 호출자 트랜잭션에 참여한다. */
    @Transactional
    public UpsertResult upsert(NewReviewItem command) {
        Instant now = clock.instant();
        Instant dueAt = nextPlanDayStart(command.userId(), now);
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
                                        command.origin(),
                                        command.sourceType(),
                                        command.sourceId(),
                                        command.conceptKey(),
                                        command.reviewType(),
                                        command.prompt(),
                                        command.expectedAnswer(),
                                        command.rubric()),
                                dueAt,
                                now));
        return new UpsertResult(created.getId(), true);
    }

    /** {@code POST /review-items} (docs/05 §11.5). 같은 concept key가 있으면 200으로 기존 카드를 돌려준다. */
    @Transactional
    public CreateResult create(CurrentUser user, CreateCommand command) {
        SkillRef skill =
                skillCatalogQueryService
                        .findActiveByCodes(List.of(command.skillCode()))
                        .get(command.skillCode());
        if (skill == null) {
            throw new BusinessValidationException(
                    "unknown skill code",
                    List.of(ApiFieldError.of("skillCode", FieldErrorCodes.SKILL_CODE_UNKNOWN)));
        }
        UUID userId = user.userId();
        String prompt = secretMasker.maskOrReject(userId, MASKING_SOURCE, command.prompt());
        String expectedAnswer =
                secretMasker.maskOrReject(userId, MASKING_SOURCE, command.expectedAnswer());
        List<RubricItem> rubric = new ArrayList<>();
        for (int index = 0; index < command.rubric().size(); index++) {
            rubric.add(
                    new RubricItem(
                            "R" + (index + 1),
                            secretMasker.maskOrReject(
                                    userId, MASKING_SOURCE, command.rubric().get(index))));
        }
        UpsertResult result =
                upsert(
                        new NewReviewItem(
                                userId,
                                skill.id(),
                                ContentOrigin.MANUAL,
                                ReviewItemSourceType.MANUAL,
                                null,
                                command.conceptKey(),
                                command.reviewType(),
                                prompt,
                                expectedAnswer,
                                rubric));
        reviewItemRepository.flush();
        return new CreateResult(result.created(), require(userId, result.reviewItemId()));
    }

    /** {@code PATCH /review-items/{reviewItemId}} (docs/05 §11.6). */
    @Transactional
    public ReviewItem patch(CurrentUser user, UUID reviewItemId, PatchCommand command) {
        UUID userId = user.userId();
        ReviewItem item = require(userId, reviewItemId);
        if (item.getVersion() != command.version()) {
            throw new ConflictException(ErrorCode.CONCURRENT_MODIFICATION, "review item changed");
        }
        String prompt = secretMasker.maskOrRejectNullable(userId, MASKING_SOURCE, command.prompt());
        String expectedAnswer =
                secretMasker.maskOrRejectNullable(userId, MASKING_SOURCE, command.expectedAnswer());
        item.editQuestion(prompt, expectedAnswer);
        if (command.status() != null) {
            item.changeStatus(command.status(), nextPlanDayStart(userId, clock.instant()));
        }
        reviewItemRepository.flush();
        return item;
    }

    /** 본인 카드. 없으면 404 (I-15). */
    @Transactional(readOnly = true)
    public ReviewItem require(UUID userId, UUID reviewItemId) {
        return reviewItemRepository
                .findByIdAndUserId(reviewItemId, userId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.RESOURCE_NOT_FOUND, "review item not found"));
    }

    private Instant nextPlanDayStart(UUID userId, Instant now) {
        UserTimeSettings time = userTimeSettingsProvider.timeSettings(userId);
        LocalDate today = PlanDayCalculator.planDate(now, time.zoneId(), time.dayStartHour());
        return PlanDayCalculator.planDayStart(
                today.plusDays(1), time.zoneId(), time.dayStartHour());
    }

    /**
     * 카드 입력 (docs/05 §9.8, docs/06 §8.3).
     *
     * @param expectedAnswer 답이 아니라 답이 다뤄야 할 것 (러버덕은 답을 만들지 않는다, NA-4)
     */
    public record NewReviewItem(
            UUID userId,
            UUID skillId,
            ContentOrigin origin,
            ReviewItemSourceType sourceType,
            @Nullable UUID sourceId,
            String conceptKey,
            ReviewType reviewType,
            String prompt,
            String expectedAnswer,
            List<RubricItem> rubric) {

        public NewReviewItem {
            rubric = List.copyOf(rubric);
        }
    }

    /** 수동 생성 입력 (docs/05 §11.5). */
    public record CreateCommand(
            String skillCode,
            String conceptKey,
            ReviewType reviewType,
            String prompt,
            String expectedAnswer,
            List<String> rubric) {

        public CreateCommand {
            rubric = List.copyOf(rubric);
        }
    }

    /** 수정 입력 (docs/05 §11.6). */
    public record PatchCommand(
            @Nullable ReviewItemStatus status,
            @Nullable String prompt,
            @Nullable String expectedAnswer,
            long version) {}

    /**
     * upsert 결과.
     *
     * @param created false면 기존 카드의 due를 당겼다 ({@code createdReviewItemCount}에 세지 않는다)
     */
    public record UpsertResult(UUID reviewItemId, boolean created) {}

    /**
     * 수동 생성 결과.
     *
     * @param created true면 201, false면 200 (docs/05 §11.5)
     */
    public record CreateResult(boolean created, ReviewItem item) {}
}
