package com.devpilot.today.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.learning.application.LearningSessionQueryService;
import com.devpilot.plan.application.PlanQueryService;
import com.devpilot.plan.application.PlanView;
import com.devpilot.plan.application.StudyBudgetService;
import com.devpilot.plan.domain.RiskLevel;
import com.devpilot.review.application.ReviewQueryService;
import com.devpilot.review.application.ReviewQueryService.DueSummary;
import com.devpilot.today.application.PlannerInputCollector.Context;
import com.devpilot.today.application.PlannerInputCollector.Inputs;
import com.devpilot.today.domain.EnergyLevel;
import com.devpilot.today.domain.LearningTask;
import com.devpilot.today.domain.PlannerModifier;
import com.devpilot.today.domain.PlannerScoring;
import com.devpilot.today.domain.PlannerScoring.CandidateInput;
import com.devpilot.today.domain.PlannerScoring.Factors;
import com.devpilot.today.domain.PlannerScoring.MilestoneContext;
import com.devpilot.today.domain.PlannerScoring.MilestoneSpan;
import com.devpilot.today.domain.PlannerScoring.ScoreInput;
import com.devpilot.today.domain.PlannerScoring.ScoredCandidate;
import com.devpilot.today.domain.PlannerScoring.SkillProfile;
import com.devpilot.today.domain.PlannerScoring.SkillTarget;
import com.devpilot.today.domain.ReasonCode;
import com.devpilot.today.domain.ReasonTemplates;
import com.devpilot.today.domain.ReasonTemplates.ReasonInput;
import com.devpilot.today.domain.ReasonTemplates.ReasonParams;
import com.devpilot.today.domain.ScoreBreakdown;
import com.devpilot.today.domain.TaskProposalPolicy;
import com.devpilot.today.domain.TaskProposalPolicy.ChallengeOption;
import com.devpilot.today.domain.TaskProposalPolicy.Proposal;
import com.devpilot.today.domain.TaskProposalPolicy.ProposalInput;
import com.devpilot.today.domain.TaskProposalPolicy.ReadingOption;
import com.devpilot.today.domain.TaskProposalPolicy.SkillContext;
import com.devpilot.today.domain.TimeAllocator;
import com.devpilot.today.domain.TimeAllocator.Allocation;
import com.devpilot.today.domain.TimeAllocator.ExtraCandidate;
import com.devpilot.today.domain.TimeAllocator.FittedExtra;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Today 계획 계산 (docs/05 §8.2 3단계, docs/06 §5). 입력(활성 plan, 요청 시점 risk, 복귀 모드, due review, planning
 * level, 집중 skill, leech, 최근 복습 실패, 어제·그제 main, ACTIVE 사이드 프로젝트)을 모아 규칙 클래스를 순서대로 돌린다: 후보 → 제안 →
 * 점수·순위 → 시간 배분·과제 조정 → reason. 저장은 {@link TodayPlanService}가 한다. AI를 호출하지 않는다.
 *
 * <p>AI 사용 가능 여부와 challenge·reading 후보는 {@link ProposalOptionCollector}가 모은다(BL-TDY-14·BL-TDY-16).
 * 규칙 입력은 {@link PlannerInputCollector}가 모은다.
 */
@Component
public class DailyPlanComposer {

    private final PlanQueryService planQueryService;
    private final StudyBudgetService studyBudgetService;
    private final LearningSessionQueryService learningSessionQueryService;
    private final ReviewQueryService reviewQueryService;
    private final PlannerInputCollector inputCollector;
    private final ProposalOptionCollector proposalOptionCollector;
    private final PlannerScoring scoring;
    private final TaskProposalPolicy proposalPolicy = new TaskProposalPolicy();
    private final TimeAllocator timeAllocator;
    private final ReasonTemplates reasonTemplates;

    DailyPlanComposer(
            PlanQueryService planQueryService,
            StudyBudgetService studyBudgetService,
            LearningSessionQueryService learningSessionQueryService,
            ReviewQueryService reviewQueryService,
            PlannerInputCollector inputCollector,
            ProposalOptionCollector proposalOptionCollector,
            DevPilotProperties properties) {
        this.planQueryService = planQueryService;
        this.studyBudgetService = studyBudgetService;
        this.learningSessionQueryService = learningSessionQueryService;
        this.reviewQueryService = reviewQueryService;
        this.inputCollector = inputCollector;
        this.proposalOptionCollector = proposalOptionCollector;
        this.scoring = new PlannerScoring(TodayRuleSettings.planner(properties));
        this.timeAllocator = new TimeAllocator(TodayRuleSettings.timeAllocation(properties));
        this.reasonTemplates = new ReasonTemplates(TodayRuleSettings.reasons(properties));
    }

