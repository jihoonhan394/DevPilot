package com.devpilot.today.application;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.today.domain.ConceptReading;
import com.devpilot.today.domain.CuratedReading;
import com.devpilot.today.domain.ReadingKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final SkillCatalogQueryService skillCatalogQueryService;

    public ReadingQueryService(
            CuratedReadingRegistry curatedReadingRegistry,
            ConceptReadingRegistry conceptReadingRegistry,
            SkillCatalogQueryService skillCatalogQueryService) {
        this.curatedReadingRegistry = curatedReadingRegistry;
        this.conceptReadingRegistry = conceptReadingRegistry;
        this.skillCatalogQueryService = skillCatalogQueryService;
    }

    /** 두 registry에 모두 없으면 404 {@code RESOURCE_NOT_FOUND}. */
    public ReadingView get(String readingKey) {
        Optional<CuratedReading> code = curatedReadingRegistry.find(readingKey);
        if (code.isPresent()) {
            return toView(code.get());
        }
        return conceptReadingRegistry
                .find(readingKey)
                .map(this::toView)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.RESOURCE_NOT_FOUND, "reading not found"));
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
}
