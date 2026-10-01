package com.devpilot.review.application;

import com.devpilot.common.domain.ContentOrigin;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.common.web.CursorCodec;
import com.devpilot.common.web.CursorPage;
import com.devpilot.learning.application.LearningEventRecorder;
import com.devpilot.learning.domain.EventSourceType;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.domain.TermCardCreatedPayload;
import com.devpilot.review.application.ReviewItemService.NewReviewItem;
import com.devpilot.review.domain.ReviewItem;
import com.devpilot.review.domain.ReviewItemSourceType;
import com.devpilot.review.domain.ReviewType;
import com.devpilot.review.domain.RubricItem;
import com.devpilot.review.domain.Term;
import com.devpilot.review.infrastructure.ReviewItemRepository;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 용어 사전 (docs/05 §20.5~§20.7, BL-TRM-01~04). AI를 부르지 않는다.
 *
 * <p>같은 것을 두 이름으로 부르면 읽을 때마다 같은 것인지 다시 확인해야 한다. 그래서 검색은 별칭까지 훑지만 보여 주는 표기는 늘 대표 표기 하나다(docs/19
 * §3.10).
 */
@Service
@Transactional(readOnly = true)
public class TermQueryService {

    /** 용어 카드의 채점 기준 1개 (docs/05 §20.7). 복습 화면의 {@code CONCEPT_HINT}가 첫 항목을 쓴다(§11.5). */
    private static final List<RubricItem> TERM_RUBRIC =
            List.of(new RubricItem("R1", "대표 표기와 뜻을 짝지어 말한다"));

    private static final String CONCEPT_PREFIX = "TERM:";

    private final TermRegistry termRegistry;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final ReviewItemRepository reviewItemRepository;
    private final ReviewItemService reviewItemService;
    private final LearningEventRecorder learningEventRecorder;
    private final CursorCodec cursorCodec;
    private final Clock clock;

    TermQueryService(
            TermRegistry termRegistry,
            SkillCatalogQueryService skillCatalogQueryService,
            ReviewItemRepository reviewItemRepository,
            ReviewItemService reviewItemService,
            LearningEventRecorder learningEventRecorder,
            CursorCodec cursorCodec,
            Clock clock) {
        this.termRegistry = termRegistry;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.reviewItemRepository = reviewItemRepository;
        this.reviewItemService = reviewItemService;
        this.learningEventRecorder = learningEventRecorder;
        this.cursorCodec = cursorCodec;
        this.clock = clock;
    }

    /**
     * {@code GET /terms} (docs/05 §20.5). 은퇴하지 않은 용어, {@code termKey} ASC.
     *
     * <p>콘텐츠 목록이라 UUID id가 없다 — cursor는 {@code termKey} 하나로 비교한다(docs/05 §1.5 마지막 행).
     */
    public CursorPage<TermSummaryView> list(
            UUID userId,
            @Nullable String q,
            @Nullable UUID skillId,
            int limit,
            @Nullable String cursor) {
        String after = cursorCodec.decodeContent(cursor);
        String needle = q == null ? null : q.strip().toLowerCase(Locale.ROOT);
        Set<String> created = termConceptKeys(userId);
        // skill 필터는 code → id를 한 번만 읽는다. 용어마다 물으면 그 자리가 바로 N+1이다
        Set<String> codesOfSkill = skillId == null ? Set.of() : codesOf(skillId);
        List<TermSummaryView> page = new ArrayList<>();
        String next = null;
        for (Term term : termRegistry.all()) {
            if (term.retired()
                    || (after != null && term.key().compareTo(after) <= 0)
                    || !matches(term, needle)
                    || (skillId != null && Collections.disjoint(term.skillCodes(), codesOfSkill))) {
                continue;
            }
            if (page.size() == limit) {
                // 한 건 더 보이면 다음 페이지가 있다는 뜻이다. 그 건은 담지 않는다
                next = cursorCodec.encodeContent(page.getLast().termKey());
                break;
            }
            page.add(
                    new TermSummaryView(
                            term.key(),
                            term.representative(),
                            term.english(),
                            term.definition(),
                            term.level(),
                            created.contains(term.conceptKey())));
        }
        return new CursorPage<>(page, next);
    }