    /** 오늘 계획을 계산한다. 호출자 트랜잭션에서 읽는다. 활성 plan이 없으면 404 {@code PLAN_NOT_FOUND}. */
    public Composition compose(
            CurrentUser user, LocalDate today, int availableMinutes, EnergyLevel energy) {
        UUID userId = user.userId();
        PlanView plan = planQueryService.getActive(userId);
        RiskLevel risk = studyBudgetService.currentRisk(userId, today).orElse(null);
        boolean comebackMode = learningSessionQueryService.isComebackMode(userId, today);
        DueSummary due =
                reviewQueryService.dueSummary(userId, today, user.zoneId(), user.dayStartHour());
        Allocation allocation =
                timeAllocator.allocate(availableMinutes, due.totalDue(), comebackMode);
        Inputs inputs =
                inputCollector.collect(
                        userId,
                        today,
                        plan,
                        new Context(risk == null ? RiskLevel.LOW : risk, energy, comebackMode),
                        due);
        ProposalOptionCollector.ProposalOptions options =
                proposalOptionCollector.collect(userId, today, user.zoneId(), user.dayStartHour());
        Chosen chosen = chooseTasks(inputs, options, allocation);
        return new Composition(
                plan.id(), risk, comebackMode, allocation, chosen.main(), chosen.extras());
    }

    /** 1위 후보로 main을 만들고, 남는 예산이 크면 다음 후보로 추가 과제를 만든다 (docs/06 §5.6). */
    private Chosen chooseTasks(
            Inputs inputs, ProposalOptionCollector.ProposalOptions options, Allocation allocation) {
        // 지금 단계는 날짜가 아니라 진행으로 정한다 (ADR-044).
        Set<String> currentCodes =
                new HashSet<>(
                        PlannerScoring.currentMilestone(
                                        inputs.milestones(), inputs.targets(), inputs.profiles())
                                .map(MilestoneSpan::skillCodes)
                                .orElse(Set.of()));
        Set<String> nextCodes =
                PlannerScoring.nextMilestone(
                                inputs.milestones(), inputs.targets(), inputs.profiles())
                        .map(MilestoneSpan::skillCodes)
                        .orElse(Set.of());
        List<String> candidates =
                scoring.selectCandidates(
                        new CandidateInput(
                                inputs.context().risk(),
                                currentCodes,
                                nextCodes,
                                inputs.overdueByCode().keySet(),
                                inputs.targets(),
                                inputs.profiles()));
        if (candidates.isEmpty()) {
            return new Chosen(null, List.of());
        }
        List<Evaluated> evaluated = new ArrayList<>();
        for (String code : candidates) {
            evaluated.add(evaluate(code, inputs, options));
        }
        Map<ScoredCandidate, Evaluated> byScore = new HashMap<>();
        evaluated.forEach(candidate -> byScore.put(candidate.scored(), candidate));
        List<Evaluated> ranked =
                scoring.rank(evaluated.stream().map(Evaluated::scored).toList()).stream()
                        .map(byScore::get)
                        .toList();
        Evaluated best = ranked.getFirst();
        Proposal main =
                timeAllocator.fit(
                        best.proposal(),
                        allocation,
                        best.skill(),
                        best.challenges(),
                        best.readings());
        Map<String, Evaluated> bySkillCode = new HashMap<>();
        ranked.forEach(candidate -> bySkillCode.put(candidate.skill().code(), candidate));
        List<ExtraCandidate> extraCandidates =
                ranked.subList(1, ranked.size()).stream().map(Evaluated::asExtraCandidate).toList();
        List<LearningTask.TaskValues> extras = new ArrayList<>();
        int rank = 2;
        for (FittedExtra extra : timeAllocator.fitExtras(allocation, main, extraCandidates)) {
            extras.add(
                    toTask(bySkillCode.get(extra.skill().code()), inputs, extra.proposal(), rank));
            rank++;
        }
        return new Chosen(toTask(best, inputs, main, 1), List.copyOf(extras));
    }

