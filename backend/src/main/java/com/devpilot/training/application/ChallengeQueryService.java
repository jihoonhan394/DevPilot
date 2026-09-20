package com.devpilot.training.application;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.web.CursorCodec;
import com.devpilot.common.web.CursorPage;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.training.application.TrainingViews.ChallengeSummaryView;
import com.devpilot.training.application.TrainingViews.ChallengeView;
import com.devpilot.training.application.TrainingViews.LastAttemptView;
import com.devpilot.training.application.TrainingViews.RubricItemView;
import com.devpilot.training.domain.Challenge;
import com.devpilot.training.domain.ChallengeAttempt;
import com.devpilot.training.domain.ChallengePurpose;
import com.devpilot.training.domain.ChallengeRubricItem;
import com.devpilot.training.domain.ChallengeStatus;
import com.devpilot.training.infrastructure.ChallengeAttemptRepository;
import com.devpilot.training.infrastructure.ChallengeRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
 * challenge 조회 (docs/05 §10.2·§10.4, BL-TRN-02). today(BL-TDY-14)와 온보딩 진단(BL-TRN-13)이 후보 목록을 쓴다. 접근
 * 규칙은 공용 seed 또는 본인 소유이고, 그 외는 404 {@code RESOURCE_NOT_FOUND}다.
 *
 * <p>본문 공개: {@code status ∈ {VALIDATED, RETIRED}}일 때만 제목·시나리오·문제를 채운다. 정답 정보(expectedConcepts,
 * rubric, commonMistakes)는 그 challenge를 한 번이라도 평가받은 사용자에게만 보인다(docs/05 §10.1).
 */
