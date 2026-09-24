package com.devpilot.today.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.integration.ai.api.AiStatus;
import com.devpilot.integration.ai.budget.AiBudgetGuard;
import com.devpilot.today.domain.ConceptReading;
import com.devpilot.today.domain.ConceptReadingSelection;
import com.devpilot.today.domain.CuratedReading;
import com.devpilot.today.domain.LearningTask;
import com.devpilot.today.domain.Lesson;
import com.devpilot.today.domain.RedoTaskPolicy;
import com.devpilot.today.domain.RedoTaskPolicy.RedoAttempt;
import com.devpilot.today.domain.RedoTaskPolicy.RedoCandidate;
import com.devpilot.today.domain.RedoTaskPolicy.RedoOrigin;
import com.devpilot.today.domain.RedoTaskPolicy.RedoSettings;
import com.devpilot.today.domain.TaskProposalPolicy.ChallengeOption;
import com.devpilot.today.domain.TaskProposalPolicy.ReadingOption;
import com.devpilot.today.infrastructure.LearningTaskRepository;
import com.devpilot.training.application.ChallengeQueryService;
import com.devpilot.training.application.ChallengeQueryService.ChallengeCandidate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 제안 분기(docs/06 §5.3)의 후보 입력: AI 사용 가능 여부, 선택 가능한 코드 읽기(BL-TDY-16), 개념 읽기(docs/19 §3.13). 호출자
 * 트랜잭션에서 읽고 AI를 호출하지 않는다.
 *
 * <pre>
 * aiAvailable = aiStatus ∉ {DISABLED, BALANCE_EXHAUSTED}
 * 제외 key = COMPLETED한 과제의 reading key
 *          + 최근 14 plan-day(today − 13 ~ today) 안에 제안된 key
 *            (오늘 plan의 PLANNED 과제는 재생성 때 지워지므로 제외)
 * 코드 읽기 후보 = 은퇴하지 않은 reading − 제외 key         (AI가 없으면 비운다 — 완료 조건이 러버덕이다)
 * 개념 읽기 후보 = 은퇴하지 않은 개념 읽기 − 제외 key        (AI 상태와 무관하다 — AI를 쓰지 않는다)
 * </pre>
 *
 * key는 한 namespace이므로({@code READ.*} 코드, {@code DOC.*} 문서) 제외 key 집합을 두 종류가 같이 쓴다. skill별 필터는
 * {@link ProposalOptions}가 한다.
 */
@Component
class ProposalOptionCollector {

    private final AiBudgetGuard aiBudgetGuard;
    private final CuratedReadingRegistry curatedReadingRegistry;
    private final ConceptReadingRegistry conceptReadingRegistry;
    private final LessonRegistry lessonRegistry;
    private final LearningTaskRepository learningTaskRepository;
    private final ChallengeQueryService challengeQueryService;
    private final RedoTaskPolicy redoTaskPolicy;
    private final int challengeExclusionDays;
    private final long redoOriginLookbackDays;

    ProposalOptionCollector(
            AiBudgetGuard aiBudgetGuard,
            CuratedReadingRegistry curatedReadingRegistry,
            ConceptReadingRegistry conceptReadingRegistry,
            LessonRegistry lessonRegistry,
            LearningTaskRepository learningTaskRepository,
            ChallengeQueryService challengeQueryService,
            DevPilotProperties properties) {
        this.aiBudgetGuard = aiBudgetGuard;
        this.curatedReadingRegistry = curatedReadingRegistry;
        this.conceptReadingRegistry = conceptReadingRegistry;
        this.lessonRegistry = lessonRegistry;
        this.learningTaskRepository = learningTaskRepository;
        this.challengeQueryService = challengeQueryService;
        DevPilotProperties.Redo redo = properties.planner().redo();
        this.redoTaskPolicy =
                new RedoTaskPolicy(
                        new RedoSettings(
                                redo.minDaysAfter(), redo.maxDaysAfter(), redo.maxAttempts()));
        this.challengeExclusionDays = properties.planner().challengeRepeatExclusionDays();
        // 실패한 재현이 창을 뒤로 미루므로(RE-2) 원본은 그보다 훨씬 오래된 것일 수 있다.
        // 시도는 max-attempts로 막혀 있어 사슬 길이가 정해진다: 원본 + 시도마다 최대 max-days-after.
        this.redoOriginLookbackDays = (long) redo.maxDaysAfter() * (redo.maxAttempts() + 1) + 1;
    }