    private Evaluated evaluate(
            String code, Inputs inputs, ProposalOptionCollector.ProposalOptions options) {
        SkillProfile profile = inputs.profiles().get(code);
        SkillTarget target = inputs.targets().get(code);
        MilestoneContext milestone =
                scoring.milestoneContext(
                        inputs.today(),
                        inputs.milestones(),
                        inputs.targets(),
                        inputs.profiles(),
                        code);
        Factors factors =
                scoring.factors(
                        new PlannerScoring.FactorInput(
                                target,
                                profile.planning(),
                                inputs.overdueByCode().get(code),
                                inputs.leechCodes().contains(code),
                                milestone.urgency(),
                                inputs.focusCodes().contains(code),
                                PlannerScoring.prerequisiteReadiness(profile, inputs.profiles())));
        SkillContext skill = skillContext(profile, inputs);
        Proposal proposal =
                proposalPolicy.propose(
                        new ProposalInput(
                                skill,
                                inputs.context().energy(),
                                inputs.context().comebackMode(),
                                options.aiAvailable(),
                                inputs.trackDefaults(),
                                options.challengesFor(code),
                                options.readingsFor(code),
                                options.conceptReadingsFor(code),
                                inputs.sideProject()));
        ScoredCandidate scored =
                scoring.score(
                        new ScoreInput(
                                code,
                                target == null ? null : target.priority(),
                                factors,
                                proposal.taskType(),
                                proposal.estimatedMinutes(),
                                proposal.difficulty(),
                                profile.lastPracticedAt(),
                                inputs.yesterday(),
                                inputs.dayBefore()),
                        new PlannerScoring.Context(
                                inputs.context().risk(),
                                inputs.context().energy(),
                                inputs.context().comebackMode(),
                                inputs.recentMainTaskTypes()));
        return new Evaluated(
                scored,
                proposal,
                milestone,
                skill,
                target,
                options.challengesFor(code),
                options.readingsFor(code));
    }

    /** 조정까지 끝난 제안을 저장할 값으로 바꾼다. {@code rank}는 main 1, 추가 과제 2·3·4다 (docs/04 §5.1). */
    private LearningTask.TaskValues toTask(
            Evaluated chosen, Inputs inputs, Proposal fitted, int rank) {
        ScoredCandidate scored = chosen.scored();
        Factors factors = scored.input().factors();
        String code = scored.input().skillCode();
        Integer overdue = inputs.overdueByCode().get(code);
        MilestoneContext milestone = chosen.milestone();
        List<ReasonCode> reasons =
                reasonTemplates.select(
                        new ReasonInput(
                                ReasonTemplates.contributions(scoring, factors),
                                factors.practicalImportance(),
                                factors.skillGap(),
                                overdue == null ? 0 : overdue,
                                inputs.recentAgainCodes().contains(code),
                                milestone.current() != null,
                                milestone.next() != null,
                                inputs.focusCodes().contains(code),
                                scored.applied(PlannerModifier.RISK_HIGH_MUST),
                                scored.applied(PlannerModifier.CONTINUATION),
                                inputs.context().energy(),
                                inputs.context().comebackMode(),
                                fitted.taskType(),
                                fitted.estimatedMinutes()));
        MilestoneSpan reasonMilestone =
                milestone.current() != null ? milestone.current() : milestone.next();
        SkillTarget target = chosen.target();
        ReasonParams params =
                new ReasonParams(
                        reasonMilestone == null ? null : reasonMilestone.title(),
                        chosen.skill().planning().implementation(),
                        target == null ? null : target.targets().implementation(),
                        overdue,
                        fitted.repoName());
        ScoreBreakdown breakdown =
                new ScoreBreakdown(
                        PlannerScoring.PLANNER_VERSION,
                        factors,
                        scored.baseScore(),
                        scored.modifiers(),
                        scored.finalScore(),
                        rank,
                        params);
        return new LearningTask.TaskValues(
                fitted.taskType(),
                inputs.skillIdsByCode().get(code),
                reasonMilestone == null ? null : reasonMilestone.id(),
                fitted.challengeId(),
                fitted.sideProjectId(),
                fitted.readingKey(),
                fitted.title(),
                fitted.description(),
                fitted.estimatedMinutes(),
                reasons,
                breakdown);
    }

    private static SkillContext skillContext(SkillProfile profile, Inputs inputs) {
        return new SkillContext(
                profile.code(),
                profile.name(),
                profile.description(),
                profile.planning(),
                inputs.focusCodes().contains(profile.code()));
    }

    /**
     * 계산 결과.
     *
     * @param deadlineRisk 요청 시점 risk. 학습 목표가 없으면 null
     * @param main 후보가 없으면 null (REVIEW 과제만 만들 수 있다)
     * @param extras 남는 예산을 채우는 추가 과제 (docs/06 §5.6, sort_order 순서). 없으면 빈 목록
     */
    public record Composition(
            UUID learningPlanId,
            @Nullable RiskLevel deadlineRisk,
            boolean comebackMode,
            Allocation allocation,
            LearningTask.@Nullable TaskValues main,
            List<LearningTask.TaskValues> extras) {

        public Composition {
            extras = List.copyOf(extras);
        }
    }

    /** 오늘 만들 과제. */
    private record Chosen(
            LearningTask.@Nullable TaskValues main, List<LearningTask.TaskValues> extras) {}

    /** 후보 1개의 계산 결과. */
    private record Evaluated(
            ScoredCandidate scored,
            Proposal proposal,
            MilestoneContext milestone,
            SkillContext skill,
            @Nullable SkillTarget target,
            List<ChallengeOption> challenges,
            List<ReadingOption> readings) {

        ExtraCandidate asExtraCandidate() {
            return new ExtraCandidate(skill, proposal, challenges, readings);
        }
    }
}
