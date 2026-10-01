package com.devpilot.integration.ai.fake;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.integration.ai.api.AiBalance;
import com.devpilot.integration.ai.api.AiCallStatus;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.AiProvider;
import com.devpilot.integration.ai.api.AiProviderCall;
import com.devpilot.integration.ai.api.AiProviderException;
import com.devpilot.integration.ai.api.AiProviderResponse;
import com.devpilot.integration.ai.api.AiUsage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * fixture 기반 provider (docs/17 §12.2, docs/09 §10.1). {@code devpilot.ai.provider = fake}일 때만
 * 등록된다({@code local}·{@code test}·{@code demo}). fixture는 {@code
 * classpath:ai-fixtures/<OPERATION>/<case>.json}이고 선택이 없으면 {@code default}다. {@code output}을 문자열로
 * 직렬화해 돌려주므로 파싱·검증·가드가 실제로 실행된다.
 *
 * <p>{@code use}, {@code receivedCalls}, {@code callCount}, {@code blockUntilReleased}, {@code
 * release}, {@code awaitBlocked}, {@code setBalance}, {@code reset}은 테스트 전용 확인 기능이다. 운영 코드에서 부르지
 * 않는다.
 */
@Component
@ConditionalOnProperty(name = "devpilot.ai.provider", havingValue = "fake")
public class FakeAiProvider implements AiProvider {

    static final String DEFAULT_CASE = "default";
    private static final long BLOCK_TIMEOUT_SECONDS = 30;

    private final String model;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final Map<AiOperation, String> selected = new EnumMap<>(AiOperation.class);
    private final Map<AiOperation, Integer> attemptIndex = new EnumMap<>(AiOperation.class);
    private final Map<AiOperation, List<AiProviderCall>> received =
            new EnumMap<>(AiOperation.class);
    private final Map<AiOperation, Gate> gates = new EnumMap<>(AiOperation.class);
    private AiBalance balance = AiBalance.unsupported();

    public FakeAiProvider(DevPilotProperties properties) {
        this.model = properties.ai().model();
    }

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public AiProviderResponse send(AiProviderCall call) {
        AiOperation operation = AiOperation.valueOf(call.schemaName());
        Gate gate;
        FixtureAttempt attempt;
        synchronized (this) {
            received.computeIfAbsent(operation, key -> new ArrayList<>()).add(call);
            String caseName = selected.getOrDefault(operation, DEFAULT_CASE);
            int index = attemptIndex.merge(operation, 1, Integer::sum) - 1;
            attempt = load(operation, caseName, index);
            gate = gates.get(operation);
        }
        if (gate != null) {
            gate.passThrough();
        }
        return respond(attempt);
    }

    @Override
    public synchronized AiBalance checkBalance() {
        return balance;
    }

    /** 다음 호출부터 쓸 fixture를 고른다. 시도 순번을 0으로 되돌린다. */
    public synchronized void use(AiOperation operation, String caseName) {
        selected.put(operation, caseName);
        attemptIndex.remove(operation);
    }

    /** provider가 받은 호출 (호출 순서). */
    public synchronized List<AiProviderCall> receivedCalls(AiOperation operation) {
        return List.copyOf(received.getOrDefault(operation, List.of()));
    }

    public synchronized int callCount(AiOperation operation) {
        return received.getOrDefault(operation, List.of()).size();
    }

    /** 이 operation의 다음 호출들을 {@link #release}까지 붙잡는다. */
    public synchronized void blockUntilReleased(AiOperation operation) {
        gates.put(operation, new Gate());
    }

    /** 붙잡은 호출을 놓는다. */
    public void release(AiOperation operation) {
        Gate gate;
        synchronized (this) {
            gate = gates.remove(operation);
        }
        if (gate != null) {
            gate.release.countDown();
        }
    }

