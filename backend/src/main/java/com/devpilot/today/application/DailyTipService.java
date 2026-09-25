package com.devpilot.today.application;

import com.devpilot.common.domain.ContentOrigin;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.learning.application.LearningEventRecorder;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.domain.TipFeedback;
import com.devpilot.learning.domain.TipViewedPayload;
import com.devpilot.review.application.ReviewItemService;
import com.devpilot.review.application.ReviewItemService.NewReviewItem;
import com.devpilot.review.domain.ReviewItemSourceType;
import com.devpilot.review.domain.ReviewType;
import com.devpilot.review.domain.RubricItem;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.today.domain.DailyTip;
import com.devpilot.today.domain.DailyTipSelector;
import com.devpilot.today.domain.UserDailyTip;
import com.devpilot.today.infrastructure.UserDailyTipRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘의 팁 (docs/05 §20.2~§20.4a, docs/06 §5.12). AI를 부르지 않는다.
 *
 * <p>하루에 하나다. 이미 오늘 보여 준 것이 있으면 다시 고르지 않고 그대로 돌려준다(TIP-3) — 새로고침할 때마다 팁이 바뀌면 "오늘의 팁"이 아니게 된다.
 */
@Service
public class DailyTipService {

    /** 묶음 2가 보는 범위 (docs/06 §5.12): {@code today − 7} ~ {@code today − 1}. */
    private static final int RECENT_DAYS = 7;

    /** TIP-5 카드의 채점 기준. 증상이 아니라 <b>원인과 볼 곳</b>을 말할 수 있는지 본다. */
    private static final List<RubricItem> TIP_RUBRIC =
            List.of(
                    new RubricItem("R1", "증상이 생기는 원인을 짚는다"),
                    new RubricItem("R2", "어디를 보면 되는지 설명한다"));

    private final DailyTipRegistry dailyTipRegistry;
    private final UserDailyTipRepository userDailyTipRepository;
    private final TipSelectionInputs selectionInputs;
    private final LearningEventRecorder learningEventRecorder;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final ReviewItemService reviewItemService;
    private final Clock clock;

    DailyTipService(
            DailyTipRegistry dailyTipRegistry,
            UserDailyTipRepository userDailyTipRepository,
            TipSelectionInputs selectionInputs,
            LearningEventRecorder learningEventRecorder,
            SkillCatalogQueryService skillCatalogQueryService,
            ReviewItemService reviewItemService,
            Clock clock) {
        this.dailyTipRegistry = dailyTipRegistry;
        this.userDailyTipRepository = userDailyTipRepository;
        this.selectionInputs = selectionInputs;
        this.learningEventRecorder = learningEventRecorder;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.reviewItemService = reviewItemService;
        this.clock = clock;
    }

