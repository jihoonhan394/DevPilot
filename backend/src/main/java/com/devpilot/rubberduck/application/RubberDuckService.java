package com.devpilot.rubberduck.application;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.common.web.AiMeta;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.AiResult;
import com.devpilot.integration.ai.api.output.RubberDuckSummaryOutput;
import com.devpilot.integration.ai.api.output.RubberDuckTurnOutput;
import com.devpilot.rubberduck.application.RubberDuckAiSupport.SummaryInput;
import com.devpilot.rubberduck.application.RubberDuckAiSupport.TargetContext;
import com.devpilot.rubberduck.application.RubberDuckAiSupport.TurnInput;
import com.devpilot.rubberduck.application.RubberDuckSummaryApplier.Applied;
import com.devpilot.rubberduck.application.RubberDuckTargetResolver.ResolvedTarget;
import com.devpilot.rubberduck.application.RubberDuckViews.RubberDuckCompleteResponse;
import com.devpilot.rubberduck.application.RubberDuckViews.RubberDuckSessionView;
import com.devpilot.rubberduck.application.RubberDuckViews.RubberDuckStartResponse;
import com.devpilot.rubberduck.application.RubberDuckViews.RubberDuckTurnResponse;
import com.devpilot.rubberduck.domain.RubberDuckSession;
import com.devpilot.rubberduck.domain.RubberDuckStatus;
import com.devpilot.rubberduck.domain.RubberDuckSummary;
import com.devpilot.rubberduck.domain.RubberDuckTargetType;
import com.devpilot.rubberduck.domain.RubberDuckTurn;
import com.devpilot.rubberduck.infrastructure.RubberDuckSessionRepository;
import com.devpilot.rubberduck.infrastructure.RubberDuckTurnRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 러버덕 세션 (docs/05 §9.6~§9.9, docs/06 §9.5). AI는 트랜잭션 밖에서 부른다(T-2): 턴·정리 모두 {@code tx1(조회·검사) →
 * AiGateway → tx2(저장)}이고, 두 트랜잭션 사이에 세션 {@code version}이 바뀌었으면 409 {@code
 * CONCURRENT_MODIFICATION}이다(docs/03 §5.3).
 *
 * <ul>
 *   <li>시작·중단은 AI를 부르지 않는다 (docs/05 §9.6·§9.9).
 *   <li>턴은 실패하면 아무것도 저장하지 않는다 — 대체 동작이 없다(docs/05 §1.9.4).
 *   <li>정리는 실패해도 세션을 {@code COMPLETED}로 두고 {@code summarySkippedReason}만 돌려준다. 대화 자체가 학습이다.
 * </ul>
 */
@Service
public class RubberDuckService {

    private final RubberDuckSessionRepository sessions;
    private final RubberDuckTurnRepository turns;
    private final RubberDuckAiSupport aiSupport;
    private final RubberDuckSummaryApplier summaryApplier;
    private final RubberDuckQueryService queries;
    private final TransactionTemplate transactions;
    private final Clock clock;