    ProposalOptions collect(
            UUID userId,
            LocalDate today,
            ZoneId zone,
            int dayStartHour,
            Map<String, UUID> skillIdsByCode) {
        AiStatus status = aiBudgetGuard.status();
        boolean aiAvailable = status != AiStatus.DISABLED && status != AiStatus.BALANCE_EXHAUSTED;
        // 재현 과제는 AI를 부르지 않으므로 AI 상태와 무관하게 고른다 (docs/06 §5.10 RE-8)
        Map<String, RedoCandidate> redos =
                redoCandidates(userId, today, zone, dayStartHour, skillIdsByCode);
        Set<String> excluded = excludedReadingKeys(userId, today);
        List<ConceptReading> conceptReadings =
                conceptReadingRegistry.active().stream()
                        .filter(reading -> !excluded.contains(reading.key()))
                        .sorted(Comparator.comparing(ConceptReading::key))
                        .toList();
        if (!aiAvailable) {
            return new ProposalOptions(
                    false, List.of(), List.of(), conceptReadings, lessonRegistry, redos);
        }
        List<CuratedReading> readings =
                curatedReadingRegistry.active().stream()
                        .filter(reading -> !excluded.contains(reading.key()))
                        .sorted(Comparator.comparing(CuratedReading::key))
                        .toList();
        Instant recentSince =
                PlanDayCalculator.planDayStart(
                        today.minusDays(challengeExclusionDays - 1L), zone, dayStartHour);
        return new ProposalOptions(
                true,
                readings,
                challengeQueryService.practiceCandidates(userId, recentSince),
                conceptReadings,
                lessonRegistry,
                redos);
    }

    /**
     * 오늘 걸 재현 후보 (docs/06 §5.10 "제안 절차"). 저장소에서 읽어 plan-day로 바꾼 뒤 순수 규칙에 넘긴다.
     *
     * <p>skill code를 모르는 과제(트랙에서 빠진 skill 등)는 넘기지 않는다 — 후보 skill과 맞출 수 없기 때문이다.
     */
    private Map<String, RedoCandidate> redoCandidates(
            UUID userId,
            LocalDate today,
            ZoneId zone,
            int dayStartHour,
            Map<String, UUID> skillIdsByCode) {
        Instant from =
                PlanDayCalculator.planDayStart(
                        today.minusDays(redoOriginLookbackDays), zone, dayStartHour);
        Map<UUID, String> codeBySkillId = new HashMap<>();
        skillIdsByCode.forEach((code, id) -> codeBySkillId.put(id, code));

        List<LearningTask> completed =
                learningTaskRepository.findRedoOriginCandidates(userId, from);
        Map<UUID, Integer> difficulties =
                challengeQueryService.difficultiesByIds(challengeIdsOf(completed));
        List<RedoOrigin> origins = new ArrayList<>();
        for (LearningTask task : completed) {
            Instant completedAt = task.getCompletedAt();
            if (completedAt == null) {
                continue;
            }
            UUID challengeId = task.getChallengeId();
            origins.add(
                    new RedoOrigin(
                            task.getId(),
                            task.getSkillId() == null ? null : codeBySkillId.get(task.getSkillId()),
                            task.getTaskType(),
                            task.getTitle(),
                            task.getDescription(),
                            task.getEstimatedMinutes(),
                            challengeId == null ? null : difficulties.get(challengeId),
                            PlanDayCalculator.planDate(completedAt, zone, dayStartHour)));
        }
        List<RedoAttempt> attempts = new ArrayList<>();
        for (LearningTask redo : learningTaskRepository.findRedoTasks(userId)) {
            Instant completedAt = redo.getCompletedAt();
            attempts.add(
                    new RedoAttempt(
                            redo.getRedoSourceTaskId(),
                            redo.getStatus(),
                            completedAt == null
                                    ? null
                                    : PlanDayCalculator.planDate(completedAt, zone, dayStartHour),
                            redo.getRedoWithoutAi()));
        }
        return redoTaskPolicy.selectBySkill(today, origins, attempts);
    }