    /** {@code GET /terms/{termKey}} (docs/05 §20.6). 은퇴한 용어도 돌려준다. */
    public TermView get(CurrentUser user, String termKey) {
        Term term = requireTerm(termKey);
        List<TermRefView> confusable = new ArrayList<>();
        for (String key : term.confusableWith()) {
            // 콘텐츠 검증(CV-103)이 막지만, 등록 전 상태에서도 목록이 깨지지 않게 한 번 더 거른다
            termRegistry
                    .find(key)
                    .ifPresent(
                            other ->
                                    confusable.add(
                                            new TermRefView(
                                                    other.key(),
                                                    other.representative(),
                                                    other.english())));
        }
        return new TermView(
                term.key(),
                term.representative(),
                term.english(),
                term.aliases(),
                term.definition(),
                term.example(),
                confusable,
                activeSkills(term),
                term.level(),
                term.sourceUrl(),
                term.retired(),
                cards(user, term));
    }

    /**
     * {@code POST /terms/{termKey}/card} (docs/05 §20.7). <b>양방향 2장</b>을 만든다.
     *
     * <p>표기를 보고 뜻이 떠오르는 것과 뜻을 보고 표기가 떠오르는 것은 다른 일이다. 회의에서 막히는 쪽은 대개 뒤쪽이라 역방향을 같이 만든다.
     *
     * <p>이미 있는 concept key는 새로 만들지 않고 문항도 덮어쓰지 않는다(§11.5). 활성 skill이 하나도 없는 용어는 카드를 만들지 않는다({@code
     * review_item.skill_id}는 not null).
     */
    @Transactional
    public TermCardResponse createCards(CurrentUser user, String termKey) {
        Term term = requireTerm(termKey);
        Optional<SkillRef> skill = firstActiveSkill(term);
        if (skill.isEmpty()) {
            return new TermCardResponse(term.key(), List.of(), 0);
        }
        UUID skillId = skill.get().id();
        ReviewItemService.UpsertResult forward =
                reviewItemService.upsert(
                        card(
                                user.userId(),
                                skillId,
                                term.conceptKey(),
                                term.representative() + " (" + term.english() + ")",
                                term.definition() + "\n" + term.example()));
        ReviewItemService.UpsertResult reverse =
                reviewItemService.upsert(
                        card(
                                user.userId(),
                                skillId,
                                term.reverseConceptKey(),
                                term.definition(),
                                reverseAnswer(term)));
        reviewItemRepository.flush();
        int createdCount = (forward.created() ? 1 : 0) + (reverse.created() ? 1 : 0);
        if (createdCount > 0) {
            recordEvent(user, term, forward.reviewItemId(), skillId, createdCount);
        }
        return new TermCardResponse(term.key(), cards(user, term), createdCount);
    }

    private NewReviewItem card(
            UUID userId, UUID skillId, String conceptKey, String prompt, String expectedAnswer) {
        return new NewReviewItem(
                userId,
                skillId,
                ContentOrigin.MANUAL,
                ReviewItemSourceType.TERM,
                null,
                conceptKey,
                ReviewType.RECALL,
                prompt,
                expectedAnswer,
                TERM_RUBRIC);
    }

    /** 역방향의 답. 별칭이 있으면 같이 준다 — 다르게 답해도 맞다는 것을 그 자리에서 알아야 한다. */
    private static String reverseAnswer(Term term) {
        return term.aliases().isEmpty()
                ? term.representative()
                : term.representative() + " (" + String.join(", ", term.aliases()) + ")";
    }