@Service
@Transactional(readOnly = true)
public class ChallengeQueryService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeAttemptRepository attemptRepository;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final CursorCodec cursorCodec;

    public ChallengeQueryService(
            ChallengeRepository challengeRepository,
            ChallengeAttemptRepository attemptRepository,
            SkillCatalogQueryService skillCatalogQueryService,
            CursorCodec cursorCodec) {
        this.challengeRepository = challengeRepository;
        this.attemptRepository = attemptRepository;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.cursorCodec = cursorCodec;
    }

    /** {@code GET /challenges} (docs/05 §10.2). */
    public CursorPage<ChallengeSummaryView> list(
            UUID userId,
            @Nullable UUID skillId,
            @Nullable ChallengePurpose purpose,
            int limit,
            @Nullable String cursor) {
        CursorCodec.Position<Instant> position = cursorCodec.decodeInstant(cursor);
        Limit fetch = Limit.of(limit + 1);
        List<Challenge> challenges =
                position == null
                        ? challengeRepository.findValidatedPage(userId, skillId, purpose, fetch)
                        : challengeRepository.findValidatedPageAfter(
                                userId, skillId, purpose, position.sortKey(), position.id(), fetch);
        boolean hasNext = challenges.size() > limit;
        List<Challenge> page = hasNext ? challenges.subList(0, limit) : challenges;
        String nextCursor = null;
        if (hasNext && !page.isEmpty()) {
            Challenge last = page.get(page.size() - 1);
            nextCursor = cursorCodec.encode(last.getCreatedAt(), last.getId());
        }
        Map<UUID, SkillRef> refs = skillRefs(page);
        Map<UUID, ChallengeAttempt> lastAttempts = lastAttempts(userId, page);
        List<ChallengeSummaryView> items = new ArrayList<>();
        for (Challenge challenge : page) {
            ChallengeAttempt last = lastAttempts.get(challenge.getId());
            items.add(
                    new ChallengeSummaryView(
                            challenge.getId(),
                            challenge.getTitle(),
                            challenge.getDifficulty(),
                            challenge.getEstimatedMinutes(),
                            challenge.getPurpose(),
                            challenge.getOrigin(),
                            challenge.isTransferChallenge(),
                            skillsOf(challenge, refs),
                            last == null
                                    ? null
                                    : new LastAttemptView(
                                            last.getId(),
                                            last.getStatus(),
                                            last.getOutcome(),
                                            last.getStartedAt()),
                            challenge.getCreatedAt()));
        }
        return new CursorPage<>(items, nextCursor);
    }

    /** {@code GET /challenges/{challengeId}} (docs/05 §10.4). */
    public ChallengeView get(UUID userId, UUID challengeId) {
        Challenge challenge = require(userId, challengeId);
        boolean answerRevealed = attemptRepository.existsEvaluated(userId, challengeId);
        boolean bodyVisible =
                challenge.getStatus() == ChallengeStatus.VALIDATED
                        || challenge.getStatus() == ChallengeStatus.RETIRED;
        Map<UUID, SkillRef> refs = skillRefs(List.of(challenge));
        UUID activeAttemptId =
                attemptRepository.findActive(userId, challengeId, Limit.of(1)).stream()
                        .findFirst()
                        .map(ChallengeAttempt::getId)
                        .orElse(null);
        return new ChallengeView(
                challenge.getId(),
                challenge.getOrigin(),
                challenge.getStatus(),
                challenge.getGenerationStatus(),
                challenge.getFailureCode(),
                challenge.getStatusUpdatedAt(),
                challenge.getPurpose(),
                challenge.isTransferChallenge(),
                bodyVisible ? challenge.getTitle() : null,
                challenge.getDifficulty(),
                bodyVisible ? challenge.getEstimatedMinutes() : null,
                bodyVisible ? challenge.getScenario() : null,
                bodyVisible ? challenge.getPrompt() : null,
                bodyVisible ? challenge.getConstraints() : null,
                skillsOf(challenge, refs),
                challenge.getTransferTargets(),
                answerRevealed,
                answerRevealed ? challenge.getExpectedConcepts() : null,
                answerRevealed ? rubricViews(challenge.getRubric()) : null,
                answerRevealed ? challenge.getCommonMistakes() : null,
                null,
                activeAttemptId,
                challenge.getCreatedAt());
    }

    /** 접근 가능한 challenge. 없거나 타인 소유면 404 (docs/05 §10 머리말). */
    public Challenge require(UUID userId, UUID challengeId) {
        return challengeRepository
                .findAccessible(userId, challengeId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.RESOURCE_NOT_FOUND, "challenge not found"));
    }

    /**
     * Today 제안용 PRACTICE 후보 (docs/06 §5.3 1번, BL-TDY-14): {@code VALIDATED}, 접근 가능, 최근 {@code
     * recentDays} plan-day 안 시도 제외, 해결한 challenge 제외.
     *
     * @param recentSince {@code planDayStart(today − (recentDays − 1))}
     */
    public List<ChallengeCandidate> practiceCandidates(UUID userId, Instant recentSince) {
        List<Challenge> challenges =
                challengeRepository.findValidatedByPurpose(userId, ChallengePurpose.PRACTICE);
        if (challenges.isEmpty()) {
            return List.of();
        }
        Set<UUID> excluded =
                new HashSet<>(
                        attemptRepository.findRecentlyAttemptedChallengeIds(userId, recentSince));
        excluded.addAll(attemptRepository.findSolvedChallengeIds(userId));
        Map<UUID, SkillRef> refs = skillRefs(challenges);
        List<ChallengeCandidate> candidates = new ArrayList<>();
        for (Challenge challenge : challenges) {
            if (excluded.contains(challenge.getId())) {
                continue;
            }
            Integer estimated = challenge.getEstimatedMinutes();
            if (estimated == null) {
                continue;
            }
            candidates.add(
                    new ChallengeCandidate(
                            challenge.getId(),
                            challenge.getSeedKey(),
                            challenge.getTitle() == null ? "" : challenge.getTitle(),
                            challenge.getScenario(),
                            challenge.getDifficulty(),
                            estimated,
                            skillCodes(challenge, refs)));
        }
        return List.copyOf(candidates);
    }

    /**
     * 온보딩 진단 후보 (docs/05 §4.2 3단계, BL-TRN-13): {@code VALIDATED}, {@code DIAGNOSTIC}, 공용 seed만. 이미
     * attempt가 있는 challenge는 제외한다(2단계).
     */
    public List<DiagnosticCandidate> diagnosticCandidates(UUID userId) {
        List<Challenge> challenges =
                challengeRepository
                        .findValidatedByPurpose(userId, ChallengePurpose.DIAGNOSTIC)
                        .stream()
                        .filter(challenge -> challenge.getOwnerUserId() == null)
                        .toList();
        if (challenges.isEmpty()) {
            return List.of();
        }
        List<DiagnosticCandidate> candidates = new ArrayList<>();
        for (Challenge challenge : challenges) {
            candidates.add(
                    new DiagnosticCandidate(
                            challenge.getId(),
                            challenge.getSeedKey(),
                            challenge.getTitle() == null ? "" : challenge.getTitle(),
                            challenge.getDifficulty(),
                            challenge.getEstimatedMinutes(),
                            challenge.getSkillIds()));
        }
        return List.copyOf(candidates);
    }

    /**
     * 진단 제안에서 제외할 challenge (docs/05 §4.2 2단계). **진단을 실제로 받은 것만** 제외한다 — 제출했거나 평가까지 끝난 attempt다.
     * {@code STARTED}(시작만 함)·{@code ABANDONED}(제출 없이 그만둠)는 진단 결과가 없으므로 제외하지 않는다.
     */
    public Set<UUID> diagnosedChallengeIds(UUID userId, Set<UUID> challengeIds) {
        if (challengeIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(attemptRepository.findDiagnosedChallengeIds(userId, challengeIds));
    }

    static List<RubricItemView> rubricViews(List<ChallengeRubricItem> rubric) {
        return rubric.stream()
                .map(
                        item ->
                                new RubricItemView(
                                        item.id(), item.criterion(), item.weightBp(), item.axis()))
                .toList();
    }

    private Map<UUID, ChallengeAttempt> lastAttempts(UUID userId, List<Challenge> page) {
        if (page.isEmpty()) {
            return Map.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        page.forEach(challenge -> ids.add(challenge.getId()));
        Map<UUID, ChallengeAttempt> latest = new HashMap<>();
        for (ChallengeAttempt attempt : attemptRepository.findForChallenges(userId, ids)) {
            latest.putIfAbsent(attempt.getChallengeId(), attempt);
        }
        return latest;
    }

    private Map<UUID, SkillRef> skillRefs(List<Challenge> challenges) {
        Set<UUID> ids = new LinkedHashSet<>();
        challenges.forEach(challenge -> ids.addAll(challenge.getSkillIds()));
        return skillCatalogQueryService.findRefs(ids);
    }

    private static List<SkillRef> skillsOf(Challenge challenge, Map<UUID, SkillRef> refs) {
        return challenge.getSkillIds().stream()
                .map(refs::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SkillRef::code))
                .toList();
    }

    private static List<String> skillCodes(Challenge challenge, Map<UUID, SkillRef> refs) {
        return skillsOf(challenge, refs).stream().map(SkillRef::code).toList();
    }

    /**
     * 러버덕 {@code CHALLENGE} 대상 (docs/05 §9.5 표): {@code targetId}는 본인 attempt id다. 없거나 타인 것이면
     * empty. skill은 challenge skill 중 code ASC 첫 번째다.
     */
    public Optional<AttemptTarget> findAttemptTarget(UUID userId, UUID attemptId) {
        return attemptRepository
                .findByIdAndUserId(attemptId, userId)
                .flatMap(
                        attempt ->
                                challengeRepository
                                        .findAccessible(userId, attempt.getChallengeId())
                                        .map(challenge -> attemptTarget(attempt, challenge)));
    }

    private AttemptTarget attemptTarget(ChallengeAttempt attempt, Challenge challenge) {
        Map<UUID, SkillRef> refs = skillRefs(List.of(challenge));
        List<SkillRef> skills = skillsOf(challenge, refs);
        String title = challenge.getTitle() == null ? "" : challenge.getTitle();
        String prompt = challenge.getPrompt() == null ? "" : challenge.getPrompt();
        return new AttemptTarget(
                attempt.getId(),
                challenge.getId(),
                title,
                (title + "\n" + prompt).strip(),
                skills.isEmpty() ? null : skills.getFirst().id());
    }

    /** 활성 attempt id (docs/05 §10.5 중복 시작 검사). */
    public Optional<UUID> findActiveAttemptId(UUID userId, UUID challengeId) {
        return attemptRepository.findActive(userId, challengeId, Limit.of(1)).stream()
                .findFirst()
                .map(ChallengeAttempt::getId);
    }

    /**
     * Today 제안 후보 (docs/06 §5.3).
     *
     * @param skillCodes 이 challenge가 다루는 활성 skill code (ASC)
     */
    public record ChallengeCandidate(
            UUID id,
            @Nullable String seedKey,
            String title,
            @Nullable String scenario,
            int difficulty,
            int estimatedMinutes,
            List<String> skillCodes) {

        public ChallengeCandidate {
            skillCodes = List.copyOf(skillCodes);
        }
    }

    /**
     * 러버덕 {@code CHALLENGE} 대상 (docs/05 §9.5).
     *
     * @param summary docs/17 §3.11 {@code targetSummary} (challenge title + prompt)
     * @param skillId 유도할 skill. challenge skill이 없으면 null
     */
    public record AttemptTarget(
            UUID attemptId,
            UUID challengeId,
            String title,
            String summary,
            @Nullable UUID skillId) {}

    /** 진단 후보 (docs/05 §4.2). */
    public record DiagnosticCandidate(
            UUID id,
            @Nullable String seedKey,
            String title,
            int difficulty,
            @Nullable Integer estimatedMinutes,
            Set<UUID> skillIds) {

        public DiagnosticCandidate {
            skillIds = Set.copyOf(skillIds);
        }
    }
}
