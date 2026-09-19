package com.devpilot.review.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.common.web.CursorCodec;
import com.devpilot.common.web.CursorPage;
import com.devpilot.learning.application.LearningSessionQueryService;
import com.devpilot.plan.application.PlanQueryService;
import com.devpilot.review.application.DueReviewsView.DueReviewItemView;
import com.devpilot.review.application.DueReviewsView.ReviewRubricItemView;
import com.devpilot.review.domain.DueReviewSelector;
import com.devpilot.review.domain.DueReviewSelector.Candidate;
import com.devpilot.review.domain.DueReviewSelector.Selection;
import com.devpilot.review.domain.ReviewItem;
import com.devpilot.review.domain.ReviewItemStatus;
import com.devpilot.review.domain.ReviewRating;
import com.devpilot.review.infrastructure.ReviewAnswerRepository;
import com.devpilot.review.infrastructure.ReviewItemRepository;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillDetailView;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.application.SkillTargetView;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 복습 조회 (docs/05 §11.2, BL-MEM-05)와 다른 모듈(today·dashboard)에 공개하는 due 집계. due 대상·정렬·상한·교차 학습 재배치는
 * {@link DueReviewSelector}(docs/06 §6.5)다. 조회는 아무것도 저장하지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class ReviewQueryService {

    private final ReviewItemRepository reviewItemRepository;
    private final ReviewAnswerRepository reviewAnswerRepository;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final PlanQueryService planQueryService;
    private final LearningSessionQueryService learningSessionQueryService;
    private final CursorCodec cursorCodec;
    private final Clock clock;
    private final int maxPerDay;
    private final int comebackMaxPerDay;
    private final DueReviewSelector selector = new DueReviewSelector();

    public ReviewQueryService(
            ReviewItemRepository reviewItemRepository,
            ReviewAnswerRepository reviewAnswerRepository,
            SkillCatalogQueryService skillCatalogQueryService,
            PlanQueryService planQueryService,
            LearningSessionQueryService learningSessionQueryService,
            CursorCodec cursorCodec,
            Clock clock,
            DevPilotProperties properties) {
        this.reviewItemRepository = reviewItemRepository;
        this.reviewAnswerRepository = reviewAnswerRepository;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.planQueryService = planQueryService;
        this.learningSessionQueryService = learningSessionQueryService;
        this.cursorCodec = cursorCodec;
        this.clock = clock;
        this.maxPerDay = properties.review().maxPerDay();
        this.comebackMaxPerDay = properties.review().comebackMaxPerDay();
    }

    /**
     * {@code GET /reviews/due} (docs/05 §11.2). 반환 수 = {@code min(limit, cap)}, 생략하면 cap. 상한을 먼저
     * 적용하고 재배치한다(RV-INTERLEAVE-C).
     */
    public DueReviewsView due(UUID userId, ZoneId zone, int dayStartHour, @Nullable Integer limit) {
        LocalDate today = PlanDayCalculator.planDate(clock.instant(), zone, dayStartHour);
        boolean comebackMode = learningSessionQueryService.isComebackMode(userId, today);
        int cap = cap(comebackMode);
        int effectiveCap = limit == null ? cap : Math.min(limit, cap);
        Map<UUID, SkillDetailView> skills = skillCatalogQueryService.activeSkillDetails();
        Map<UUID, ReviewItem> items = new HashMap<>();
        Selection selection =
                selector.select(
                        candidates(userId, today, zone, dayStartHour, skills, items),
                        nextPlanDayStart(today, zone, dayStartHour),
                        effectiveCap);
        List<DueReviewItemView> views =
                selection.ordered().stream()
                        .map(
                                candidate ->
                                        toView(
                                                items.get(candidate.id()),
                                                candidate,
                                                skills,
                                                zone,
                                                dayStartHour))
                        .toList();
        return new DueReviewsView(today, cap, comebackMode, selection.totalDue(), views);
    }

    /**
     * planner·dashboard 입력: 오늘 due 수(상한 전)와 skill별 최대 연체 일수 (docs/06 §5.4 reviewUrgency, §5.6
     * dueCount).
     */
    public DueSummary dueSummary(UUID userId, LocalDate today, ZoneId zone, int dayStartHour) {
        Map<UUID, SkillDetailView> skills = skillCatalogQueryService.activeSkillDetails();
        List<Candidate> due =
                selector.select(
                                candidates(
                                        userId, today, zone, dayStartHour, skills, new HashMap<>()),
                                nextPlanDayStart(today, zone, dayStartHour),
                                Integer.MAX_VALUE)
                        .ordered();
        Map<UUID, Integer> maxOverdue = new HashMap<>();
        due.forEach(
                candidate ->
                        maxOverdue.merge(candidate.skillId(), candidate.overdueDays(), Math::max));
        return new DueSummary(due.size(), maxOverdue);
    }

    /** 러버덕 {@code REVIEW_ITEM} 대상 확인 (docs/05 §9.5 표). 타 사용자·없는 카드는 빈 값. */
    public Optional<ReviewItemRef> findItemRef(UUID userId, UUID reviewItemId) {
        return reviewItemRepository
                .findByIdAndUserId(reviewItemId, userId)
                .map(item -> new ReviewItemRef(item.getId(), item.getSkillId(), item.getPrompt()));
    }

    /** {@code GET /review-items} (docs/05 §11.4): {@code dueAt} ASC, {@code id} ASC. */
    public CursorPage<ReviewItemView> listItems(
            CurrentUser user,
            @Nullable UUID skillId,
            @Nullable ReviewItemStatus status,
            int limit,
            @Nullable String cursor) {
        UUID userId = user.userId();
        CursorCodec.Position<Instant> position = cursorCodec.decodeInstant(cursor);
        Limit fetch = Limit.of(limit + 1);
        List<ReviewItem> items =
                position == null
                        ? reviewItemRepository.findItemPage(userId, skillId, status, fetch)
                        : reviewItemRepository.findItemPageAfter(
                                userId, skillId, status, position.sortKey(), position.id(), fetch);
        boolean hasNext = items.size() > limit;
        List<ReviewItem> page = hasNext ? items.subList(0, limit) : items;
        String nextCursor = null;
        if (hasNext && !page.isEmpty()) {
            ReviewItem last = page.get(page.size() - 1);
            nextCursor = cursorCodec.encode(last.getDueAt(), last.getId());
        }
        return new CursorPage<>(
                page.stream().map(item -> view(item, user.zoneId(), user.dayStartHour())).toList(),
                nextCursor);
    }

    /** 카드 1장의 응답 view (docs/05 §11.1). */
    public ReviewItemView view(ReviewItem item, ZoneId zone, int dayStartHour) {
        SkillRef skill =
                skillCatalogQueryService
                        .findRefs(List.of(item.getSkillId()))
                        .get(item.getSkillId());
        return ReviewItemView.of(
                item,
                Objects.requireNonNull(skill, "review item skill"),
                PlanDayCalculator.planDate(item.getDueAt(), zone, dayStartHour));
    }

    /** 개념 키 → 카드 id·skill·due (docs/05 §10.6 {@code reviewScheduled}). 없는 키는 map에 없다. */
    public Map<String, ScheduledCardRef> findByConceptKeys(
            UUID userId, Collection<String> conceptKeys, ZoneId zone, int dayStartHour) {
        if (conceptKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, ScheduledCardRef> refs = new HashMap<>();
        for (ReviewItem item : reviewItemRepository.findByConceptKeys(userId, conceptKeys)) {
            refs.put(
                    item.getConceptKey(),
                    new ScheduledCardRef(
                            item.getId(),
                            item.getSkillId(),
                            PlanDayCalculator.planDate(item.getDueAt(), zone, dayStartHour)));
        }
        return Map.copyOf(refs);
    }

    /** 복습 상한 (docs/06 §5.6 첫 줄). */
    public int cap(boolean comebackMode) {
        return comebackMode ? comebackMaxPerDay : maxPerDay;
    }

    /**
     * {@code plan_date ≥ from}에 최종 등급 {@code AGAIN} 답변이 있는 skill (docs/06 §5.8 {@code
     * RECENT_RECALL_FAILURE}).
     */
    public Set<UUID> skillsWithRecentAgain(UUID userId, LocalDate from) {
        return Set.copyOf(
                reviewAnswerRepository.findSkillIdsWithFinalRatingSince(
                        userId, ReviewRating.AGAIN, from));
    }

    private List<Candidate> candidates(
            UUID userId,
            LocalDate today,
            ZoneId zone,
            int dayStartHour,
            Map<UUID, SkillDetailView> skills,
            Map<UUID, ReviewItem> byId) {
        Map<UUID, SkillTargetView> targets = planQueryService.activePlanTargets(userId);
        List<ReviewItem> active =
                reviewItemRepository.findActiveDueBefore(
                        userId, nextPlanDayStart(today, zone, dayStartHour));
        byId.putAll(
                active.stream().collect(Collectors.toMap(ReviewItem::getId, Function.identity())));
        return active.stream()
                .filter(item -> skills.containsKey(item.getSkillId()))
                .map(
                        item -> {
                            SkillTargetView target = targets.get(item.getSkillId());
                            LocalDate dueDate =
                                    PlanDayCalculator.planDate(item.getDueAt(), zone, dayStartHour);
                            return new Candidate(
                                    item.getId(),
                                    item.getSkillId(),
                                    item.getDueAt(),
                                    DueReviewSelector.overdueDays(dueDate, today),
                                    target == null ? null : target.priority(),
                                    item.getConsecutiveFailures());
                        })
                .toList();
    }

    private static DueReviewItemView toView(
            ReviewItem item,
            Candidate candidate,
            Map<UUID, SkillDetailView> skills,
            ZoneId zone,
            int dayStartHour) {
        SkillDetailView skill = skills.get(item.getSkillId());
        return new DueReviewItemView(
                item.getId(),
                skill.code(),
                skill.name(),
                item.getReviewType(),
                false,
                item.getPrompt(),
                item.getExpectedAnswer(),
                item.getRubric().stream()
                        .map(rubric -> new ReviewRubricItemView(rubric.id(), rubric.criterion()))
                        .toList(),
                PlanDayCalculator.planDate(item.getDueAt(), zone, dayStartHour),
                candidate.overdueDays());
    }

    static Instant nextPlanDayStart(LocalDate today, ZoneId zone, int dayStartHour) {
        return PlanDayCalculator.planDayStart(today.plusDays(1), zone, dayStartHour);
    }

    /** 러버덕 대상 요약 (docs/05 §9.5). */
    public record ReviewItemRef(UUID id, UUID skillId, String prompt) {}

    /** challenge 평가로 만들어진 카드 (docs/06 §8.3, docs/05 §10.6). */
    public record ScheduledCardRef(UUID reviewItemId, UUID skillId, LocalDate dueDate) {}

    /**
     * 오늘 due 집계.
     *
     * @param totalDue 상한 적용 전 due 수
     * @param maxOverdueDaysBySkill skill id → due 카드의 최대 연체 일수
     */
    public record DueSummary(int totalDue, Map<UUID, Integer> maxOverdueDaysBySkill) {

        public DueSummary {
            maxOverdueDaysBySkill = Map.copyOf(maxOverdueDaysBySkill);
        }
    }
}