    private static Set<UUID> challengeIdsOf(List<LearningTask> tasks) {
        Set<UUID> ids = new HashSet<>();
        for (LearningTask task : tasks) {
            UUID challengeId = task.getChallengeId();
            if (challengeId != null) {
                ids.add(challengeId);
            }
        }
        return ids;
    }

    /** 완료했거나 최근 14 plan-day 안에 제안된 reading key (코드 읽기·개념 읽기 공통, docs/06 §5.3). */
    private Set<String> excludedReadingKeys(UUID userId, LocalDate today) {
        Set<String> excluded =
                new HashSet<>(learningTaskRepository.findCompletedReadingKeys(userId));
        excluded.addAll(
                learningTaskRepository.findProposedReadingKeys(
                        userId, ConceptReadingSelection.recentProposalFrom(today), today));
        return excluded;
    }

    /** 제안 입력. {@code readings}·{@code conceptReadings}는 key ASC. */
    record ProposalOptions(
            boolean aiAvailable,
            List<CuratedReading> readings,
            List<ChallengeCandidate> challenges,
            List<ConceptReading> conceptReadings,
            LessonRegistry lessons,
            Map<String, RedoCandidate> redosBySkillCode) {

        ProposalOptions {
            readings = List.copyOf(readings);
            challenges = List.copyOf(challenges);
            conceptReadings = List.copyOf(conceptReadings);
            redosBySkillCode = Map.copyOf(redosBySkillCode);
        }

        /** 오늘 이 skill에 걸 재현 후보 (docs/06 §5.10). 없으면 null이고 제안은 1번 분기부터 간다. */
        @Nullable RedoCandidate redoFor(String skillCode) {
            return redosBySkillCode.get(skillCode);
        }

        /** 해당 skill code를 가진 reading 후보 (key ASC). */
        List<ReadingOption> readingsFor(String skillCode) {
            return readings.stream()
                    .filter(reading -> reading.skillCodes().contains(skillCode))
                    .map(ProposalOptions::option)
                    .toList();
        }

        /** 해당 skill code를 가진 개념 읽기 후보 (key ASC, docs/06 §5.3). */
        List<ConceptReading> conceptReadingsFor(String skillCode) {
            return ConceptReadingSelection.candidates(conceptReadings, skillCode, Set.of());
        }

        /** 만들기 과제의 설명이 될 노트의 {@code inProject} (ADR-048). 노트가 없으면 null이고, 그때는 일반 문장으로 돌아간다. */
        @Nullable String projectGuideFor(String skillCode) {
            return lessons.findBySkillCode(skillCode).map(Lesson::inProject).orElse(null);
        }

        /** 해당 skill code를 가진 challenge 후보 (docs/06 §5.3 1번). */
        List<ChallengeOption> challengesFor(String skillCode) {
            return challenges.stream()
                    .filter(challenge -> challenge.skillCodes().contains(skillCode))
                    .map(ProposalOptions::option)
                    .toList();
        }

        private static ChallengeOption option(ChallengeCandidate challenge) {
            return new ChallengeOption(
                    challenge.id(),
                    challenge.seedKey(),
                    challenge.title(),
                    challenge.scenario(),
                    challenge.difficulty(),
                    challenge.estimatedMinutes());
        }

        private static ReadingOption option(CuratedReading reading) {
            return new ReadingOption(
                    reading.key(),
                    reading.repo().name(),
                    reading.path(),
                    reading.startLine(),
                    reading.endLine(),
                    reading.estimatedMinutes(),
                    reading.question());
        }
    }
}
