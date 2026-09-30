package com.devpilot.today.application;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.today.domain.ConceptReading;
import com.devpilot.today.domain.CuratedReading;
import com.devpilot.today.domain.Lesson;
import com.devpilot.today.domain.LessonUnit;
import com.devpilot.today.domain.ReadingKind;
import com.devpilot.today.infrastructure.LearningTaskRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 읽기 자료 조회 (docs/05 §19.7, BL-TDY-16). key로 {@link CuratedReadingRegistry}(코드 읽기) → 없으면 {@link
 * ConceptReadingRegistry}(개념 읽기) 순서로 찾고 skill code만 DB의 활성 skill로 해석한다. 사용자 소유 리소스가 아니므로 모든 사용자가 같은
 * 내용을 본다. 은퇴한 단위도 200이다. AI·외부 요청이 없다.
 */
@Service
@Transactional(readOnly = true)
public class ReadingQueryService {

    private final CuratedReadingRegistry curatedReadingRegistry;
    private final ConceptReadingRegistry conceptReadingRegistry;
    private final LessonRegistry lessonRegistry;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final LearningTaskRepository learningTaskRepository;

    public ReadingQueryService(
            CuratedReadingRegistry curatedReadingRegistry,
            ConceptReadingRegistry conceptReadingRegistry,
            LessonRegistry lessonRegistry,
            SkillCatalogQueryService skillCatalogQueryService,
            LearningTaskRepository learningTaskRepository) {
        this.curatedReadingRegistry = curatedReadingRegistry;
        this.conceptReadingRegistry = conceptReadingRegistry;
        this.lessonRegistry = lessonRegistry;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.learningTaskRepository = learningTaskRepository;
    }

    /** 세 registry 어디에도 없으면 404 {@code RESOURCE_NOT_FOUND}. */
    public ReadingView get(String readingKey) {
        Optional<CuratedReading> code = curatedReadingRegistry.find(readingKey);
        if (code.isPresent()) {
            return toView(code.get());
        }
        Optional<ConceptReading> concept = conceptReadingRegistry.find(readingKey);
        if (concept.isPresent()) {
            return toView(concept.get());
        }
        return lessonRegistry
                .find(readingKey)
                .map(this::toView)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.RESOURCE_NOT_FOUND, "reading not found"));
    }

    /**
     * 개념 노트는 <b>어디로 가야 하는지만</b> 알려 준다 (docs/05 §19.7). 내용은 노트 화면이 {@code GET /lessons/{key}}로 따로
     * 읽는다 — 진행(마친 단위)이 사용자마다 다르고 이 endpoint는 사용자와 무관하기 때문이다.
     */
    private ReadingView toView(Lesson lesson) {
        return new ReadingView(
                lesson.key(),
                ReadingKind.LESSON,
                resolve(List.of(lesson.skillCode())),
                lesson.units().stream().mapToInt(LessonUnit::minutes).sum(),
                lesson.retired(),
                null,
                null);
    }

    /** 러버덕 {@code CODE_READING} 대상 요약 등 다른 모듈이 쓰는 코드 읽기 조회. 없으면 빈 값. */
    public Optional<CuratedReading> find(String readingKey) {
        return curatedReadingRegistry.find(readingKey);
    }

    private ReadingView toView(CuratedReading reading) {
        return new ReadingView(
                reading.key(),
                ReadingKind.CODE,
                resolve(reading.skillCodes()),
                reading.estimatedMinutes(),
                reading.retired(),
                CodeReadingView.of(reading),
                null);
    }

    private ReadingView toView(ConceptReading reading) {
        return new ReadingView(
                reading.key(),
                ReadingKind.CONCEPT,
                resolve(reading.skillCodes()),
                reading.estimatedMinutes(),
                reading.retired(),
                null,
                ConceptReadingView.of(reading));
    }

    /** 활성 skill로 해석한다. 없는 code는 뺀다 (docs/05 §19.7). */
    private List<SkillRef> resolve(List<String> skillCodes) {
        Map<String, SkillRef> skills = skillCatalogQueryService.findActiveByCodes(skillCodes);
        return skillCodes.stream().map(skills::get).filter(Objects::nonNull).toList();
    }

    /**
     * {@code GET /readings} (docs/05 §19.14): 내가 받은 적이 있는 읽기를 최근 순으로.
     *
     * <p>완료한 reading은 다음 제안에서 빠진다(docs/06 §5.3). 목록이 없으면 <b>읽었던 코드로 돌아갈 길이 없다</b> — 커밋까지 고정해 둔 자료가
     * 한 번 쓰고 사라진다.
     *
     * <p>registry에서 사라진 key는 건너뛴다. 콘텐츠가 빠졌다고 목록이 깨지지는 않는다.
     */
    public List<ReadingHistoryView> history(UUID userId, int limit) {
        Set<String> completed =
                new LinkedHashSet<>(learningTaskRepository.findCompletedReadingKeys(userId));
        List<ReadingHistoryView> items = new ArrayList<>();
        for (ReadingHistoryRow row :
                learningTaskRepository.findReadingHistory(userId, Limit.of(limit))) {
            summarize(row, completed.contains(row.readingKey())).ifPresent(items::add);
        }
        return List.copyOf(items);
    }

    private Optional<ReadingHistoryView> summarize(ReadingHistoryRow row, boolean completed) {
        Optional<CuratedReading> code = curatedReadingRegistry.find(row.readingKey());
        if (code.isPresent()) {
            CuratedReading reading = code.get();
            return Optional.of(
                    item(
                            row,
                            ReadingKind.CODE,
                            reading.question(),
                            reading.repo().name() + " " + reading.path(),
                            reading.estimatedMinutes(),
                            completed,
                            reading.retired()));
        }
        Optional<ConceptReading> concept = conceptReadingRegistry.find(row.readingKey());
        if (concept.isPresent()) {
            ConceptReading reading = concept.get();
            return Optional.of(
                    item(
                            row,
                            ReadingKind.CONCEPT,
                            reading.title(),
                            reading.publisher(),
                            reading.estimatedMinutes(),
                            completed,
                            reading.retired()));
        }
        return lessonRegistry
                .find(row.readingKey())
                .map(
                        lesson ->
                                item(
                                        row,
                                        ReadingKind.LESSON,
                                        lesson.title(),
                                        null,
                                        lesson.units().stream().mapToInt(LessonUnit::minutes).sum(),
                                        completed,
                                        lesson.retired()));
    }

    private static ReadingHistoryView item(
            ReadingHistoryRow row,
            ReadingKind kind,
            String title,
            @Nullable String source,
            @Nullable Integer estimatedMinutes,
            boolean completed,
            boolean retired) {
        return new ReadingHistoryView(
                row.readingKey(),
                kind,
                title,
                source,
                estimatedMinutes,
                row.lastPlanDate(),
                completed,
                retired);
    }
}
