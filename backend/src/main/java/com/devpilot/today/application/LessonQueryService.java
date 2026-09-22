package com.devpilot.today.application;

import com.devpilot.common.domain.ContentOrigin;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.learning.application.LearningEventQueryService;
import com.devpilot.learning.application.LearningEventQueryService.LessonProgressView;
import com.devpilot.learning.application.LearningEventRecorder;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.domain.UnitSolvedPayload;
import com.devpilot.review.application.ReviewItemService;
import com.devpilot.review.application.ReviewItemService.NewReviewItem;
import com.devpilot.review.domain.ReviewItemSourceType;
import com.devpilot.review.domain.ReviewType;
import com.devpilot.review.domain.RubricItem;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.today.application.LessonListView.LessonSummaryView;
import com.devpilot.today.application.LessonView.LessonExampleView;
import com.devpilot.today.application.LessonView.LessonProblemView;
import com.devpilot.today.application.LessonView.LessonQuestionView;
import com.devpilot.today.application.LessonView.LessonSourceView;
import com.devpilot.today.application.LessonView.LessonUnitView;
import com.devpilot.today.domain.CompleteQuestion;
import com.devpilot.today.domain.HelpLevel;
import com.devpilot.today.domain.Lesson;
import com.devpilot.today.domain.LessonProblem;
import com.devpilot.today.domain.LessonSource;
import com.devpilot.today.domain.LessonStatus;
import com.devpilot.today.domain.LessonUnit;
import com.devpilot.today.domain.PredictQuestion;
import com.devpilot.today.domain.UnitAnswerMatcher;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개념 노트 조회·채점·기록 (docs/05 §21, BL-LSN-02~04). 본문은 {@link LessonRegistry}에 있고 skill code만 DB의 활성
 * skill로 해석한다.
 *
 * <p><b>AI를 부르지 않는다.</b> 예측·빈칸은 {@link UnitAnswerMatcher}가 문자열로 채점하고, 백지 문제는 채점하지 않는다 — 모범 답안을 돌려주고
 * 사용자가 스스로 견준다(docs/01 원칙 9: 서버는 사용자 코드를 실행하지 않는다).
 */
@Service
@Transactional(readOnly = true)
public class LessonQueryService {