    RubberDuckService(
            RubberDuckSessionRepository sessions,
            RubberDuckTurnRepository turns,
            RubberDuckAiSupport aiSupport,
            RubberDuckSummaryApplier summaryApplier,
            RubberDuckQueryService queries,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.sessions = sessions;
        this.turns = turns;
        this.aiSupport = aiSupport;
        this.summaryApplier = summaryApplier;
        this.queries = queries;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** 세션 시작 (docs/05 §9.6). AI를 부르지 않는다. */
    public RubberDuckStartResponse start(CurrentUser user, StartCommand command) {
        validateShape(command);
        UUID userId = user.userId();
        ResolvedTarget target =
                queries.resolveTarget(
                        userId, command.targetType(), command.targetId(), command.conceptKey());
        UUID skillId = queries.resolveSkillId(command.skillCode(), target);
        return Objects.requireNonNull(
                transactions.execute(status -> startSession(userId, command, target, skillId)));
    }

    /** 설명 제출 → AI 질문 (docs/05 §9.7). 실패하면 턴을 저장하지 않는다. */
    public RubberDuckTurnResponse submitTurn(CurrentUser user, UUID sessionId, String explanation) {
        UUID userId = user.userId();
        String masked = aiSupport.mask(userId, explanation);
        TurnContext context =
                Objects.requireNonNull(
                        transactions.execute(status -> loadForTurn(userId, sessionId, masked)));
        AiResult<RubberDuckTurnOutput> result =
                aiSupport.askQuestion(
                        new TurnInput(context.aiContext(), context.conversation(), masked));
        if (!result.succeeded()) {
            throw result.toFailureException();
        }
        Instant now = clock.instant();
        return Objects.requireNonNull(
                transactions.execute(
                        status -> saveTurn(userId, sessionId, context, masked, result, now)));
    }

    /** 종료·정리 (docs/05 §9.8). 정리 실패는 오류가 아니다. */
    public RubberDuckCompleteResponse complete(CurrentUser user, UUID sessionId) {
        UUID userId = user.userId();
        CompleteContext context =
                Objects.requireNonNull(
                        transactions.execute(status -> loadForComplete(userId, sessionId)));
        if (context.earlyResponse() != null) {
            return context.earlyResponse();
        }
        AiResult<RubberDuckSummaryOutput> result =
                aiSupport.summarize(
                        new SummaryInput(
                                context.requireAiContext(),
                                context.conversation(),
                                context.availableSkillCodes(),
                                queries.knownSkillCodes()));
        Instant now = clock.instant();
        LocalDate planDate = PlanDayCalculator.planDate(now, user.zoneId(), user.dayStartHour());
        return Objects.requireNonNull(
                transactions.execute(
                        status -> saveSummary(userId, sessionId, context, result, planDate, now)));
    }

    /**
     * 방치 세션 정리 (docs/03 §6 {@code StaleRubberDuckJob}). 사용자 단위 트랜잭션이다. 정리 AI를 부르지 않고 복습 카드·학습 이벤트도
     * 만들지 않는다.
     *
     * @return 이번에 {@code ABANDONED}로 바꾼 세션 수
     */
    @Transactional
    public int abandonStale(UUID userId, Instant cutoff) {
        List<RubberDuckSession> stale = sessions.findStaleSessions(userId, cutoff);
        if (stale.isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        stale.forEach(session -> session.abandon(now));
        sessions.flush();
        return stale.size();
    }

    /** 중단 (docs/05 §9.9). AI를 부르지 않고 턴 기록은 남는다. */
    public RubberDuckSessionView abandon(CurrentUser user, UUID sessionId) {
        UUID userId = user.userId();
        return Objects.requireNonNull(
                transactions.execute(
                        status -> {
                            RubberDuckSession session = queries.require(userId, sessionId);
                            session.requireInProgress();
                            session.abandon(clock.instant());
                            sessions.flush();
                            return queries.view(session, queries.turnsOf(session), true);
                        }));
    }

    private RubberDuckStartResponse startSession(
            UUID userId, StartCommand command, ResolvedTarget target, @Nullable UUID skillId) {
        UUID abandonedSessionId = abandonInProgress(userId);
        RubberDuckSession session =
                RubberDuckSession.start(
                        new RubberDuckSession.Values(
                                userId,
                                command.targetType(),
                                target.targetId(),
                                command.targetType() == RubberDuckTargetType.CONCEPT
                                        ? command.conceptKey()
                                        : null,
                                skillId,
                                queries.inProgressLearningSessionId(userId)),
                        clock.instant());
        try {
            sessions.saveAndFlush(session);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION,
                    "another rubber duck session started",
                    exception);
        }
        return new RubberDuckStartResponse(
                queries.view(session, List.of(), true), abandonedSessionId);
    }

    /** 사용자당 진행 중 세션은 1개다(I-18): 이전 세션을 {@code ABANDONED}로 바꾸고 flush한다. */
    private @Nullable UUID abandonInProgress(UUID userId) {
        Optional<RubberDuckSession> current =
                sessions.findByUserIdAndStatus(userId, RubberDuckStatus.IN_PROGRESS);
        if (current.isEmpty()) {
            return null;
        }
        current.get().abandon(clock.instant());
        sessions.flush();
        return current.get().getId();
    }

    private TurnContext loadForTurn(UUID userId, UUID sessionId, String masked) {
        RubberDuckSession session = queries.require(userId, sessionId);
        if (!queries.policy().canSubmitTurn(session.getStatus(), session.getTurnCount())) {
            throw new ConflictException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "rubber duck session does not accept more turns");
        }
        List<RubberDuckTurn> sessionTurns = queries.turnsOf(session);
        ResolvedTarget target = queries.targetOf(session);
        List<Boolean> stuck =
                new ArrayList<>(sessionTurns.stream().map(RubberDuckTurn::isLearnerStuck).toList());
        stuck.add(queries.policy().isDontKnow(masked));
        return new TurnContext(
                session.getVersion(),
                queries.aiContext(session, target),
                RubberDuckAiSupport.conversation(sessionTurns),
                stuck);
    }

