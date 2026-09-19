package com.devpilot.today.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.integration.ai.api.AiStatus;
import com.devpilot.integration.ai.budget.AiBudgetGuard;
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
 * 제안 분기(docs/06 §5.3)의 AI 의존 입력: AI 사용 가능 여부와 선택 가능한 reading 후보(BL-TDY-16). challenge 후보는 training
 * 모듈이 붙을 때 이 클래스에 더한다. 호출자 트랜잭션에서 읽고 AI를 호출하지 않는다.
 *
 * <pre>
 * aiAvailable = aiStatus ∉ {DISABLED, BALANCE_EXHAUSTED}
 * reading 후보 = 은퇴하지 않은 reading 중
 *   - 사용자가 COMPLETED한 READ_CODE 과제의 reading key가 아니고
 *   - 최근 14 plan-day(today − 13 ~ today) 안에 제안된 적이 없는 것 (오늘 plan의 PLANNED 과제는 재생성 때 지워지므로 제외)
 *   정렬 key ASC. skill별 필터는 {@link ProposalOptions#readingsFor(String)}
 * </pre>
 */
@Component
class ProposalOptionCollector {

    /** 최근 14 plan-day: {@code [today − 13, today]}. */
    private static final int RECENT_PROPOSAL_DAYS = 14;

    private final AiBudgetGuard aiBudgetGuard;
    private final CuratedReadingRegistry curatedReadingRegistry;
    private final LearningTaskRepository learningTaskRepository;
    private final ChallengeQueryService challengeQueryService;
    private final int challengeExclusionDays;

    ProposalOptionCollector(
            AiBudgetGuard aiBudgetGuard,
            CuratedReadingRegistry curatedReadingRegistry,
            LearningTaskRepository learningTaskRepository,
            ChallengeQueryService challengeQueryService,
            DevPilotProperties properties) {
        this.aiBudgetGuard = aiBudgetGuard;
        this.curatedReadingRegistry = curatedReadingRegistry;
        this.learningTaskRepository = learningTaskRepository;
        this.challengeQueryService = challengeQueryService;
        this.challengeExclusionDays = properties.planner().challengeRepeatExclusionDays();
    }

    ProposalOptions collect(UUID userId, LocalDate today, ZoneId zone, int dayStartHour) {
        AiStatus status = aiBudgetGuard.status();
        boolean aiAvailable = status != AiStatus.DISABLED && status != AiStatus.BALANCE_EXHAUSTED;
        if (!aiAvailable) {
            return new ProposalOptions(false, List.of(), List.of());
        }
        Set<String> excluded =
                new HashSet<>(learningTaskRepository.findCompletedReadingKeys(userId));
        excluded.addAll(
                learningTaskRepository.findProposedReadingKeys(
                        userId, today.minusDays(RECENT_PROPOSAL_DAYS - 1L), today));
        List<CuratedReading> readings =
                curatedReadingRegistry.active().stream()
                        .filter(reading -> !excluded.contains(reading.key()))
                        .sorted(Comparator.comparing(CuratedReading::key))
                        .toList();
        Instant recentSince =
                PlanDayCalculator.planDayStart(
                        today.minusDays(challengeExclusionDays - 1L), zone, dayStartHour);
        return new ProposalOptions(
                true, readings, challengeQueryService.practiceCandidates(userId, recentSince));
    }

    /** 제안 입력. {@code readings}는 key ASC. */
    record ProposalOptions(
            boolean aiAvailable,
            List<CuratedReading> readings,
            List<ChallengeCandidate> challenges) {

        ProposalOptions {
            readings = List.copyOf(readings);
            challenges = List.copyOf(challenges);
        }

        /** 해당 skill code를 가진 reading 후보 (key ASC). */
        List<ReadingOption> readingsFor(String skillCode) {
            return readings.stream()
                    .filter(reading -> reading.skillCodes().contains(skillCode))
                    .map(ProposalOptions::option)
                    .toList();
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