    /**
     * 목록 정렬 (docs/05 §21.9): status → lastSolvedAt DESC(IN_PROGRESS·DONE) → lessonKey ASC. 아직 안 연
     * 노트는 lastSolvedAt이 없으므로 그 자리에서 곧바로 key 순이 된다.
     */
    private static final Comparator<LessonSummaryView> LIST_ORDER =
            Comparator.comparingInt((LessonSummaryView view) -> view.status().listRank())
                    .thenComparing(
                            LessonSummaryView::lastSolvedAt,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(LessonSummaryView::lessonKey);

    private final LessonRegistry lessonRegistry;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final LearningEventQueryService learningEventQueryService;
    private final LearningEventRecorder learningEventRecorder;
    private final ReviewItemService reviewItemService;
    private final Clock clock;

    public LessonQueryService(
            LessonRegistry lessonRegistry,
            SkillCatalogQueryService skillCatalogQueryService,
            LearningEventQueryService learningEventQueryService,
            LearningEventRecorder learningEventRecorder,
            ReviewItemService reviewItemService,
            Clock clock) {
        this.lessonRegistry = lessonRegistry;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.learningEventQueryService = learningEventQueryService;
        this.learningEventRecorder = learningEventRecorder;
        this.reviewItemService = reviewItemService;
        this.clock = clock;
    }

    /** 노트 하나. 없으면 404 {@code RESOURCE_NOT_FOUND}. */
    public LessonView get(UUID userId, String lessonKey) {
        return toView(userId, lesson(lessonKey));
    }

    /**
     * 노트 목록과 진행 (docs/05 §21.9). 활성 skill로 풀리는 은퇴하지 않은 노트만, 이어서 할 것이 맨 위로 오게 정렬한다.
     *
     * <p>진행은 {@link LearningEventQueryService#lessonProgress}를 <b>한 번</b> 불러 모든 노트 것을 한꺼번에 받는다.
     */
    public LessonListView list(UUID userId) {
        List<Lesson> lessons = lessonRegistry.all().stream().filter(l -> !l.retired()).toList();
        Map<String, SkillRef> skills =
                skillCatalogQueryService.findActiveByCodes(
                        lessons.stream().map(Lesson::skillCode).toList());
        Map<String, LessonProgressView> progress = learningEventQueryService.lessonProgress(userId);
        List<LessonSummaryView> summaries = new ArrayList<>();
        for (Lesson lesson : lessons) {
            SkillRef skill = skills.get(lesson.skillCode());
            if (skill == null) {
                continue;
            }
            LessonProgressView done = progress.get(lesson.key());
            int solved = done == null ? 0 : done.solvedUnitCount();
            summaries.add(
                    new LessonSummaryView(
                            lesson.key(),
                            skill.id().toString(),
                            skill.code(),
                            skill.name(),
                            lesson.title(),
                            lesson.oneLine(),
                            lesson.units().size(),
                            solved,
                            lesson.units().stream().mapToInt(LessonUnit::minutes).sum(),
                            LessonStatus.of(solved, lesson.units().size()),
                            done == null ? null : done.lastSolvedAt()));
        }
        summaries.sort(LIST_ORDER);
        return new LessonListView(List.copyOf(summaries));
    }

    /** 그 skill의 노트. 없으면 404. */
    public LessonView getForSkill(UUID userId, UUID skillId) {
        SkillRef skill =
                Optional.ofNullable(
                                skillCatalogQueryService.findRefs(List.of(skillId)).get(skillId))
                        .orElseThrow(LessonQueryService::notFound);
        return toView(
                userId,
                lessonRegistry
                        .findBySkillCode(skill.code())
                        .orElseThrow(LessonQueryService::notFound));
    }

    /** 출력 예측 채점 (docs/05 §21.4). */
    public PredictResult checkPredict(String lessonKey, String unitKey, String answer) {
        PredictQuestion question = unit(lessonKey, unitKey).predict();
        return new PredictResult(
                UnitAnswerMatcher.matches(answer, question.answer()),
                question.answer(),
                question.explanation());
    }

    /** 빈칸 채점 (docs/05 §21.5). 답 개수가 빈칸 수와 다르면 예외를 던지지 않고 controller가 먼저 막는다. */
    public CompleteResult checkComplete(String lessonKey, String unitKey, List<String> answers) {
        CompleteQuestion question = unit(lessonKey, unitKey).complete();
        List<Boolean> results = UnitAnswerMatcher.matchBlanks(answers, question.answers());
        List<String> expected = question.answers().stream().map(List::getFirst).toList();
        return new CompleteResult(
                results.stream().allMatch(Boolean::booleanValue),
                results,
                expected,
                question.explanation());
    }

    /** 빈칸 수. controller가 답 개수를 검사할 때 쓴다. */
    public int blanks(String lessonKey, String unitKey) {
        return unit(lessonKey, unitKey).complete().blanks();
    }

    /** 확인 목록 개수. controller가 {@code selfChecksMet} 상한을 검사할 때 쓴다. */
    public int selfCheckCount(String lessonKey, String unitKey) {
        return unit(lessonKey, unitKey).problem().selfChecks().size();
    }

    /** 모범 답안과 확인 목록 (docs/05 §21.6). 상태를 바꾸지 않는다. */
    public AnswerResult answer(String lessonKey, String unitKey) {
        LessonProblem problem = unit(lessonKey, unitKey).problem();
        return new AnswerResult(problem.modelAnswer(), problem.selfChecks());
    }

    /**
     * 단위 한 바퀴를 마쳤다 (docs/05 §21.7). {@code UNIT_SOLVED} 학습 이벤트 1건을 남긴다 — 사용자 답은 받지도 저장하지도 않는다.
     * dedupe 하지 않으므로 다시 풀면 이벤트가 하나 더 쌓인다.
     */
    @Transactional
    public FinishResult finish(
            UUID userId,
            String lessonKey,
            String unitKey,
            HelpLevel helpLevel,
            @Nullable Integer selfChecksMet,
            LocalDate planDate) {
        Lesson lesson = lesson(lessonKey);
        LessonUnit unit = lesson.unit(unitKey).orElseThrow(LessonQueryService::notFound);
        UUID skillId =
                Optional.ofNullable(
                                skillCatalogQueryService
                                        .findActiveByCodes(List.of(lesson.skillCode()))
                                        .get(lesson.skillCode()))
                        .map(SkillRef::id)
                        .orElse(null);
        learningEventRecorder.record(
                new LearningEventRecorder.NewLearningEvent(
                        userId,
                        skillId,
                        null,
                        LearningEventType.UNIT_SOLVED,
                        null,
                        null,
                        planDate,
                        new UnitSolvedPayload(
                                lessonKey, unit.key(), helpLevel.name(), selfChecksMet),
                        null,
                        clock.instant()));
        if (helpLevel != HelpLevel.NONE && skillId != null) {
            registerReview(userId, skillId, unit);
        }
        return new FinishResult(unit.key(), helpLevel, selfChecksMet, clock.instant());
    }

    /**
     * 도움을 받아 푼 단위는 복습으로 돌아온다 (재설계안 R-0 6번).
     *
     * <p><b>임시 연결이다.</b> 지금은 기존 scheduler에 그대로 얹어 다음 plan-day에 due가 된다 — 1·7·30일 간격 사다리와 "도움 없이 풀어도
     * 7일 뒤 한 번"(D-10)은 `06` §6.2를 고치는 Step 3에서 온다.
     *
     * <p>{@code conceptKey}는 단위 key라서 같은 단위를 다시 풀면 카드가 하나 더 생기지 않고 due만 당겨진다({@code
     * ReviewItemService#upsert}). {@code sourceId}는 두지 않는다 — 단위를 가리키는 것은 UUID가 아니다(`04` §3).
     *
     * <p>skill을 찾지 못한 노트는 카드를 만들지 않는다. 복습은 skill 단위로 도는데 붙일 자리가 없기 때문이다.
     */
    private void registerReview(UUID userId, UUID skillId, LessonUnit unit) {
        LessonProblem problem = unit.problem();
        List<RubricItem> rubric = new ArrayList<>();
        for (int index = 0; index < problem.selfChecks().size(); index++) {
            rubric.add(new RubricItem("S" + (index + 1), problem.selfChecks().get(index)));
        }
        reviewItemService.upsert(
                new NewReviewItem(
                        userId,
                        skillId,
                        ContentOrigin.SEED,
                        ReviewItemSourceType.LESSON_UNIT,
                        null,
                        unit.key(),
                        ReviewType.EXPLAIN,
                        problem.prompt(),
                        problem.modelAnswer(),
                        rubric));
    }

    private Lesson lesson(String lessonKey) {
        return lessonRegistry.find(lessonKey).orElseThrow(LessonQueryService::notFound);
    }

    private LessonUnit unit(String lessonKey, String unitKey) {
        return lesson(lessonKey).unit(unitKey).orElseThrow(LessonQueryService::notFound);
    }

    private static NotFoundException notFound() {
        return new NotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "lesson not found");
    }