    private RubberDuckTurnResponse saveTurn(
            UUID userId,
            UUID sessionId,
            TurnContext context,
            String maskedExplanation,
            AiResult<RubberDuckTurnOutput> result,
            Instant now) {
        RubberDuckSession session = requireUnchanged(userId, sessionId, context.version());
        int turnNo = session.recordTurn();
        boolean learnerStuck = context.stuck().getLast();
        String question = result.requireValue().question();
        turns.saveAndFlush(
                RubberDuckTurn.record(
                        new RubberDuckTurn.Values(
                                session.getId(),
                                turnNo,
                                maskedExplanation,
                                question,
                                learnerStuck,
                                result.aiCallId()),
                        now));
        sessions.flush();
        return new RubberDuckTurnResponse(
                turnNo,
                question,
                queries.policy().suggestHint(context.stuck()),
                queries.policy().remainingTurns(turnNo),
                RubberDuckAiSupport.meta(result, AiOperation.RUBBER_DUCK),
                session.getVersion());
    }

    private CompleteContext loadForComplete(UUID userId, UUID sessionId) {
        RubberDuckSession session = queries.require(userId, sessionId);
        session.requireInProgress();
        if (!queries.policy().needsSummary(session.getTurnCount())) {
            session.complete(RubberDuckStatus.ABANDONED, null, clock.instant());
            sessions.flush();
            return CompleteContext.early(
                    new RubberDuckCompleteResponse(
                            session.getId(),
                            session.getStatus(),
                            List.of(),
                            List.of(),
                            null,
                            0,
                            null,
                            null,
                            session.getVersion()));
        }
        List<RubberDuckTurn> sessionTurns = queries.turnsOf(session);
        ResolvedTarget target = queries.targetOf(session);
        return new CompleteContext(
                session.getVersion(),
                queries.aiContext(session, target),
                RubberDuckAiSupport.conversation(sessionTurns),
                queries.availableSkillCodes(session.getSkillId()),
                null);
    }

