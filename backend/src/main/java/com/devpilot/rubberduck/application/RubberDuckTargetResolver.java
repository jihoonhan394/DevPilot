package com.devpilot.rubberduck.application;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.learning.application.LearningSessionQueryService;
import com.devpilot.project.application.SideProjectQueryService;
import com.devpilot.project.application.SideProjectView;
import com.devpilot.review.application.ReviewQueryService;
import com.devpilot.review.application.ReviewQueryService.ReviewItemRef;
import com.devpilot.rubberduck.domain.RubberDuckSession;
import com.devpilot.rubberduck.domain.RubberDuckTargetType;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillDetailView;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.today.application.ReadingQueryService;
import com.devpilot.today.application.TodayQueryService;
import com.devpilot.today.application.TodayQueryService.ReadCodeTaskRef;
import com.devpilot.today.domain.CuratedReading;
import com.devpilot.training.application.ChallengeQueryService;
import com.devpilot.training.application.ChallengeQueryService.AttemptTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 러버덕 대상 확인·요약·skill 유도 (docs/05 §9.5 표). 대상이 본인 것이 아니거나 없으면 400 {@code VALIDATION_FAILED}(field
 * {@code targetId}, code {@code REFERENCE_NOT_FOUND}) — 타인 소유와 없음을 구분하지 않는다(docs/05 §1.2.3). 대상이
 * 지워진 세션도 조회할 수 있어야 하므로 조회 경로는 예외를 던지지 않고 빈 요약을 돌려준다.
 */
@Component
class RubberDuckTargetResolver {

    private static final String TARGET_ID = "targetId";

    private final TodayQueryService todayQueryService;
    private final ReadingQueryService readingQueryService;
    private final ReviewQueryService reviewQueryService;
    private final SideProjectQueryService sideProjectQueryService;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final LearningSessionQueryService learningSessionQueryService;
    private final ChallengeQueryService challengeQueryService;

    RubberDuckTargetResolver(
            TodayQueryService todayQueryService,
            ReadingQueryService readingQueryService,
            ReviewQueryService reviewQueryService,
            SideProjectQueryService sideProjectQueryService,
            SkillCatalogQueryService skillCatalogQueryService,
            LearningSessionQueryService learningSessionQueryService,
            ChallengeQueryService challengeQueryService) {
        this.todayQueryService = todayQueryService;
        this.readingQueryService = readingQueryService;
        this.reviewQueryService = reviewQueryService;
        this.sideProjectQueryService = sideProjectQueryService;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.learningSessionQueryService = learningSessionQueryService;
        this.challengeQueryService = challengeQueryService;
    }

    /** 지금 진행 중인 학습 세션 id (docs/05 §9.6 5단계). 없으면 null. */
    @Nullable UUID inProgressLearningSessionId(UUID userId) {
        return learningSessionQueryService.findInProgressId(userId).orElse(null);
    }

    /**
     * {@code skillCode}가 있으면 활성 skill이어야 한다(400 {@code SKILL_CODE_UNKNOWN}). 없으면 대상에서 유도하고, 유도할 수
     * 없으면 null이다(RD-7 — 그 세션은 학습 이벤트를 남기지 않는다).
     */
    @Nullable UUID resolveSkillId(@Nullable String skillCode, ResolvedTarget target) {
        if (skillCode == null || skillCode.isBlank()) {
            return target.derivedSkillId();
        }
        SkillRef skill =
                skillCatalogQueryService.findActiveByCodes(List.of(skillCode)).get(skillCode);
        if (skill == null) {
            throw new BusinessValidationException(
                    "unknown skill code",
                    List.of(ApiFieldError.of("skillCode", FieldErrorCodes.SKILL_CODE_UNKNOWN)));
        }
        return skill.id();
    }

    /** 시작 요청의 대상 확인 (docs/05 §9.6 2단계). 없으면 {@code REFERENCE_NOT_FOUND}. */
    ResolvedTarget require(
            UUID userId,
            RubberDuckTargetType targetType,
            @Nullable UUID targetId,
            @Nullable String conceptKey) {
        return switch (targetType) {
            case CONCEPT -> concept(conceptKey);
            case CODE_READING -> codeReading(userId, requireId(targetId));
            case REVIEW_ITEM -> reviewItem(userId, requireId(targetId));
            case PROJECT_WORK -> projectWork(userId, requireId(targetId));
            case CHALLENGE -> challenge(userId, requireId(targetId));
        };
    }

    /** {@code CHALLENGE} 대상은 본인 attempt다 (docs/05 §9.5 표, docs/06 §9.5 RD-3 연결). */
    private ResolvedTarget challenge(UUID userId, UUID targetId) {
        AttemptTarget target =
                challengeQueryService
                        .findAttemptTarget(userId, targetId)
                        .orElseThrow(RubberDuckTargetResolver::referenceNotFound);
        return new ResolvedTarget(
                targetId, null, null, target.title(), target.summary(), target.skillId());
    }

