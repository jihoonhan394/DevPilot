package com.devpilot.integration.ai.deepseek;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.integration.ai.AiOperationCatalog;
import com.devpilot.integration.ai.api.AiBalance;
import com.devpilot.integration.ai.api.AiCallStatus;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.AiProvider;
import com.devpilot.integration.ai.api.AiProviderCall;
import com.devpilot.integration.ai.api.AiProviderException;
import com.devpilot.integration.ai.api.AiProviderResponse;
import com.devpilot.integration.ai.api.AiUsage;
import java.io.InterruptedIOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * DeepSeek {@code POST /responses} provider (docs/17 §2, §5.3, BL-AIP-03). 공식 Java SDK가 없으므로 Spring
 * {@link RestClient}로 직접 호출한다. 재시도하지 않는다 — 재시도는 {@code AiGateway}가 하고 재시도 전 대기({@code waitBefore})만
 * 여기서 한다(ARCH-07 예외 패키지).
 *
 * <ul>
 *   <li>요청: {@code model}, {@code instructions}, {@code input}, {@code text.format{type:
 *       json_schema, name, schema}}, {@code max_output_tokens}, {@code thinking{type}}, {@code
 *       reasoning_effort}(thinking on만), {@code store}. {@code user}·{@code user_id}·{@code
 *       temperature}·{@code stream}은 보내지 않는다
 *   <li>응답: {@code status}·{@code incomplete_details.reason} → {@code finishReason}, {@code
 *       output[]}의 {@code type = message} 항목 {@code output_text}만 이어 붙인다, {@code
 *       usage}(cached·reasoning 포함), {@code model}
 *   <li>operation마다 read timeout이 고정된 {@link RestClient}를 기동 시 만든다(값이 같으면 공유). connect timeout 5s
 * </ul>
 *
 * 응답 본문의 {@code error.message}는 저장·로그하지 않는다(코드만). {@code api-key}가 비어 있으면 기동 실패다(BL-AIP-15).
 */
