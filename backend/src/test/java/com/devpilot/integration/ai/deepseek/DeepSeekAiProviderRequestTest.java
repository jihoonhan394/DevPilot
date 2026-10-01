package com.devpilot.integration.ai.deepseek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.devpilot.integration.ai.api.AiBalance;
import com.devpilot.integration.ai.api.AiCallStatus;
import com.devpilot.integration.ai.api.AiProviderCall;
import com.devpilot.integration.ai.api.AiProviderException;
import com.devpilot.integration.ai.api.AiProviderResponse;
import com.devpilot.testsupport.MutableClock;
import com.devpilot.testsupport.TestClockConfig;
import com.devpilot.testsupport.UnitTest;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * docs/17 §12.1 {@code DeepSeekAiProviderRequestTest}: 요청 JSON(§2.2), 헤더, 응답 매핑, {@code RestClient}
 * 예외 분류(§5.3), {@code waitBefore} 대기, 잔액 조회(§8.7). 실제 네트워크 없이 {@link MockRestServiceServer}만 쓴다.
 */
@UnitTest
class DeepSeekAiProviderRequestTest {

    private static final String BASE = "https://deepseek.example.invalid";
    private static final String KEY = "sk-" + "0".repeat(32);

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<Duration> waits = new ArrayList<>();
    private MockRestServiceServer server;
    private DeepSeekAiProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl(BASE)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + KEY);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        provider =
                new DeepSeekAiProvider(
                        name -> client,
                        client,
                        false,
                        waits::add,
                        new MutableClock(TestClockConfig.DEFAULT_INSTANT));
    }

    @Test
    void shouldSendResponsesRequestWithThinkingOffAndNoUserFields() {
        server.expect(requestTo(BASE + "/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + KEY))
                .andExpect(
                        request -> {
                            JsonNode body =
                                    json.readTree(
                                            ((org.springframework.mock.http.client
                                                                    .MockClientHttpRequest)
                                                            request)
                                                    .getBodyAsString(StandardCharsets.UTF_8));
                            assertThat(body.path("model").asString()).isEqualTo("deepseek-flash");
                            assertThat(body.path("instructions").asString()).isEqualTo("system");
                            assertThat(body.path("input").asString()).isEqualTo("user");
                            assertThat(body.path("text").path("format").path("type").asString())
                                    .isEqualTo("json_schema");
                            assertThat(body.path("text").path("format").path("name").asString())
                                    .isEqualTo("RUBBER_DUCK");
                            assertThat(
                                            body.path("text")
                                                    .path("format")
                                                    .path("schema")
                                                    .path("type")
                                                    .asString())
                                    .isEqualTo("object");
                            assertThat(body.path("max_output_tokens").asInt()).isEqualTo(1500);
                            assertThat(body.path("thinking").path("type").asString())
                                    .isEqualTo("disabled");
                            assertThat(body.has("reasoning_effort")).isFalse();
                            assertThat(body.path("store").asBoolean(true)).isFalse();
                            assertThat(body.has("user")).isFalse();
                            assertThat(body.has("user_id")).isFalse();
                            assertThat(body.has("temperature")).isFalse();
                            assertThat(body.has("stream")).isFalse();
                        })
                .andRespond(withSuccess(completed(), MediaType.APPLICATION_JSON));

        AiProviderResponse response = provider.send(call(false, null, Duration.ZERO));

        assertThat(response.finishReason()).isEqualTo("completed");
        assertThat(response.outputText()).isEqualTo("{\"a\":1}");
        assertThat(response.usage().inputTokens()).isEqualTo(1200);
        assertThat(response.usage().cachedTokens()).isEqualTo(800);
        assertThat(response.usage().outputTokens()).isEqualTo(300);
        assertThat(response.usage().reasoningTokens()).isEqualTo(120);
        assertThat(response.model()).isEqualTo("deepseek-flash");
        assertThat(waits).isEmpty();
        server.verify();
    }

    @Test
    void shouldSendReasoningEffortOnlyWhenThinkingIsOn() {
        server.expect(requestTo(BASE + "/responses"))
                .andExpect(
                        request -> {
                            JsonNode body =
                                    json.readTree(
                                            ((org.springframework.mock.http.client
                                                                    .MockClientHttpRequest)
                                                            request)
                                                    .getBodyAsString(StandardCharsets.UTF_8));
                            assertThat(body.path("thinking").path("type").asString())
                                    .isEqualTo("enabled");
                            assertThat(body.path("reasoning_effort").asString()).isEqualTo("low");
                        })
                .andRespond(withSuccess(completed(), MediaType.APPLICATION_JSON));

        provider.send(call(true, "low", Duration.ZERO));

        server.verify();
    }

    @Test
    void shouldReadIncompleteReasonAndIgnoreReasoningItems() {
        String body =
                """
                {"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"model":"deepseek-flash",
                 "output":[{"type":"reasoning","content":[{"type":"output_text","text":"secret thoughts"}]}],
                 "usage":{"input_tokens":10,"output_tokens":5}}
                """;
        server.expect(requestTo(BASE + "/responses"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        AiProviderResponse response = provider.send(call(false, null, Duration.ZERO));

        assertThat(response.finishReason()).isEqualTo("max_output_tokens");
        assertThat(response.outputText()).isNull();
        assertThat(response.usage().cachedTokens()).isZero();
    }

    @Test
    void shouldClassifyInsufficientBalanceAsNonRetryableProviderError() {
        server.expect(requestTo(BASE + "/responses"))
                .andRespond(
                        withStatus(HttpStatus.PAYMENT_REQUIRED)
                                .body("{\"error\":{\"message\":\"Insufficient Balance\"}}"));

        assertThatThrownBy(() -> provider.send(call(false, null, Duration.ZERO)))
                .isInstanceOfSatisfying(
                        AiProviderException.class,
                        exception -> {
                            assertThat(exception.status()).isEqualTo(AiCallStatus.PROVIDER_ERROR);
                            assertThat(exception.errorCode()).isEqualTo("insufficient_balance");
                            assertThat(exception.retryable()).isFalse();
                        });
    }

    @Test
    void shouldClassifyRateLimitWithRetryAfterSeconds() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "7");
        server.expect(requestTo(BASE + "/responses"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        assertThatThrownBy(() -> provider.send(call(false, null, Duration.ZERO)))
                .isInstanceOfSatisfying(
                        AiProviderException.class,
                        exception -> {
                            assertThat(exception.status()).isEqualTo(AiCallStatus.RATE_LIMITED);
                            assertThat(exception.errorCode()).isEqualTo("rate_limit");
                            assertThat(exception.retryable()).isTrue();
                            assertThat(exception.retryAfter()).isEqualTo(Duration.ofSeconds(7));
                        });
    }

    @Test
    void shouldClassifyServerErrorAsRetryableAndKeepErrorCode() {
        server.expect(requestTo(BASE + "/responses"))
                .andRespond(
                        withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        "{\"error\":{\"code\":\"server_busy\",\"message\":\"hidden\"}}"));

        assertThatThrownBy(() -> provider.send(call(false, null, Duration.ZERO)))
                .isInstanceOfSatisfying(
                        AiProviderException.class,
                        exception -> {
                            assertThat(exception.status()).isEqualTo(AiCallStatus.PROVIDER_ERROR);
                            assertThat(exception.errorCode()).isEqualTo("server_busy");
                            assertThat(exception.retryable()).isTrue();
                        });
    }

    @Test
    void shouldClassifyBadRequestAsNonRetryable() {
        server.expect(requestTo(BASE + "/responses"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> provider.send(call(false, null, Duration.ZERO)))
                .isInstanceOfSatisfying(
                        AiProviderException.class,
                        exception -> {
                            assertThat(exception.errorCode()).isEqualTo("http_400");
                            assertThat(exception.retryable()).isFalse();
                        });
    }

    @Test
    void shouldClassifyReadTimeoutAsTimeout() {
        server.expect(requestTo(BASE + "/responses"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> provider.send(call(false, null, Duration.ZERO)))
                .isInstanceOfSatisfying(
                        AiProviderException.class,
                        exception -> {
                            assertThat(exception.status()).isEqualTo(AiCallStatus.TIMEOUT);
                            assertThat(exception.errorCode()).isEqualTo("timeout");
                        });
    }

    @Test
    void shouldClassifyConnectionFailureAsRetryableIo() {
        server.expect(requestTo(BASE + "/responses"))
                .andRespond(withException(new IOException("connection refused")));

        assertThatThrownBy(() -> provider.send(call(false, null, Duration.ZERO)))
                .isInstanceOfSatisfying(
                        AiProviderException.class,
                        exception -> {
                            assertThat(exception.errorCode()).isEqualTo("io");
                            assertThat(exception.retryable()).isTrue();
                        });
    }

    @Test
    void shouldWaitBeforeSendingWhenRetryAsksForIt() {
        server.expect(requestTo(BASE + "/responses"))
                .andRespond(withSuccess(completed(), MediaType.APPLICATION_JSON));

        provider.send(call(false, null, Duration.ofSeconds(2)));

        assertThat(waits).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void shouldParseUsdBalance() {
        server.expect(requestTo(BASE + "/user/balance"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                """
                                {"is_available":true,"balance_infos":[
                                  {"currency":"CNY","total_balance":"80.00"},
                                  {"currency":"USD","total_balance":"0.99"}]}
                                """,
                                MediaType.APPLICATION_JSON));

        AiBalance balance = provider.checkBalance();

        assertThat(balance.supported()).isTrue();
        assertThat(balance.available()).isTrue();
        assertThat(balance.totalBalanceUsd()).isEqualByComparingTo(new BigDecimal("0.99"));
    }

    private AiProviderCall call(boolean thinking, String effort, Duration waitBefore) {
        return new AiProviderCall(
                "deepseek-flash",
                "system",
                "user",
                json.readTree("{\"type\":\"object\",\"additionalProperties\":false}"),
                "RUBBER_DUCK",
                thinking,
                effort,
                1500,
                waitBefore);
    }

    private static String completed() {
        return """
        {"id":"resp_1","status":"completed","model":"deepseek-flash",
         "output":[
           {"type":"reasoning","content":[{"type":"output_text","text":"thinking"}]},
           {"type":"message","content":[{"type":"output_text","text":"{\\"a\\":"},{"type":"output_text","text":"1}"}]}],
         "usage":{"input_tokens":1200,"input_tokens_details":{"cached_tokens":800},
                  "output_tokens":300,"output_tokens_details":{"reasoning_tokens":120}}}
        """;
    }
}