    /** 붙잡힌 호출이 들어올 때까지 기다린다. 들어왔으면 true. */
    public boolean awaitBlocked(AiOperation operation, Duration timeout) {
        Gate gate;
        synchronized (this) {
            gate = gates.get(operation);
        }
        if (gate == null) {
            return false;
        }
        try {
            return gate.entered.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 잔액 조회 결과 hook (docs/17 §8.7). */
    public synchronized void setBalance(boolean available, @Nullable String usd) {
        this.balance = AiBalance.of(available, usd == null ? null : new BigDecimal(usd));
    }

    /** 선택·기록·잔액·붙잡기를 초기화한다. */
    public void reset() {
        List<Gate> open;
        synchronized (this) {
            selected.clear();
            attemptIndex.clear();
            received.clear();
            open = List.copyOf(gates.values());
            gates.clear();
            balance = AiBalance.unsupported();
        }
        open.forEach(gate -> gate.release.countDown());
    }

    private AiProviderResponse respond(FixtureAttempt attempt) {
        String simulate = attempt.simulate();
        if (simulate != null) {
            AiProviderResponse filtered = simulate(simulate, attempt);
            if (filtered != null) {
                return filtered;
            }
        }
        return new AiProviderResponse(
                attempt.finishReason(), attempt.outputText(), attempt.usage(), model);
    }

    private @Nullable AiProviderResponse simulate(String simulate, FixtureAttempt attempt) {
        Integer retryAfter = attempt.retryAfterSeconds();
        switch (simulate) {
            case "TIMEOUT" ->
                    throw new AiProviderException(
                            AiCallStatus.TIMEOUT, "timeout", true, null, null);
            case "RATE_LIMITED" ->
                    throw new AiProviderException(
                            AiCallStatus.RATE_LIMITED,
                            "rate_limit",
                            true,
                            retryAfter == null ? null : Duration.ofSeconds(retryAfter),
                            null);
            case "PROVIDER_ERROR" ->
                    throw new AiProviderException(
                            AiCallStatus.PROVIDER_ERROR, "http_503", true, null, null);
            case "CONNECTION_ERROR" ->
                    throw new AiProviderException(
                            AiCallStatus.PROVIDER_ERROR, "io", true, null, null);
            case "BAD_REQUEST" ->
                    throw new AiProviderException(
                            AiCallStatus.PROVIDER_ERROR, "http_400", false, null, null);
            case "INSUFFICIENT_BALANCE" ->
                    throw new AiProviderException(
                            AiCallStatus.PROVIDER_ERROR, "insufficient_balance", false, null, null);
            case "CONTENT_FILTER" -> {
                return new AiProviderResponse("content_filter", null, attempt.usage(), model);
            }
            default -> throw new IllegalStateException("unknown fixture simulate " + simulate);
        }
    }

    private FixtureAttempt load(AiOperation operation, String caseName, int index) {
        String path = "ai-fixtures/" + operation.name() + "/" + caseName + ".json";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("fake AI fixture not found: " + path);
        }
        JsonNode root;
        try {
            root = jsonMapper.readTree(resource.getContentAsByteArray());
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot read " + path, exception);
        }
        JsonNode attempts = root.path("attempts");
        if (!attempts.isArray() || attempts.isEmpty()) {
            throw new IllegalStateException("fixture has no attempts: " + path);
        }
        JsonNode attempt = attempts.get(Math.min(index, attempts.size() - 1));
        String outputText = null;
        if (attempt.has("outputRaw")) {
            outputText = attempt.path("outputRaw").asString();
        } else if (attempt.has("output")) {
            outputText = jsonMapper.writeValueAsString(attempt.path("output"));
        }
        JsonNode usage = attempt.path("usage");
        return new FixtureAttempt(
                attempt.path("finishReason").asString("completed"),
                outputText,
                attempt.has("simulate") ? attempt.path("simulate").asString() : null,
                attempt.has("retryAfterSeconds") ? attempt.path("retryAfterSeconds").asInt() : null,
                new AiUsage(
                        usage.path("inputTokens").asInt(0),
                        usage.path("cachedTokens").asInt(0),
                        usage.path("outputTokens").asInt(0),
                        usage.path("reasoningTokens").asInt(0)));
    }

    private record FixtureAttempt(
            String finishReason,
            @Nullable String outputText,
            @Nullable String simulate,
            @Nullable Integer retryAfterSeconds,
            AiUsage usage) {}

    /** {@link #blockUntilReleased} 상태. */
    private static final class Gate {

        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        void passThrough() {
            entered.countDown();
            try {
                if (!release.await(BLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new AiProviderException(
                            AiCallStatus.TIMEOUT, "timeout", false, null, null);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AiProviderException(
                        AiCallStatus.TIMEOUT, "timeout", false, null, exception);
            }
        }
    }
}
