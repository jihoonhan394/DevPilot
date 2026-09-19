package com.devpilot.today.application;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.today.domain.CuratedReading;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코드 읽기 과제 조회 (docs/05 §19.7, BL-TDY-16). {@link CuratedReadingRegistry}에서 읽고 skill code만 DB의 활성
 * skill로 해석한다. 사용자 소유 리소스가 아니므로 모든 사용자가 같은 내용을 본다. 은퇴한 reading도 200이다. AI·외부 요청이 없다.
 */
@Service
@Transactional(readOnly = true)
public class ReadingQueryService {

    private final CuratedReadingRegistry curatedReadingRegistry;
    private final SkillCatalogQueryService skillCatalogQueryService;

    public ReadingQueryService(
            CuratedReadingRegistry curatedReadingRegistry,
            SkillCatalogQueryService skillCatalogQueryService) {
        this.curatedReadingRegistry = curatedReadingRegistry;
        this.skillCatalogQueryService = skillCatalogQueryService;
    }

    /** registry에 없으면 404 {@code RESOURCE_NOT_FOUND}. */
    public CuratedReadingView get(String readingKey) {
        CuratedReading reading =
                curatedReadingRegistry
                        .find(readingKey)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "reading not found"));
        return toView(reading);
    }

    /** 러버덕 {@code CODE_READING} 대상 요약 등 다른 모듈이 쓰는 조회. 없으면 빈 값. */
    public Optional<CuratedReading> find(String readingKey) {
        return curatedReadingRegistry.find(readingKey);
    }

    private CuratedReadingView toView(CuratedReading reading) {
        Map<String, SkillRef> skills =
                skillCatalogQueryService.findActiveByCodes(reading.skillCodes());
        List<SkillRef> resolved =
                reading.skillCodes().stream().map(skills::get).filter(Objects::nonNull).toList();
        return new CuratedReadingView(
                reading.key(),
                CuratedRepoView.of(reading.repo()),
                reading.path(),
                reading.startLine(),
                reading.endLine(),
                resolved,
                reading.estimatedMinutes(),
                reading.question(),
                reading.lookFor(),
                reading.retired());
    }
}