    private void recordEvent(
            CurrentUser user, Term term, UUID reviewItemId, UUID skillId, int createdCount) {
        learningEventRecorder.record(
                new LearningEventRecorder.NewLearningEvent(
                        user.userId(),
                        skillId,
                        null,
                        LearningEventType.TERM_CARD_CREATED,
                        EventSourceType.REVIEW_ITEM,
                        reviewItemId,
                        PlanDayCalculator.planDate(
                                clock.instant(), user.zoneId(), user.dayStartHour()),
                        new TermCardCreatedPayload(term.key(), term.conceptKey(), createdCount),
                        "TERM_CARD:" + term.key() + ":" + skillId,
                        clock.instant()));
    }

    /** 이 사용자가 이 용어로 만든 카드 (concept key ASC). 없으면 빈 목록. */
    private List<CreatedCardView> cards(CurrentUser user, Term term) {
        List<ReviewItem> items =
                reviewItemRepository.findByConceptKeys(
                        user.userId(), List.of(term.conceptKey(), term.reverseConceptKey()));
        return items.stream()
                .sorted(Comparator.comparing(ReviewItem::getConceptKey))
                .map(
                        item ->
                                new CreatedCardView(
                                        item.getId(),
                                        item.getConceptKey(),
                                        item.getReviewType(),
                                        PlanDayCalculator.planDate(
                                                item.getDueAt(),
                                                user.zoneId(),
                                                user.dayStartHour())))
                .toList();
    }

    /** 이 사용자가 이미 카드를 만든 용어의 concept key. */
    private Set<String> termConceptKeys(UUID userId) {
        Set<String> keys = new HashSet<>();
        for (String key : reviewItemRepository.findConceptKeys(userId)) {
            if (key.startsWith(CONCEPT_PREFIX)) {
                keys.add(key);
            }
        }
        return keys;
    }

    /** 대표 표기·영어·별칭에 대한 부분 일치, 대소문자 무시 (docs/05 §20.5). */
    private static boolean matches(Term term, @Nullable String needle) {
        if (needle == null || needle.isEmpty()) {
            return true;
        }
        if (contains(term.representative(), needle) || contains(term.english(), needle)) {
            return true;
        }
        return term.aliases().stream().anyMatch(alias -> contains(alias, needle));
    }

    private static boolean contains(String text, String needle) {
        return text.toLowerCase(Locale.ROOT).contains(needle);
    }

    /** 그 skill을 가리키는 code 집합. 없는 {@code skillId}는 오류가 아니라 빈 집합이고, 그러면 목록도 빈다(docs/05 §20.5). */
    private Set<String> codesOf(UUID skillId) {
        Set<String> all = new HashSet<>();
        termRegistry.all().forEach(term -> all.addAll(term.skillCodes()));
        Map<String, SkillRef> skills = skillCatalogQueryService.findActiveByCodes(all);
        Set<String> codes = new HashSet<>();
        skills.forEach(
                (code, ref) -> {
                    if (ref.id().equals(skillId)) {
                        codes.add(code);
                    }
                });
        return codes;
    }

    /** 파일 순서를 지키고 은퇴한 skill은 뺀다 (docs/05 §19.7과 같은 규칙). */
    private List<SkillRef> activeSkills(Term term) {
        Map<String, SkillRef> skills =
                skillCatalogQueryService.findActiveByCodes(term.skillCodes());
        List<SkillRef> refs = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (String code : term.skillCodes()) {
            SkillRef ref = skills.get(code);
            if (ref != null && seen.add(ref.id())) {
                refs.add(ref);
            }
        }
        return refs;
    }

    private Optional<SkillRef> firstActiveSkill(Term term) {
        Map<String, SkillRef> skills =
                skillCatalogQueryService.findActiveByCodes(term.skillCodes());
        return term.skillCodes().stream().map(skills::get).filter(Objects::nonNull).findFirst();
    }

    private Term requireTerm(String termKey) {
        return termRegistry
                .find(termKey)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.RESOURCE_NOT_FOUND, "term not found"));
    }
}
