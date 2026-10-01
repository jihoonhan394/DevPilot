package com.devpilot.integration.ai;

import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.common.config.DevPilotProperties.AiOperationSettings;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.integration.ai.api.AiBudgetDecision;
import com.devpilot.integration.ai.api.AiCallStatus;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.AiProvider;
import com.devpilot.integration.ai.api.AiProviderCall;
import com.devpilot.integration.ai.api.AiProviderException;
import com.devpilot.integration.ai.api.AiProviderResponse;
import com.devpilot.integration.ai.api.AiRequest;
import com.devpilot.integration.ai.api.AiResult;
import com.devpilot.integration.ai.api.GuardAction;
import com.devpilot.integration.ai.budget.AiBudgetGuard;
import com.devpilot.integration.ai.prompt.PromptRegistry;
import com.devpilot.integration.ai.prompt.RenderedPrompt;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;

/**
 * 도메인 모듈이 쓰는 유일한 AI 진입점 (docs/03 §3.3, docs/17 §5.2, BL-AIP-07). 트랜잭션 밖에서만 부른다(T-2) — 활성 트랜잭션 안에서
 * 부르면 {@link IllegalStateException}이다(ArchUnit ARCH-10과 이중 방어).
 *
 * <pre>
 * 1. provider disabled → PROVIDER_ERROR(AI_UNAVAILABLE), 로그 없음
 * 2. 예산: SYNC면 check(동시 실행 예약, finally에서 닫음), ASYNC면 checkLimitsOnly. 거부 → BUDGET_BLOCKED 결과
 * 3. prompt 렌더링(절삭), wire 스키마, fingerprint
 * 4. deadline = now + timeout 안에서 provider 호출 → 5. 종료 상태·파싱·Bean Validation·가드
 * 6. 호출마다 ai_call_log 1행 (REQUIRES_NEW) → 7. 성공이면 반환, 재시도 가능하고 남은 재시도·시간이 있으면 재시도
 * </pre>
 *
 * 재시도 요청은 같은 {@code instructions} + 같은 user message 뒤에 {@code <validation_feedback>} 블록(경로와 고정
 * 문구만)을 붙인다. 전송 오류 재시도 전 대기는 {@code min(Retry-After ?? retry-after-default, 남은 시간)}이고 provider가
 * 기다린다. 프롬프트·응답 원문은 저장·로그하지 않는다.
 */
@Component
public class AiGateway {

    private static final String FEEDBACK_HEADER =
            "<validation_feedback>\n"
                    + "직전 응답이 서버 검사를 통과하지 못했다. 같은 과제를 처음부터 다시 수행하고 아래 항목을 고친 결과만 출력한다.";
    private static final String FEEDBACK_FOOTER = "</validation_feedback>";

    private final AiOperationCatalog operations;
    private final AiBudgetGuard budgetGuard;
    private final PromptRegistry prompts;
    private final AiProvider provider;
    private final AiOutputProcessor processor;
    private final AiCallRecorder recorder;
    private final Clock clock;

    public AiGateway(
            AiOperationCatalog operations,
            AiBudgetGuard budgetGuard,
            PromptRegistry prompts,
            AiProvider provider,
            AiOutputProcessor processor,
            AiCallRecorder recorder,
            Clock clock) {
        this.operations = operations;
        this.budgetGuard = budgetGuard;
        this.prompts = prompts;
        this.provider = provider;
        this.processor = processor;
        this.recorder = recorder;
        this.clock = clock;
    }

