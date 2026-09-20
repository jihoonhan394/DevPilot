package com.devpilot.today.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.integration.ai.api.AiStatus;
import com.devpilot.integration.ai.budget.AiBudgetGuard;
import com.devpilot.today.domain.ConceptReading;
import com.devpilot.today.domain.ConceptReadingSelection;
import com.devpilot.today.domain.CuratedReading;
import com.devpilot.today.domain.TaskProposalPolicy.ChallengeOption;
import com.devpilot.today.domain.TaskProposalPolicy.ReadingOption;
import com.devpilot.today.infrastructure.LearningTaskRepository;
import com.devpilot.training.application.ChallengeQueryService;
import com.devpilot.training.application.ChallengeQueryService.ChallengeCandidate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
    private final LearningTaskRepository learningTaskRepository;
    private final ChallengeQueryService challengeQueryService;
    private final int challengeExclusionDays;

    ProposalOptionCollector(
            AiBudgetGuard aiBudgetGuard,
            CuratedReadingRegistry curatedReadingRegistry,
            ConceptReadingRegistry conceptReadingRegistry,
            LearningTaskRepository learningTaskRepository,
            ChallengeQueryService challengeQueryService,
            DevPilotProperties properties) {
        this.aiBudgetGuard = aiBudgetGuard;
        this.curatedReadingRegistry = curatedReadingRegistry;
        this.conceptReadingRegistry = conceptReadingRegistry;
        this.learningTaskRepository = learningTaskRepository;
        this.challengeQueryService = challengeQueryService;
        this.challengeExclusionDays = properties.planner().challengeRepeatExclusionDays();
    }

    ProposalOptions collect(UUID userId, LocalDate today, ZoneId zone, int dayStartHour) {
        AiStatus status = aiBudgetGuard.status();
        boolean aiAvailable = status != AiStatus.DISABLED && status != AiStatus.BALANCE_EXHAUSTED;
        Set<String> excluded = excludedReadingKeys(userId, today);
        List<ConceptReading> conceptReadings =
                conceptReadingRegistry.active().stream()
                        .filter(reading -> !excluded.contains(reading.key()))
                        .sorted(Comparator.comparing(ConceptReading::key))
                        .toList();
        if (!aiAvailable) {
            return new ProposalOptions(false, List.of(), List.of(), conceptReadings);
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
                conceptReadings);
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
            List<ConceptReading> conceptReadings) {

        ProposalOptions {
            readings = List.copyOf(readings);
            challenges = List.copyOf(challenges);
            conceptReadings = List.copyOf(conceptReadings);
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