    /** 조회용: 대상이 지워졌으면 {@code title}·{@code summary}가 비어 있다(docs/05 §9.5). */
    ResolvedTarget forSession(RubberDuckSession session) {
        UUID targetId = session.getTargetId();
        String conceptKey = session.getConceptKey();
        try {
            if (session.getTargetType() == RubberDuckTargetType.CONCEPT) {
                return concept(conceptKey);
            }
            return targetId == null
                    ? missing(session)
                    : require(session.getUserId(), session.getTargetType(), targetId, conceptKey);
        } catch (BusinessValidationException exception) {
            return missing(session);
        }
    }

    /** {@code conceptKey}의 접두사와 {@code .} 경계로 가장 길게 일치하는 활성 skill code (docs/05 §9.8). */
    Optional<UUID> skillIdForConceptKey(String conceptKey) {
        UUID best = null;
        int bestLength = 0;
        for (SkillDetailView skill : skillCatalogQueryService.activeSkillDetails().values()) {
            String code = skill.code();
            boolean prefix =
                    conceptKey.equals(code)
                            || (conceptKey.startsWith(code)
                                    && conceptKey.charAt(code.length()) == '.');
            if (prefix && code.length() > bestLength) {
                best = skill.id();
                bestLength = code.length();
            }
        }
        return Optional.ofNullable(best);
    }

    /** 세션 skill + 그 선행 skill의 code (docs/17 §3.12 {@code availableSkillCodes}). */
    List<String> availableSkillCodes(@Nullable UUID skillId) {
        if (skillId == null) {
            return List.of();
        }
        Map<UUID, SkillDetailView> details = skillCatalogQueryService.activeSkillDetails();
        SkillDetailView skill = details.get(skillId);
        if (skill == null) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        codes.add(skill.code());
        skill.prerequisiteIds().stream()
                .map(details::get)
                .filter(Objects::nonNull)
                .map(SkillDetailView::code)
                .forEach(codes::add);
        return List.copyOf(codes);
    }

    private ResolvedTarget concept(@Nullable String conceptKey) {
        String key = conceptKey == null ? "" : conceptKey;
        return new ResolvedTarget(
                null, key, null, key, key, skillIdForConceptKey(key).orElse(null));
    }

    private ResolvedTarget codeReading(UUID userId, UUID targetId) {
        ReadCodeTaskRef task =
                todayQueryService
                        .findReadCodeTask(userId, targetId)
                        .orElseThrow(RubberDuckTargetResolver::referenceNotFound);
        String readingKey = task.readingKey();
        Optional<CuratedReading> reading =
                readingKey == null ? Optional.empty() : readingQueryService.find(readingKey);
        String summary = reading.map(RubberDuckTargetResolver::readingSummary).orElse(task.title());
        return new ResolvedTarget(
                targetId, null, readingKey, task.title(), summary, task.skillId());
    }

    private ResolvedTarget reviewItem(UUID userId, UUID targetId) {
        ReviewItemRef item =
                reviewQueryService
                        .findItemRef(userId, targetId)
                        .orElseThrow(RubberDuckTargetResolver::referenceNotFound);
        return new ResolvedTarget(
                targetId, null, null, item.prompt(), item.prompt(), item.skillId());
    }

    private ResolvedTarget projectWork(UUID userId, UUID targetId) {
        SideProjectView project =
                sideProjectQueryService
                        .find(userId, targetId)
                        .orElseThrow(RubberDuckTargetResolver::referenceNotFound);
        String description = project.description();
        String summary = description == null ? project.name() : project.name() + "\n" + description;
        return new ResolvedTarget(targetId, null, null, project.name(), summary, null);
    }

    private static ResolvedTarget missing(RubberDuckSession session) {
        return new ResolvedTarget(
                session.getTargetId(), session.getConceptKey(), null, null, "", null);
    }

    /** 저장소·경로·줄 범위·질문 (docs/17 §3.11). 코드 본문은 서버에 없다. */
    private static String readingSummary(CuratedReading reading) {
        return reading.repo().name()
                + " · "
                + reading.path()
                + " "
                + reading.startLine()
                + "~"
                + reading.endLine()
                + "줄\n"
                + reading.question();
    }

    private static UUID requireId(@Nullable UUID targetId) {
        if (targetId == null) {
            throw referenceNotFound();
        }
        return targetId;
    }

    private static BusinessValidationException referenceNotFound() {
        return new BusinessValidationException(
                "rubber duck target not found",
                List.of(ApiFieldError.of(TARGET_ID, FieldErrorCodes.REFERENCE_NOT_FOUND)));
    }

    /**
     * 확인한 대상.
     *
     * @param title 사람이 알아볼 문구. 대상이 지워졌으면 null
     * @param summary AI 입력 {@code targetSummary} (docs/17 §3.11). 대상이 지워졌으면 빈 문자열
     * @param derivedSkillId {@code skillCode} 생략 시 쓸 skill. 유도할 수 없으면 null
     */
    record ResolvedTarget(
            @Nullable UUID targetId,
            @Nullable String conceptKey,
            @Nullable String readingKey,
            @Nullable String title,
            String summary,
            @Nullable UUID derivedSkillId) {}
}