    /** AI 호출 1회(논리 호출). 결과의 {@code status}로 성공·실패를 나눈다 — 실패는 예외가 아니다. */
    public <T> AiResult<T> call(AiRequest<T> request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("AiGateway must be called outside a transaction (T-2)");
        }
        AiOperation operation = request.operation();
        String version = prompts.activeVersion(operation);
        if ("disabled".equals(operations.provider())) {
            return failure(
                    request,
                    version,
                    AiCallStatus.PROVIDER_ERROR,
                    null,
                    List.of(),
                    ErrorCode.AI_UNAVAILABLE,
                    null);
        }
        AiBudgetDecision decision =
                operations.sync(operation)
                        ? budgetGuard.check(request.userId(), operation)
                        : budgetGuard.checkLimitsOnly(request.userId(), operation);
        if (!decision.allowed()) {
            return blocked(request, version, decision);
        }
        try {
            return run(request, version);
        } finally {
            decision.reservation().close();
        }
    }

    private <T> AiResult<T> run(AiRequest<T> request, String version) {
        AiOperation operation = request.operation();
        AiOperationSettings settings = operations.settings(operation);
        RenderedPrompt rendered =
                prompts.render(
                        operation,
                        request.variables(),
                        request.userContent(),
                        settings.inputTokenBudget());
        String model = operations.model();
        CallContext context =
                new CallContext(
                        request,
                        settings,
                        version,
                        rendered,
                        processor.schemas().wireSchema(operation),
                        sha256Hex(
                                operation.promptId()
                                        + "@"
                                        + version
                                        + "\n"
                                        + model
                                        + "\n"
                                        + rendered.system()
                                        + "\n"
                                        + rendered.user()),
                        clock.instant().plus(settings.timeout()));
        AttemptState state = new AttemptState(settings.maxRetries(), settings.thinking());
        while (true) {
            AiResult<T> result = attempt(context, state);
            if (result != null) {
                return result;
            }
        }
    }

    /** provider 호출 1회. 끝났으면 결과, 재시도하면 null(상태는 {@code state}에 반영). */
    private <T> @Nullable AiResult<T> attempt(CallContext context, AttemptState state) {
        AiRequest<?> request = context.request();
        AiOperationSettings settings = context.settings();
        String effort = state.thinking ? settings.effort() : "off";
        if (!remaining(context.deadline())) {
            AttemptOutcome timeout =
                    AttemptOutcome.failure(AiCallStatus.TIMEOUT, "deadline", false);
            UUID id =
                    recorder.record(
                            context.record(
                                    state.attemptNo,
                                    effort,
                                    timeout,
                                    null,
                                    List.of(),
                                    Duration.ZERO));
            return failureOf(context, timeout, id, List.of());
        }
        long started = System.nanoTime();
        AiProviderResponse response = null;
        AttemptOutcome outcome;
        try {
            response = provider.send(providerCall(context, state));
            outcome =
                    processor.evaluate(
                            request.operation(),
                            response,
                            request.outputType(),
                            request.guardContext(),
                            state.retriesLeft == 0);
        } catch (AiProviderException exception) {
            outcome = AttemptOutcome.transport(exception);
        }
        Duration latency = Duration.ofNanos(System.nanoTime() - started);
        boolean retry =
                !outcome.succeeded()
                        && outcome.retryable()
                        && state.retriesLeft > 0
                        && remaining(context.deadline());
        List<GuardAction> actions = new ArrayList<>(outcome.guardActions());
        if (retry) {
            outcome.violationPaths()
                    .forEach(
                            violation ->
                                    actions.add(
                                            new GuardAction(
                                                    violation.guard(),
                                                    "RETRIED",
                                                    violation.path())));
        }
        UUID callId =
                recorder.record(
                        context.record(
                                state.attemptNo, effort, outcome, response, actions, latency));
        if (outcome.succeeded()) {
            return success(context, outcome, callId, actions);
        }
        if (!retry) {
            return failureOf(context, outcome, callId, actions);
        }
        state.prepareRetry(outcome, waitBefore(outcome, context.deadline()));
        return null;
    }

    private AiProviderCall providerCall(CallContext context, AttemptState state) {
        RenderedPrompt rendered = context.rendered();
        String userText =
                state.feedback.isEmpty()
                        ? rendered.user()
                        : rendered.user() + "\n\n" + feedbackBlock(state.feedback);
        AiOperationSettings settings = context.settings();
        return new AiProviderCall(
                operations.model(),
                rendered.system(),
                userText,
                context.wireSchema(),
                context.request().operation().name(),
                state.thinking,
                state.thinking ? settings.reasoningEffort() : null,
                settings.maxTokens(),
                state.waitBefore);
    }

    private Duration waitBefore(AttemptOutcome outcome, Instant deadline) {
        if (!outcome.transport()) {
            return Duration.ZERO;
        }
        Duration retryAfter = outcome.retryAfter();
        Duration wanted = retryAfter == null ? operations.retryAfterDefault() : retryAfter;
        Duration left = Duration.between(clock.instant(), deadline);
        return wanted.compareTo(left) <= 0 ? wanted : left;
    }

    private boolean remaining(Instant deadline) {
        Duration left = Duration.between(clock.instant(), deadline);
        return !(left.isNegative() || left.isZero());
    }

    @SuppressWarnings("unchecked")
    private <T> AiResult<T> success(
            CallContext context, AttemptOutcome outcome, UUID callId, List<GuardAction> actions) {
        Class<T> type = (Class<T>) context.request().outputType();
        return new AiResult<>(
                AiCallStatus.SUCCESS,
                type.cast(outcome.value()),
                type.cast(outcome.rawValue()),
                callId,
                operations.model(),
                context.version(),
                actions,
                null,
                null,
                null);
    }

    private <T> AiResult<T> blocked(
            AiRequest<T> request, String version, AiBudgetDecision decision) {
        ErrorCode errorCode =
                decision.errorCode() == null ? ErrorCode.AI_UNAVAILABLE : decision.errorCode();
        AiCallStatus status =
                errorCode == ErrorCode.AI_UNAVAILABLE
                        ? AiCallStatus.PROVIDER_ERROR
                        : AiCallStatus.BUDGET_BLOCKED;
        return failure(
                request,
                version,
                status,
                decision.blockedCallId(),
                List.of(),
                errorCode,
                decision.retryAfterSeconds());
    }

    @SuppressWarnings("unchecked")
    private <T> AiResult<T> failureOf(
            CallContext context, AttemptOutcome outcome, UUID callId, List<GuardAction> actions) {
        ErrorCode errorCode =
                switch (outcome.status()) {
                    case REFUSED -> ErrorCode.AI_REFUSED;
                    case INVALID_OUTPUT -> ErrorCode.AI_OUTPUT_INVALID;
                    case TIMEOUT -> ErrorCode.AI_TIMEOUT;
                    default -> ErrorCode.AI_UNAVAILABLE;
                };
        return failure(
                (AiRequest<T>) context.request(),
                context.version(),
                outcome.status(),
                callId,
                actions,
                errorCode,
                null);
    }

    private <T> AiResult<T> failure(
            AiRequest<T> request,
            String version,
            AiCallStatus status,
            @Nullable UUID callId,
            List<GuardAction> actions,
            ErrorCode errorCode,
            @Nullable Long retryAfterSeconds) {
        return new AiResult<>(
                status,
                null,
                null,
                callId,
                operations.model(),
                version,
                actions,
                errorCode,
                failureCode(status, errorCode),
                retryAfterSeconds);
    }

    /** docs/17 §5.4 둘째·셋째 열. */
    static AsyncFailureCode failureCode(AiCallStatus status, ErrorCode errorCode) {
        return switch (status) {
            case REFUSED -> AsyncFailureCode.AI_REFUSED;
            case INVALID_OUTPUT -> AsyncFailureCode.AI_OUTPUT_INVALID;
            case TIMEOUT -> AsyncFailureCode.AI_TIMEOUT;
            case RATE_LIMITED -> AsyncFailureCode.AI_RATE_LIMITED;
            case BUDGET_BLOCKED ->
                    errorCode == ErrorCode.AI_CONCURRENCY_LIMIT
                            ? AsyncFailureCode.AI_RATE_LIMITED
                            : AsyncFailureCode.AI_BUDGET_EXCEEDED;
            default -> AsyncFailureCode.AI_UNAVAILABLE;
        };
    }

    private static String feedbackBlock(List<String> lines) {
        return FEEDBACK_HEADER + "\n" + String.join("\n", lines) + "\n" + FEEDBACK_FOOTER;
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /** 논리 호출 1회의 고정 값. */
    private final class CallContext {

        private final AiRequest<?> request;
        private final AiOperationSettings settings;
        private final String version;
        private final RenderedPrompt rendered;
        private final JsonNode wireSchema;
        private final String fingerprint;
        private final Instant deadline;

        CallContext(
                AiRequest<?> request,
                AiOperationSettings settings,
                String version,
                RenderedPrompt rendered,
                JsonNode wireSchema,
                String fingerprint,
                Instant deadline) {
            this.request = request;
            this.settings = settings;
            this.version = version;
            this.rendered = rendered;
            this.wireSchema = wireSchema;
            this.fingerprint = fingerprint;
            this.deadline = deadline;
        }

        AiRequest<?> request() {
            return request;
        }

        AiOperationSettings settings() {
            return settings;
        }

        String version() {
            return version;
        }

        RenderedPrompt rendered() {
            return rendered;
        }

        JsonNode wireSchema() {
            return wireSchema;
        }

        Instant deadline() {
            return deadline;
        }

        AiCallRecorder.AttemptRecord record(
                int attemptNo,
                String effort,
                AttemptOutcome outcome,
                @Nullable AiProviderResponse response,
                List<GuardAction> actions,
                Duration latency) {
            return new AiCallRecorder.AttemptRecord(
                    request.userId(),
                    request.operation(),
                    operations.provider(),
                    response == null || response.model().isBlank()
                            ? operations.model()
                            : response.model(),
                    version,
                    effort,
                    attemptNo,
                    outcome,
                    response == null ? null : response.usage(),
                    response == null ? null : response.finishReason(),
                    actions,
                    fingerprint,
                    latency);
        }
    }

    /** 재시도 사이에 바뀌는 값. */
    private static final class AttemptState {

        private int retriesLeft;
        private int attemptNo = 1;
        private boolean thinking;
        private Duration waitBefore = Duration.ZERO;
        private List<String> feedback = List.of();

        AttemptState(int retriesLeft, boolean thinking) {
            this.retriesLeft = retriesLeft;
            this.thinking = thinking;
        }

        void prepareRetry(AttemptOutcome outcome, Duration nextWait) {
            waitBefore = nextWait;
            Boolean nextThinking = outcome.nextThinking();
            if (nextThinking != null) {
                thinking = nextThinking;
            }
            feedback = outcome.feedback();
            retriesLeft--;
            attemptNo++;
        }
    }
}
