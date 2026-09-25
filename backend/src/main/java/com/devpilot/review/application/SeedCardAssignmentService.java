package com.devpilot.review.application;

import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.common.time.UserTimeSettingsProvider;
import com.devpilot.common.time.UserTimeSettingsProvider.UserTimeSettings;
import com.devpilot.learning.application.LearningEventQueryService;
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
import java.util.Collection;
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
 *
 * <p><b>배정 시점은 그 skill을 처음 배울 때다</b>(ADR-055). 온보딩에서 전부 복사하던 것을 그만뒀다 — 111장을 하루 5장씩 깔면 첫 23일의 복습이
 * <b>배운 적 없는 개념</b>으로 채워진다. 모르는 것이 매일 복습 칸에 있으면 그 칸을 안 보게 되고, 정작 배운 것의 복습까지 같이 묻힌다.
 */
@Service
public class SeedCardAssignmentService {

    /** 하루에 첫 due가 몰리는 카드 수 (docs/06 §6.3). */
    static final int CARDS_PER_DAY = 5;

    private final SeedCardRegistry seedCardRegistry;
    private final ReviewItemRepository reviewItemRepository;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final PlanQueryService planQueryService;
    private final LearningEventQueryService learningEventQueryService;
    private final UserTimeSettingsProvider userTimeSettingsProvider;
    private final Clock clock;

    public SeedCardAssignmentService(
            SeedCardRegistry seedCardRegistry,
            ReviewItemRepository reviewItemRepository,
            SkillCatalogQueryService skillCatalogQueryService,
            PlanQueryService planQueryService,
            LearningEventQueryService learningEventQueryService,
            UserTimeSettingsProvider userTimeSettingsProvider,
            Clock clock) {
        this.seedCardRegistry = seedCardRegistry;
        this.reviewItemRepository = reviewItemRepository;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.planQueryService = planQueryService;
        this.learningEventQueryService = learningEventQueryService;
        this.userTimeSettingsProvider = userTimeSettingsProvider;
        this.clock = clock;
    }

    /**
     * 그 skill을 처음 배울 때 그 skill의 카드만 복사한다 (ADR-055, docs/06 §6.3).
     *
     * <p>첫 due는 <b>그 사용자의 마지막 seed due 다음 날부터</b> 하루 5장씩이다. 오늘부터 깔면 여러 skill을 잇따라 시작한 주에 한 날짜에 카드가
     * 겹쳐 쌓인다.
     *
     * @return 복사한 카드 수. 이미 다 있으면 0
     */
    @Transactional
    public int assignForSkill(UUID userId, UUID skillId) {
        return assignForSkills(userId, Set.of(skillId));
    }

    /**
     * 여러 skill의 카드를 <b>한 번에</b> 배정한다. 하루 5장 분산이 skill 경계를 넘어 이어지도록 한 호출로 처리한다 — skill마다 따로 부르면 각
     * 호출이 앞 호출의 마지막 due 뒤에서 시작해 날짜가 불필요하게 벌어진다.
     */
    @Transactional
    public int assignForSkills(UUID userId, Collection<UUID> skillIds) {
        if (skillIds.isEmpty()) {
            return 0;
        }
        UserTimeSettings settings = userTimeSettingsProvider.timeSettings(userId);
        ZoneId zone = settings.zoneId();
        int dayStartHour = settings.dayStartHour();
        LocalDate today = PlanDayCalculator.planDate(clock.instant(), zone, dayStartHour);
        return assign(
                userId,
                nextSeedStart(userId, today, zone, dayStartHour),
                zone,
                dayStartHour,
                Set.copyOf(skillIds));
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
            Set<UUID> studied = new HashSet<>(reviewItemRepository.findSkillIdsWithItems(userId));
            studied.addAll(learningEventQueryService.skillIdsWithAnyEvent(userId));
            total +=
                    assign(
                            userId,
                            nextSeedStart(userId, today, zone, dayStartHour),
                            zone,
                            dayStartHour,
                            studied);
        }
        return total;
    }

    /** 마지막 seed due 다음 plan-day (없거나 지났으면 오늘). 새 카드를 이미 쌓인 것 뒤에 붙인다. */
    private LocalDate nextSeedStart(UUID userId, LocalDate today, ZoneId zone, int dayStartHour) {
        Optional<Instant> latestDue =
                reviewItemRepository.findLatestDueAt(userId, ReviewItemSourceType.SEED_CARD);
        return latestDue
                .map(due -> PlanDayCalculator.planDate(due, zone, dayStartHour).plusDays(1))
                .filter(date -> date.isAfter(today))
                .orElse(today);
    }

    private int assign(
            UUID userId, LocalDate startDate, ZoneId zone, int dayStartHour, Set<UUID> skillIds) {
        List<SeedCard> cards = seedCardRegistry.cards();
        if (cards.isEmpty() || skillIds.isEmpty()) {
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
                        .filter(card -> skillIds.contains(skills.get(card.skillCode()).id()))
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
