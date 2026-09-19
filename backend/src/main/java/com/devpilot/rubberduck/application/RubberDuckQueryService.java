package com.devpilot.rubberduck.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.web.AiMeta;
import com.devpilot.common.web.AxisLevels;
import com.devpilot.rubberduck.application.RubberDuckAiSupport.TargetContext;
import com.devpilot.rubberduck.application.RubberDuckTargetResolver.ResolvedTarget;
import com.devpilot.rubberduck.application.RubberDuckViews.RubberDuckGapView;
import com.devpilot.rubberduck.application.RubberDuckViews.RubberDuckSessionView;
import com.devpilot.rubberduck.application.RubberDuckViews.RubberDuckSummaryView;
import com.devpilot.rubberduck.application.RubberDuckViews.RubberDuckTurnView;
import com.devpilot.rubberduck.domain.RubberDuckPolicy;
import com.devpilot.rubberduck.domain.RubberDuckSession;
import com.devpilot.rubberduck.domain.RubberDuckSummary;
import com.devpilot.rubberduck.domain.RubberDuckTargetType;
import com.devpilot.rubberduck.domain.RubberDuckTurn;
import com.devpilot.rubberduck.infrastructure.RubberDuckSessionRepository;
import com.devpilot.rubberduck.infrastructure.RubberDuckTurnRepository;
import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillDetailView;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.skill.application.UserSkillStateQueryService;
import com.devpilot.skill.application.UserSkillStateQueryService.PlanningState;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 러버덕 조회 (docs/05 §9.10)와 응답 DTO 조립. 세션이 본인 것이 아니면 404 {@code RESOURCE_NOT_FOUND}다(docs/05 §1.1).
 * AI가 불가한 상태에서도 조회는 된다.
 */
@Service
@Transactional(readOnly = true)
public class RubberDuckQueryService {

    private static final String NONE = "(없음)";

    private final RubberDuckSessionRepository sessions;
    private final RubberDuckTurnRepository turns;
    private final RubberDuckTargetResolver targetResolver;
    private final RubberDuckAiSupport aiSupport;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final UserSkillStateQueryService userSkillStateQueryService;
    private final RubberDuckPolicy policy;

    RubberDuckQueryService(
            RubberDuckSessionRepository sessions,
            RubberDuckTurnRepository turns,
            RubberDuckTargetResolver targetResolver,
            RubberDuckAiSupport aiSupport,
            SkillCatalogQueryService skillCatalogQueryService,
            UserSkillStateQueryService userSkillStateQueryService,
            DevPilotProperties properties) {
        this.sessions = sessions;
        this.turns = turns;
        this.targetResolver = targetResolver;
        this.aiSupport = aiSupport;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.userSkillStateQueryService = userSkillStateQueryService;
        this.policy = RubberDuckRuleSettings.policy(properties);
    }

    /** {@code GET /rubber-duck/{sessionId}} (docs/05 §9.10). 턴 전체를 {@code turn_no} ASC로 담는다. */
    public RubberDuckSessionView get(UUID userId, UUID sessionId) {
        RubberDuckSession session = require(userId, sessionId);
        return view(session, turnsOf(session), true);
    }

