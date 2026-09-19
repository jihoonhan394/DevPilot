package com.devpilot.review.application;

import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.common.time.UserTimeSettingsProvider;
import com.devpilot.common.time.UserTimeSettingsProvider.UserTimeSettings;
import com.devpilot.plan.application.PlanQueryService;
import com.devpilot.review.domain.ReviewItem;
import com.devpilot.review.domain.ReviewItemSourceType;
import com.devpilot.review.domain.SeedCard;
import com.devpilot.review.infrastructure.ReviewItemRepository;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.application.SkillTargetView;
import com.devpilot.skill.domain.Priority;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * seed 복습 카드 배정 (docs/04 §9, docs/06 §6.3, BL-MEM-08). 카드는 {@link SeedCardRegistry}에 있고 사용자별 {@code
 * review_item}으로 복사한다({@code source_type = SEED_CARD}, {@code origin = SEED}). 첫 due는 정렬(priority
 * MUST → SHOULD → LATER → 목표 없음, practicalImportance DESC, conceptKey ASC) 후 하루 5장씩 나눈다. 이미 있는
 * concept key는 건너뛴다(I-06). 비활성 skill의 카드는 복사하지 않는다.
 */
@Service
public class SeedCardAssignmentService {

    /** 하루에 첫 due가 몰리는 카드 수 (docs/06 §6.3). */
    static final int CARDS_PER_DAY = 5;

    private final SeedCardRegistry seedCardRegistry;
    private final ReviewItemRepository reviewItemRepository;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final PlanQueryService planQueryService;
    private final UserTimeSettingsProvider userTimeSettingsProvider;
    private final Clock clock;

    public SeedCardAssignmentService(
            SeedCardRegistry seedCardRegistry,
            ReviewItemRepository reviewItemRepository,
            SkillCatalogQueryService skillCatalogQueryService,
            PlanQueryService planQueryService,
            UserTimeSettingsProvider userTimeSettingsProvider,
            Clock clock) {
        this.seedCardRegistry = seedCardRegistry;
        this.reviewItemRepository = reviewItemRepository;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.planQueryService = planQueryService;
        this.userTimeSettingsProvider = userTimeSettingsProvider;
        this.clock = clock;
    }

    /**
     * 온보딩 시 복사 (docs/05 §4.1 8단계). 호출자 트랜잭션에 참여한다. 온보딩이 만든 plan의 목표로 정렬하므로 plan 생성 뒤에 부른다.
     *
     * @param today 온보딩 요청의 timezone·dayStartHour로 계산한 plan-day
     * @return 복사한 카드 수 ({@code assignedSeedCardCount})
     */
    @Transactional
    public int assignForNewUser(UUID userId, LocalDate today, ZoneId zone, int dayStartHour) {
        return assign(userId, today, zone, dayStartHour);
    }

    /**
     * 기동 시 backfill (docs/04 §9 4단계): 활성 plan이 있는 모든 사용자에게 아직 없는 카드를 추가한다. 새 카드의 첫 due는 그 사용자의 마지막
     * seed 카드 due 다음 plan-day부터(없으면 오늘부터) 하루 5장씩 나눈다(docs/06 §6.3 "신규 seed 카드" 행).
     *
     * @return 추가한 카드 수 합계
     */
    @Transactional
    public int backfillAll() {
        Instant now = clock.instant();
        int total = 0;
        for (UUID userId : planQueryService.userIdsWithActivePlan()) {
            UserTimeSettings settings = userTimeSettingsProvider.timeSettings(userId);
            ZoneId zone = settings.zoneId();
            int dayStartHour = settings.dayStartHour();
            LocalDate today = PlanDayCalculator.planDate(now, zone, dayStartHour);
            Optional<Instant> latestDue =
                    reviewItemRepository.findLatestDueAt(userId, ReviewItemSourceType.SEED_CARD);
            LocalDate start =
                    latestDue
                            .map(
                                    due ->
                                            PlanDayCalculator.planDate(due, zone, dayStartHour)
                                                    .plusDays(1))
                            .filter(date -> date.isAfter(today))
                            .orElse(today);
            total += assign(userId, start, zone, dayStartHour);
        }
        return total;
    }

    private int assign(UUID userId, LocalDate startDate, ZoneId zone, int dayStartHour) {
        List<SeedCard> cards = seedCardRegistry.cards();
        if (cards.isEmpty()) {
            return 0;
        }
        Set<String> existing = new HashSet<>(reviewItemRepository.findConceptKeys(userId));
        Set<String> codes = new HashSet<>();
        cards.forEach(card -> codes.add(card.skillCode()));
        Map<String, SkillRef> skills = skillCatalogQueryService.findActiveByCodes(codes);
        Map<UUID, SkillTargetView> targets = planQueryService.activePlanTargets(userId);
        List<SeedCard> missing =
                cards.stream()
                        .filter(card -> !existing.contains(card.conceptKey()))
                        .filter(card -> skills.containsKey(card.skillCode()))
                        .sorted(order(skills, targets))
                        .toList();
        Instant now = clock.instant();
        List<ReviewItem> items = new ArrayList<>();
        for (int index = 0; index < missing.size(); index++) {
            SeedCard card = missing.get(index);
            Instant dueAt =
                    PlanDayCalculator.planDayStart(
                            startDate.plusDays(index / CARDS_PER_DAY), zone, dayStartHour);
            items.add(
                    ReviewItem.fromSeedCard(
                            userId, skills.get(card.skillCode()).id(), card, dueAt, now));
        }
        reviewItemRepository.saveAll(items);
        reviewItemRepository.flush();
        return items.size();
    }

    private static Comparator<SeedCard> order(
            Map<String, SkillRef> skills, Map<UUID, SkillTargetView> targets) {
        return Comparator.comparingInt(
                        (SeedCard card) -> priorityRank(target(card, skills, targets)))
                .thenComparing(
                        Comparator.comparingInt(
                                        (SeedCard card) ->
                                                importance(target(card, skills, targets)))
                                .reversed())
                .thenComparing(SeedCard::conceptKey);
    }

    private static @Nullable SkillTargetView target(
            SeedCard card, Map<String, SkillRef> skills, Map<UUID, SkillTargetView> targets) {
        return targets.get(skills.get(card.skillCode()).id());
    }

    private static int priorityRank(@Nullable SkillTargetView target) {
        return target == null ? Priority.values().length : target.priority().ordinal();
    }

    private static int importance(@Nullable SkillTargetView target) {
        return target == null ? 0 : target.practicalImportanceBp();
    }
}