    private LessonView toView(UUID userId, Lesson lesson) {
        Optional<SkillRef> skill =
                Optional.ofNullable(
                        skillCatalogQueryService
                                .findActiveByCodes(List.of(lesson.skillCode()))
                                .get(lesson.skillCode()));
        List<LearningEventQueryService.UnitSolvedView> progress =
                learningEventQueryService.unitProgress(userId, lesson.key());
        return new LessonView(
                lesson.key(),
                skill.map(SkillRef::id).orElse(null),
                lesson.skillCode(),
                skill.map(SkillRef::name).orElse(lesson.skillCode()),
                lesson.title(),
                lesson.whyItMatters(),
                lesson.oneLine(),
                lesson.units().stream().map(unit -> toView(unit, progress)).toList(),
                lesson.commonMistakes(),
                lesson.inProject(),
                lesson.sources().stream().map(LessonQueryService::toView).toList(),
                lesson.readMore().stream().map(LessonQueryService::toView).toList(),
                lesson.retired());
    }

    private static LessonUnitView toView(
            LessonUnit unit, List<LearningEventQueryService.UnitSolvedView> progress) {
        return new LessonUnitView(
                unit.key(),
                unit.title(),
                unit.minutes(),
                unit.core(),
                unit.explain(),
                new LessonExampleView(
                        unit.example().language(),
                        unit.example().code(),
                        unit.example().output(),
                        unit.example().note()),
                new LessonQuestionView(
                        unit.predict().question(),
                        unit.predict().code(),
                        unit.predict().choices(),
                        0),
                new LessonQuestionView(
                        unit.complete().question(),
                        unit.complete().code(),
                        List.of(),
                        unit.complete().blanks()),
                new LessonProblemView(
                        unit.problem().prompt(),
                        unit.problem().deliverables(),
                        unit.problem().starterCode(),
                        unit.problem().hints()),
                unit.prerequisiteUnits(),
                progress.stream()
                        .filter(item -> item.unitKey().equals(unit.key()))
                        .findFirst()
                        .map(
                                item ->
                                        new LessonView.UnitProgressView(
                                                true,
                                                HelpLevel.valueOf(item.helpLevel()),
                                                item.solvedAt(),
                                                item.selfChecksMet()))
                        .orElse(null));
    }

    private static LessonSourceView toView(LessonSource source) {
        return new LessonSourceView(source.title(), source.url(), source.versionScope());
    }

    /** 예측 채점 결과 (docs/05 §21.4). */
    public record PredictResult(boolean correct, String expected, String explanation) {}

    /** 빈칸 채점 결과 (docs/05 §21.5). */
    public record CompleteResult(
            boolean correct, List<Boolean> results, List<String> expected, String explanation) {}

    /** 모범 답안 (docs/05 §21.6). */
    public record AnswerResult(String modelAnswer, List<String> selfChecks) {}

    /** 단위를 마친 기록 (docs/05 §21.7). */
    public record FinishResult(
            String unitKey,
            HelpLevel helpLevel,
            @Nullable Integer selfChecksMet,
            java.time.Instant recordedAt) {}
}