    /** 소유 검사 (docs/05 §1.1): 타 사용자 것과 없는 것은 같은 404다. */
    RubberDuckSession require(UUID userId, UUID sessionId) {
        return sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        ErrorCode.RESOURCE_NOT_FOUND,
                                        "rubber duck session not found"));
    }

    List<RubberDuckTurn> turnsOf(RubberDuckSession session) {
        return turns.findBySessionIdOrderByTurnNoAsc(session.getId());
    }

    RubberDuckPolicy policy() {
        return policy;
    }

    /** 대상 확인 (docs/05 §9.6 2단계). 없으면 400 {@code REFERENCE_NOT_FOUND}. */
    ResolvedTarget resolveTarget(
            UUID userId,
            RubberDuckTargetType targetType,
            @Nullable UUID targetId,
            @Nullable String conceptKey) {
        return targetResolver.require(userId, targetType, targetId, conceptKey);
    }

    /** AI 입력의 공통 부분 (docs/17 §3.11). */
    TargetContext aiContext(RubberDuckSession session, ResolvedTarget target) {
        return new TargetContext(
                session.getUserId(),
                session.getTargetType().name(),
                target.summary(),
                skillSummary(session.getUserId(), session.getSkillId()));
    }

    ResolvedTarget targetOf(RubberDuckSession session) {
        return targetResolver.forSession(session);
    }

    /** {@code skillCode} 검사·유도 (docs/05 §9.6 3단계). */
    @Nullable UUID resolveSkillId(@Nullable String skillCode, ResolvedTarget target) {
        return targetResolver.resolveSkillId(skillCode, target);
    }

    /** 시작 시점의 진행 중 학습 세션 (docs/05 §9.6 5단계). */
    @Nullable UUID inProgressLearningSessionId(UUID userId) {
        return targetResolver.inProgressLearningSessionId(userId);
    }

    List<String> availableSkillCodes(@Nullable UUID skillId) {
        return targetResolver.availableSkillCodes(skillId);
    }

    Set<String> knownSkillCodes() {
        return skillCatalogQueryService.activeSkillDetails().values().stream()
                .map(SkillDetailView::code)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** {@code skill.code}·이름·planning level 4축 (docs/17 §3.11). skill이 없으면 {@code (없음)}. */
    String skillSummary(UUID userId, @Nullable UUID skillId) {
        if (skillId == null) {
            return NONE;
        }
        SkillRef ref = skillCatalogQueryService.findRefs(List.of(skillId)).get(skillId);
        if (ref == null) {
            return NONE;
        }
        PlanningState state = userSkillStateQueryService.planningStates(userId).get(skillId);
        AxisLevels levels = state == null ? AxisLevels.ZERO : state.planning();
        return ref.code()
                + " ("
                + ref.name()
                + ") K"
                + levels.knowledge()
                + " I"
                + levels.implementation()
                + " E"
                + levels.explanation()
                + " D"
                + levels.debugging();
    }

    /** 세션 응답 (docs/05 §9.5 {@code RubberDuckSessionView}). */
    RubberDuckSessionView view(
            RubberDuckSession session, List<RubberDuckTurn> sessionTurns, boolean includeTurns) {
        ResolvedTarget target = targetResolver.forSession(session);
        UUID skillId = session.getSkillId();
        SkillRef skill =
                skillId == null
                        ? null
                        : skillCatalogQueryService.findRefs(List.of(skillId)).get(skillId);
        List<RubberDuckTurnView> turnViews = includeTurns ? turnViews(sessionTurns) : List.of();
        return new RubberDuckSessionView(
                session.getId(),
                session.getTargetType(),
                session.getTargetId(),
                session.getConceptKey(),
                target.readingKey(),
                target.title(),
                skill,
                session.getStatus(),
                session.getTurnCount(),
                policy.maxTurns(),
                suggestHint(sessionTurns),
                turnViews,
                summaryView(session.getSummary()),
                null,
                session.getLearningSessionId(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getVersion());
    }

    /** RD-3: 마지막 턴들의 {@code learner_stuck}로 판정한다. */
    boolean suggestHint(List<RubberDuckTurn> sessionTurns) {
        return policy.suggestHint(
                sessionTurns.stream().map(RubberDuckTurn::isLearnerStuck).toList());
    }

    static @Nullable RubberDuckSummaryView summaryView(@Nullable RubberDuckSummary summary) {
        if (summary == null) {
            return null;
        }
        return new RubberDuckSummaryView(
                gapViews(summary), summary.confirmed(), summary.overallNote());
    }

    static List<RubberDuckGapView> gapViews(RubberDuckSummary summary) {
        return summary.gaps().stream()
                .map(
                        gap ->
                                new RubberDuckGapView(
                                        gap.conceptKey(),
                                        gap.whatWasMissed(),
                                        gap.whyItMatters(),
                                        gap.reviewQuestion(),
                                        gap.reviewItemId()))
                .toList();
    }

    private List<RubberDuckTurnView> turnViews(List<RubberDuckTurn> sessionTurns) {
        List<UUID> aiCallIds =
                sessionTurns.stream()
                        .map(RubberDuckTurn::getAiCallId)
                        .filter(Objects::nonNull)
                        .toList();
        Map<UUID, AiMeta> metas = aiSupport.metas(aiCallIds);
        List<RubberDuckTurnView> views = new ArrayList<>();
        for (RubberDuckTurn turn : sessionTurns) {
            UUID aiCallId = turn.getAiCallId();
            views.add(
                    new RubberDuckTurnView(
                            turn.getTurnNo(),
                            turn.getUserText(),
                            turn.getAiQuestion(),
                            turn.isLearnerStuck(),
                            turn.getCreatedAt(),
                            aiCallId == null ? null : metas.get(aiCallId)));
        }
        return views;
    }
}