@Component
@ConditionalOnProperty(name = "devpilot.ai.provider", havingValue = "deepseek")
public class DeepSeekAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekAiProvider.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration BALANCE_TIMEOUT = Duration.ofSeconds(10);
    private static final int ERROR_CODE_MAX = 60;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_PAYMENT_REQUIRED = 402;

    private final Function<String, RestClient> clientForOperation;
    private final RestClient balanceClient;
    private final boolean store;
    private final Sleeper sleeper;
    private final Clock clock;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Autowired
    public DeepSeekAiProvider(
            DevPilotProperties properties, AiOperationCatalog operations, Clock clock) {
        this(
                clientsByOperation(properties.ai().deepseek(), operations),
                client(properties.ai().deepseek(), BALANCE_TIMEOUT),
                properties.ai().deepseek().store(),
                Sleeper.THREAD,
                clock);
    }

    DeepSeekAiProvider(
            Function<String, RestClient> clientForOperation,
            RestClient balanceClient,
            boolean store,
            Sleeper sleeper,
            Clock clock) {
        this.clientForOperation = clientForOperation;
        this.balanceClient = balanceClient;
        this.store = store;
        this.sleeper = sleeper;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "deepseek";
    }

    @Override
    public AiProviderResponse send(AiProviderCall call) {
        waitBefore(call.waitBefore());
        String body = jsonMapper.writeValueAsString(requestBody(call));
        String response;
        try {
            response =
                    clientForOperation
                            .apply(call.schemaName())
                            .post()
                            .uri("/responses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(String.class);
        } catch (RestClientException exception) {
            throw classify(exception);
        }
        return parseResponse(response == null ? "" : response);
    }

    @Override
    public AiBalance checkBalance() {
        String response;
        try {
            response = balanceClient.get().uri("/user/balance").retrieve().body(String.class);
        } catch (RestClientException exception) {
            throw classify(exception);
        }
        try {
            JsonNode root = jsonMapper.readTree(response == null ? "{}" : response);
            BigDecimal usd = null;
            for (JsonNode info : root.path("balance_infos")) {
                if ("USD".equals(info.path("currency").asString(""))) {
                    usd = new BigDecimal(info.path("total_balance").asString("0"));
                }
            }
            return AiBalance.of(root.path("is_available").asBoolean(false), usd);
        } catch (JacksonException | NumberFormatException exception) {
            throw new AiProviderException(
                    AiCallStatus.PROVIDER_ERROR, "client", false, null, exception);
        }
    }

    /** 요청 본문 (docs/17 §2.2). */
    ObjectNode requestBody(AiProviderCall call) {
        ObjectNode body = jsonMapper.createObjectNode();
        body.put("model", call.model());
        body.put("instructions", call.instructions());
        body.put("input", call.input());
        ObjectNode format = body.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", call.schemaName());
        format.set("schema", call.wireSchema());
        body.put("max_output_tokens", call.maxOutputTokens());
        body.putObject("thinking").put("type", call.thinking() ? "enabled" : "disabled");
        if (call.thinking() && call.reasoningEffort() != null) {
            body.put("reasoning_effort", call.reasoningEffort());
        }
        body.put("store", store);
        return body;
    }

    /** 응답 매핑 (docs/17 §2.2 "응답에서 읽는 필드"). */
    AiProviderResponse parseResponse(String response) {
        JsonNode root;
        try {
            root = jsonMapper.readTree(response);
        } catch (JacksonException exception) {
            throw new AiProviderException(
                    AiCallStatus.PROVIDER_ERROR, "client", false, null, exception);
        }
        String status = root.path("status").asString("");
        String finishReason =
                switch (status) {
                    case "completed" -> "completed";
                    case "incomplete" ->
                            root.path("incomplete_details").path("reason").asString("incomplete");
                    default -> status.isEmpty() ? "unknown" : status;
                };
        StringBuilder text = new StringBuilder();
        boolean found = false;
        for (JsonNode item : root.path("output")) {
            if (!"message".equals(item.path("type").asString(""))) {
                continue;
            }
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asString(""))) {
                    text.append(content.path("text").asString(""));
                    found = true;
                }
            }
        }
        JsonNode usage = root.path("usage");
        AiUsage usageValue =
                new AiUsage(
                        usage.path("input_tokens").asInt(0),
                        usage.path("input_tokens_details").path("cached_tokens").asInt(0),
                        usage.path("output_tokens").asInt(0),
                        usage.path("output_tokens_details").path("reasoning_tokens").asInt(0));
        return new AiProviderResponse(
                finishReason,
                found ? text.toString() : null,
                usageValue,
                root.path("model").asString(""));
    }

    /** {@code RestClient} 예외 → {@link AiProviderException} (docs/17 §5.3 첫 표). 구체적인 예외부터 본다. */
    AiProviderException classify(RestClientException exception) {
        if (exception instanceof HttpClientErrorException clientError) {
            int status = clientError.getStatusCode().value();
            if (status == HTTP_TOO_MANY_REQUESTS) {
                return new AiProviderException(
                        AiCallStatus.RATE_LIMITED,
                        errorCode(clientError, "rate_limit"),
                        true,
                        retryAfter(clientError),
                        exception);
            }
            if (status == HTTP_PAYMENT_REQUIRED) {
                log.error("deepseek rejected the call: insufficient balance (status 402)");
                return new AiProviderException(
                        AiCallStatus.PROVIDER_ERROR,
                        "insufficient_balance",
                        false,
                        null,
                        exception);
            }
            log.error("deepseek rejected the call status={}", status);
            return new AiProviderException(
                    AiCallStatus.PROVIDER_ERROR,
                    errorCode(clientError, "http_" + status),
                    false,
                    null,
                    exception);
        }
        if (exception instanceof HttpServerErrorException serverError) {
            return new AiProviderException(
                    AiCallStatus.PROVIDER_ERROR,
                    errorCode(serverError, "http_" + serverError.getStatusCode().value()),
                    true,
                    retryAfter(serverError),
                    exception);
        }
        if (exception instanceof ResourceAccessException) {
            if (isTimeout(exception)) {
                return new AiProviderException(
                        AiCallStatus.TIMEOUT, "timeout", true, null, exception);
            }
            return new AiProviderException(
                    AiCallStatus.PROVIDER_ERROR, "io", true, null, exception);
        }
        log.error("deepseek call failed errorType={}", exception.getClass().getSimpleName());
        return new AiProviderException(
                AiCallStatus.PROVIDER_ERROR, "client", false, null, exception);
    }

    private String errorCode(RestClientResponseException exception, String fallback) {
        try {
            JsonNode error = jsonMapper.readTree(exception.getResponseBodyAsString()).path("error");
            String code = error.path("code").asString("");
            if (code.isEmpty()) {
                code = error.path("type").asString("");
            }
            if (code.isEmpty()) {
                return fallback;
            }
            return code.length() > ERROR_CODE_MAX ? code.substring(0, ERROR_CODE_MAX) : code;
        } catch (JacksonException parseFailure) {
            return fallback;
        }
    }

    /** {@code Retry-After}: 초 또는 HTTP-date. 없거나 읽을 수 없으면 null. */
    private @Nullable Duration retryAfter(RestClientResponseException exception) {
        HttpHeaders headers = exception.getResponseHeaders();
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(trimmed)));
        } catch (NumberFormatException notSeconds) {
            try {
                Instant at =
                        ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME)
                                .toInstant();
                Duration until = Duration.between(clock.instant(), at);
                return until.isNegative() ? Duration.ZERO : until;
            } catch (DateTimeParseException notDate) {
                return null;
            }
        }
    }

    private static boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof HttpTimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof InterruptedIOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void waitBefore(Duration wait) {
        if (wait.isZero() || wait.isNegative()) {
            return;
        }
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(
                    AiCallStatus.TIMEOUT, "interrupted", false, null, exception);
        }
    }

    private static Function<String, RestClient> clientsByOperation(
            DevPilotProperties.Deepseek deepseek, AiOperationCatalog operations) {
        String apiKey = deepseek.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "devpilot.ai.deepseek.api-key (DEEPSEEK_API_KEY) is required when provider is"
                            + " deepseek");
        }
        Map<Duration, RestClient> byTimeout = new HashMap<>();
        Map<AiOperation, RestClient> clients = new EnumMap<>(AiOperation.class);
        for (AiOperation operation : AiOperation.values()) {
            Duration timeout = operations.settings(operation).timeout();
            clients.put(
                    operation,
                    byTimeout.computeIfAbsent(timeout, value -> client(deepseek, value)));
        }
        return name -> clients.get(AiOperation.valueOf(name));
    }

    private static RestClient client(DevPilotProperties.Deepseek deepseek, Duration readTimeout) {
        // HttpClient는 RestClient와 수명이 같다(애플리케이션 수명). 따로 닫지 않는다.
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        factory.setReadTimeout(readTimeout);
        String apiKey = deepseek.apiKey() == null ? "" : deepseek.apiKey();
        return RestClient.builder()
                .baseUrl(deepseek.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** 재시도 전 대기 (테스트는 기록만 하는 구현을 넣는다). */
    @FunctionalInterface
    interface Sleeper {

        Sleeper THREAD = duration -> Thread.sleep(duration.toMillis());

        void sleep(Duration duration) throws InterruptedException;
    }
}
