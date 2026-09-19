package com.devpilot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * docs/07 §16 ST-09, TM-10 (AC-08): 강제 예외·SQL 오류·JSON 오류 응답에 stack trace, 예외 클래스명, SQL, 패키지명이 없고
 * {@code detail}은 고정 문구다. 401은 사유와 관계없이 같은 본문이다. 예외를 던지는 probe controller를 쓰므로 별도 context다.
 */
@IntegrationTest
@Import(ProblemDetailsLeakTest.ProbeConfig.class)
class ProblemDetailsLeakTest extends ApiTestSupport {

    private static final List<String> FORBIDDEN_FRAGMENTS =
            List.of(
                    "Exception",
                    "exception",
                    "com.devpilot",
                    "java.",
                    "tools.jackson",
                    "org.springframework",
                    "select ",
                    "SELECT",
                    "secret_column",
                    "uq_probe_secret",
                    "\tat ",
                    "stackTrace",
                    "trace\":\"");

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({
        "/api/v1/test-probe/runtime, 500, INTERNAL_ERROR",
        "/api/v1/test-probe/sql, 500, INTERNAL_ERROR",
        "/api/v1/test-probe/integrity, 409, CONCURRENT_MODIFICATION"
    })
    void shouldHideInternalsWhenHandlerThrows(String path, int status, String code)
            throws Exception {
        MvcResult result = api.get(onboardedOwner(), path).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        JsonNode body = api.body(result);
        assertThat(body.path("code").asString()).isEqualTo(code);
        assertThat(body.path("detail").asString()).isNotBlank();
        assertNoLeak(result);
    }

    @Test
    void shouldUseSameFixedDetailForEveryInternalError() throws Exception {
        TestUser user = onboardedOwner();

        String runtime =
                api.body(api.get(user, "/api/v1/test-probe/runtime")).path("detail").asString();
        String sql = api.body(api.get(user, "/api/v1/test-probe/sql")).path("detail").asString();

        assertThat(sql).isEqualTo(runtime);
    }

    @Test
    void shouldHideParserDetailsWhenJsonIsMalformed() throws Exception {
        TestUser user = TestUser.owner();
        api.onboard(user);

        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/side-projects")
                                        .header(
                                                HttpHeaders.AUTHORIZATION,
                                                "Bearer " + api.token(user))
                                        .header(TestApi.IDEMPOTENCY_KEY, TestApi.newKey())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\": \"깨진 JSON\", "))
                        .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(api.body(result).path("code").asString()).isEqualTo("MALFORMED_REQUEST");
        assertNoLeak(result);
    }

    @Test
    void shouldAnswerIdenticalBodyForEveryUnauthorizedReason() throws Exception {
        // exp가 과거인 토큰 (supabase 모드 decoder는 시스템 시계를 쓰므로 MutableClock 대신 고정 과거 시각)
        String expired =
                api.token(
                        TestUser.owner(),
                        builder ->
                                builder.claim(
                                        "exp",
                                        Instant.parse("2001-01-01T00:00:00Z").getEpochSecond()));

        JsonNode missing = problem(mockMvc.perform(get("/api/v1/me")).andReturn());
        JsonNode garbage =
                problem(
                        mockMvc.perform(
                                        get("/api/v1/me")
                                                .header(
                                                        HttpHeaders.AUTHORIZATION,
                                                        "Bearer not.a.jwt"))
                                .andReturn());
        JsonNode expiredToken =
                problem(
                        mockMvc.perform(
                                        get("/api/v1/me")
                                                .header(
                                                        HttpHeaders.AUTHORIZATION,
                                                        "Bearer " + expired))
                                .andReturn());

        assertThat(garbage).isEqualTo(missing);
        assertThat(expiredToken).isEqualTo(missing);
        assertThat(missing.path("code").asString()).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    /** 비교에서 요청마다 다른 값(traceId)을 뺀 401 본문. */
    private JsonNode problem(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertNoLeak(result);
        ObjectNode node = (ObjectNode) api.body(result);
        node.remove("traceId");
        return node;
    }

    private static void assertNoLeak(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (String fragment : FORBIDDEN_FRAGMENTS) {
            assertThat(body).as("response must not contain %s", fragment).doesNotContain(fragment);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig {

        @RestController
        @RequestMapping("/api/v1/test-probe")
        static class ProbeController {

            @GetMapping("/runtime")
            String runtime() {
                throw new IllegalStateException(
                        "SELECT secret_column FROM devpilot.app_user failed at com.devpilot.Probe");
            }

            @GetMapping("/sql")
            String sql() {
                throw new BadSqlGrammarException(
                        "probe",
                        "select secret_column from devpilot.app_user",
                        new SQLException("ERROR: column secret_column does not exist"));
            }

            @GetMapping("/integrity")
            String integrity() {
                throw new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint uq_probe_secret");
            }
        }
    }
}