    /**
     * {@code GET /tips/today} (docs/05 §20.2). <b>조회이지만 표시 기록을 남기는 쓰기</b>다 — 그래야 하루 동안 같은 팁이 유지되고
     * 같은 팁을 두 번 제안하지 않는다(I-26).
     */
    @Transactional
    public DailyTipView today(CurrentUser user) {
        UUID userId = user.userId();
        LocalDate today =
                PlanDayCalculator.planDate(clock.instant(), user.zoneId(), user.dayStartHour());
        Optional<UserDailyTip> shown = userDailyTipRepository.findByUserIdAndShownOn(userId, today);
        if (shown.isPresent()) {
            return toView(requireTip(shown.get().getTipKey()), shown.get());
        }
        DailyTip chosen =
                new DailyTipSelector()
                        .select(selectionInputs.inputs(user, today, dailyTipRegistry.all()))
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "no tip left"));
        UserDailyTip record = record(userId, chosen, today);
        return toView(chosen, record);
    }

    /**
     * {@code POST /tips/{tipKey}/feedback} (docs/05 §20.3).
     *
     * <p>아직 보여 준 적 없는 팁이면 404다 — <b>읽기 전에 고를 수 없다.</b>
     *
     * @return 이번에 기록했으면 {@code created = true}(201), 이미 있던 값이면 false(200)
     */
    @Transactional
    public FeedbackResult chooseFeedback(UUID userId, String tipKey, TipFeedback feedback) {
        DailyTip tip = requireTip(tipKey);
        UserDailyTip record =
                userDailyTipRepository
                        .findByUserIdAndTipKey(userId, tipKey)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "tip not shown yet"));
        boolean created = record.chooseFeedback(feedback);
        userDailyTipRepository.flush();
        if (created && feedback == TipFeedback.LEARNED) {
            registerReview(userId, tip);
        }
        return new FeedbackResult(toView(tip, record), created);
    }

    /**
     * "새로 알았어요" → 복습 카드 (docs/06 §5.12 TIP-5).
     *
     * <p>읽고 끝나면 며칠 뒤엔 없던 일이 된다. 그래서 새로 안 것만 다음 날 한 번 돌아온다 — 읽은 것과 아는 것을 가르는 자리다.
     *
     * <p>활성 skill이 하나도 없으면 카드를 만들지 않는다({@code review_item.skill_id}는 not null).
     */
    private void registerReview(UUID userId, DailyTip tip) {
        firstActiveSkill(tip)
                .ifPresent(
                        skill ->
                                reviewItemService.upsert(
                                        new NewReviewItem(
                                                userId,
                                                skill.id(),
                                                ContentOrigin.MANUAL,
                                                ReviewItemSourceType.TIP,
                                                null,
                                                "TIP:" + tip.key(),
                                                ReviewType.EXPLAIN,
                                                "\""
                                                        + tip.title()
                                                        + "\" — 이 증상이 왜 생기는지와 어디를 먼저 보면 되는지 설명하세요.",
                                                tip.cause() + "\n" + tip.whereToLook(),
                                                TIP_RUBRIC)));
    }

    /** {@code GET /tips/{tipKey}} (docs/05 §20.4a). 은퇴한 팁도 돌려주고 표시 기록은 만들지 않는다. */
    @Transactional(readOnly = true)
    public DailyTipView get(UUID userId, String tipKey) {
        DailyTip tip = requireTip(tipKey);
        return toView(
                tip, userDailyTipRepository.findByUserIdAndTipKey(userId, tipKey).orElse(null));
    }

    /**
     * 표시 기록 1행 + {@code TIP_VIEWED} (docs/05 §20.2 4·5단계).
     *
     * <p>동시 요청으로 unique를 위반하면 오류가 아니라 <b>그 행을 다시 읽어</b> 같은 응답을 돌려준다(6단계).
     */
    private UserDailyTip record(UUID userId, DailyTip tip, LocalDate today) {
        UserDailyTip record = UserDailyTip.shown(userId, tip.key(), today, clock.instant());
        try {
            userDailyTipRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException exception) {
            return userDailyTipRepository
                    .findByUserIdAndTipKey(userId, tip.key())
                    .orElseThrow(() -> exception);
        }
        UUID skillId = firstActiveSkill(tip).map(SkillRef::id).orElse(null);
        learningEventRecorder.record(
                new LearningEventRecorder.NewLearningEvent(
                        userId,
                        skillId,
                        null,
                        LearningEventType.TIP_VIEWED,
                        null,
                        null,
                        today,
                        new TipViewedPayload(tip.key(), tip.series().name(), tip.level().name()),
                        "TIP_VIEWED:" + tip.key(),
                        clock.instant()));
        return record;
    }

    private DailyTip requireTip(String tipKey) {
        return dailyTipRegistry
                .find(tipKey)
                .orElseThrow(
                        () -> new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "tip not found"));
    }

    private Optional<SkillRef> firstActiveSkill(DailyTip tip) {
        Map<String, SkillRef> skills = skillCatalogQueryService.findActiveByCodes(tip.skillCodes());
        return tip.skillCodes().stream()
                .map(skills::get)
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }

    private DailyTipView toView(DailyTip tip, @Nullable UserDailyTip record) {
        Map<String, SkillRef> skills = skillCatalogQueryService.findActiveByCodes(tip.skillCodes());
        List<SkillRef> refs = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (String code : tip.skillCodes()) {
            SkillRef ref = skills.get(code);
            if (ref != null && seen.add(ref.id())) {
                refs.add(ref);
            }
        }
        return new DailyTipView(
                tip.key(),
                tip.series(),
                tip.level(),
                tip.title(),
                tip.symptom(),
                tip.cause(),
                tip.example(),
                tip.whereToLook(),
                tip.experiment(),
                tip.sourceUrl(),
                refs,
                tip.estimatedMinutes(),
                record == null ? null : record.getShownOn(),
                record == null ? null : record.getFeedback());
    }

    /** {@code RECENT_DAYS}만큼 거슬러 올라가는 시작 plan-day. */
    static LocalDate recentFrom(LocalDate today) {
        return today.minusDays(RECENT_DAYS);
    }

    /**
     * 피드백 저장 결과.
     *
     * @param created 이번 요청이 값을 기록했으면 true (201). 이미 있었으면 false (200)
     */
    public record FeedbackResult(DailyTipView tip, boolean created) {}
}