    private RubberDuckCompleteResponse saveSummary(
            UUID userId,
            UUID sessionId,
            CompleteContext context,
            AiResult<RubberDuckSummaryOutput> result,
            LocalDate planDate,
            Instant now) {
        RubberDuckSession session = requireUnchanged(userId, sessionId, context.version());
        if (!result.succeeded()) {
            session.complete(RubberDuckStatus.COMPLETED, null, now);
            sessions.flush();
            return new RubberDuckCompleteResponse(
                    session.getId(),
                    session.getStatus(),
                    List.of(),
                    List.of(),
                    null,
                    0,
                    result.failureCode(),
                    null,
                    session.getVersion());
        }
        RubberDuckSummaryOutput output = result.requireValue();
        RubberDuckSummaryOutput rawValue = result.rawValue();
        RubberDuckSummaryOutput raw = rawValue == null ? output : rawValue;
        Applied applied =
                summaryApplier.apply(
                        session,
                        output,
                        raw.gaps().size(),
                        result.promptVersionLabel(AiOperation.RUBBER_DUCK_SUMMARY),
                        planDate,
                        now);
        RubberDuckSummary summary = applied.summary();
        session.complete(RubberDuckStatus.COMPLETED, summary, now);
        sessions.flush();
        AiMeta meta = RubberDuckAiSupport.meta(result, AiOperation.RUBBER_DUCK_SUMMARY);
        return new RubberDuckCompleteResponse(
                session.getId(),
                session.getStatus(),
                RubberDuckQueryService.gapViews(summary),
                summary.confirmed(),
                summary.overallNote(),
                applied.createdReviewItemCount(),
                null,
                meta,
                session.getVersion());
    }

    /** tx1과 tx2 사이에 세션이 바뀌었으면 409 (docs/05 §1.6). */
    private RubberDuckSession requireUnchanged(UUID userId, UUID sessionId, long version) {
        RubberDuckSession session = queries.require(userId, sessionId);
        if (session.getVersion() != version) {
            throw new ConflictException(
                    ErrorCode.CONCURRENT_MODIFICATION, "rubber duck session changed");
        }
        return session;
    }

    /** docs/05 §9.6 1단계: {@code CONCEPT}은 {@code conceptKey}만, 그 외는 {@code targetId}만. */
    private static void validateShape(StartCommand command) {
        List<ApiFieldError> errors = new ArrayList<>();
        boolean concept = command.targetType() == RubberDuckTargetType.CONCEPT;
        String conceptKey = command.conceptKey();
        boolean hasConceptKey = conceptKey != null && !conceptKey.isBlank();
        boolean hasTargetId = command.targetId() != null;
        if (concept) {
            if (!hasConceptKey) {
                errors.add(ApiFieldError.of("conceptKey", FieldErrorCodes.ONE_OF_REQUIRED));
            }
            if (hasTargetId) {
                errors.add(ApiFieldError.of("targetId", FieldErrorCodes.MUTUALLY_EXCLUSIVE));
            }
        } else {
            if (!hasTargetId) {
                errors.add(ApiFieldError.of("targetId", FieldErrorCodes.ONE_OF_REQUIRED));
            }
            if (hasConceptKey) {
                errors.add(ApiFieldError.of("conceptKey", FieldErrorCodes.MUTUALLY_EXCLUSIVE));
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessValidationException("invalid rubber duck target", errors);
        }
    }

    /** 시작 입력 (docs/05 §9.6 {@code RubberDuckStartRequest}). */
    public record StartCommand(
            RubberDuckTargetType targetType,
            @Nullable UUID targetId,
            @Nullable String conceptKey,
            @Nullable String skillCode) {}

    /** tx1에서 모은 턴 입력. {@code stuck}의 마지막 값이 이번 턴 판정이다(RD-3). */
    private record TurnContext(
            long version, TargetContext aiContext, List<String> conversation, List<Boolean> stuck) {

        TurnContext {
            conversation = List.copyOf(conversation);
            stuck = List.copyOf(stuck);
        }
    }

    /** tx1에서 모은 정리 입력. {@code earlyResponse}가 있으면 AI를 부르지 않는다(턴 0개). */
    private record CompleteContext(
            long version,
            @Nullable TargetContext aiContext,
            List<String> conversation,
            List<String> availableSkillCodes,
            @Nullable RubberDuckCompleteResponse earlyResponse) {

        static CompleteContext early(RubberDuckCompleteResponse response) {
            return new CompleteContext(0, null, List.of(), List.of(), response);
        }

        TargetContext requireAiContext() {
            return Objects.requireNonNull(aiContext, "aiContext");
        }
    }
}
