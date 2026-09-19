package com.devpilot.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.common.security.DevTokenSigningKey;
import com.devpilot.testsupport.AuditLogCapture;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.MutableClock;
import com.devpilot.testsupport.TestClockConfig;
import com.devpilot.testsupport.TestJwtFactory;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** docs/09 §6.4 (D-01 ~ D-10), AC-25, docs/05 §1.4.5 · §1.2. devtoken 모드 전용 context. */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "devpilot.security.auth-mode=devtoken")
class DevTokenIntegrationTest {

    private static final String OWNER = "owner@devpilot.test";
    private static final String OWNER_SUB = "159725c9-9f1f-5187-844e-f474bd7e29b1";
    private static final String ISSUER = "http://localhost/dev";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired private MockMvc mockMvc;
    @Autowired private MutableClock clock;
    @Autowired private DevTokenSigningKey signingKey;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetClock() {
        clock.setInstant(TestClockConfig.DEFAULT_INSTANT);
    }

    @Test
    void shouldIssueTokenAndAuditWhenEmailIsAllowlisted() throws Exception {
        try (AuditLogCapture audit = AuditLogCapture.start()) {
            mockMvc.perform(tokenRequest("Owner@DevPilot.test"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresAt").value("2026-11-04T10:00:00Z"))
                    .andExpect(jsonPath("$.accessToken").isString());

            assertThat(audit.events()).containsExactly("AUTH_DEVTOKEN_ISSUED");
        }
    }

    @Test
    void shouldCarryDocumentedClaimsWhenTokenIsIssued() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(issueToken(OWNER)).getJWTClaimsSet();

        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getAudience()).containsExactly("authenticated");
        assertThat(claims.getSubject()).isEqualTo(OWNER_SUB);
        assertThat(claims.getStringClaim("email")).isEqualTo(OWNER);
        List<Object> amr = claims.getListClaim("amr");
        assertThat(amr).hasSize(1);
        assertThat(amr.get(0))
                .asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsEntry("method", "devtoken");
    }

    @Test
    void shouldKeepSameSubjectWhenIssuedTwice() throws Exception {
        String first = SignedJWT.parse(issueToken(OWNER)).getJWTClaimsSet().getSubject();
        String second = SignedJWT.parse(issueToken(OWNER)).getJWTClaimsSet().getSubject();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void shouldRejectWithProblemDetailWhenEmailIsNotAllowlisted() throws Exception {
        try (AuditLogCapture audit = AuditLogCapture.start()) {
            mockMvc.perform(tokenRequest("stranger@devpilot.test"))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("USER_NOT_ALLOWED"))
                    .andExpect(jsonPath("$.type").value("urn:devpilot:problem:user-not-allowed"))
                    .andExpect(jsonPath("$.accessToken").doesNotExist());

            assertThat(audit.events()).containsExactly("AUTH_DEVTOKEN_REJECTED");
        }
    }

    @Test
    void shouldReturnValidationErrorWhenEmailIsMalformed() throws Exception {
        mockMvc.perform(tokenRequest("not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("Email"));
    }

    @Test
    void shouldReturnMalformedRequestWhenBodyIsMissingOrHasUnknownField() throws Exception {
        mockMvc.perform(post("/api/v1/dev/token").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(
                        post("/api/v1/dev/token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + OWNER + "\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void shouldServePublicJwksWithoutPrivatePartWhenRequested() throws Exception {
        mockMvc.perform(get("/api/v1/dev/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.keys[0].kty").value("EC"))
                .andExpect(jsonPath("$.keys[0].crv").value("P-256"))
                .andExpect(jsonPath("$.keys[0].alg").value("ES256"))
                .andExpect(jsonPath("$.keys[0].kid").value(signingKey.keyId()))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    @Test
    void shouldProvisionUserOnceWhenIssuedTokenIsUsed() throws Exception {
        String firstToken = issueToken(OWNER);
        String secondToken = issueToken(OWNER);

        try (AuditLogCapture audit = AuditLogCapture.start()) {
            String firstId =
                    mockMvc.perform(
                                    get("/api/v1/me")
                                            .header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.displayName").value("owner"))
                            .andExpect(jsonPath("$.onboardingCompleted").value(false))
                            .andExpect(jsonPath("$.aiStatus").value("DISABLED"))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            String secondId =
                    mockMvc.perform(
                                    get("/api/v1/me")
                                            .header(HttpHeaders.AUTHORIZATION, bearer(secondToken)))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            assertThat(idOf(firstId)).isEqualTo(idOf(secondId));
            assertThat(appUserRows(OWNER_SUB)).isEqualTo(1);
            assertThat(audit.events())
                    .filteredOn("AUTH_USER_PROVISIONED"::equals)
                    .hasSizeLessThanOrEqualTo(1);
        }
    }

    @Test
    void shouldNotCreateUserRowWhenOnlyTokenIsIssued() throws Exception {
        String token = issueToken("invited@devpilot.test");
        String subject = SignedJWT.parse(token).getJWTClaimsSet().getSubject();

        assertThat(appUserRows(subject)).isZero();
    }

    @Test
    void shouldReturnUnauthorizedProblemWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.instance").value("/api/v1/me"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void shouldRejectTokenWhenExpired() throws Exception {
        String token = issueToken(OWNER);
        clock.advance(Duration.ofHours(720).plusSeconds(61));

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldRejectTokenWhenSignedWithAnotherKey() throws Exception {
        DevTokenSigningKey otherKey = DevTokenSigningKey.generate();
        String forged = TestJwtFactory.sign(otherKey.signingJwk(), ownerClaims(builder -> {}));

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectTokenWhenAudienceOrIssuerIsWrong() throws Exception {
        String wrongAudience =
                TestJwtFactory.sign(
                        signingKey.signingJwk(), ownerClaims(builder -> builder.audience("anon")));
        String wrongIssuer =
                TestJwtFactory.sign(
                        signingKey.signingJwk(),
                        ownerClaims(builder -> builder.issuer("http://other.example.invalid/dev")));

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(wrongAudience)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(wrongIssuer)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnForbiddenWhenValidTokenEmailIsNotAllowlisted() throws Exception {
        String stranger =
                TestJwtFactory.sign(
                        signingKey.signingJwk(),
                        ownerClaims(builder -> builder.claim("email", "stranger@devpilot.test")));

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_NOT_ALLOWED"));
    }

    @Test
    void shouldRejectRequestBodyOverLimitBeforeAuthentication() throws Exception {
        String body = "{\"email\":\"" + "a".repeat(70_000) + "@devpilot.test\"}";

        mockMvc.perform(
                        post("/api/v1/dev/token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"));
    }

    @Test
    void shouldEchoTraceIdInHeaderAndProblemBody() throws Exception {
        String traceId = "0123456789abcdef0123456789abcdef";

        mockMvc.perform(get("/api/v1/me").header("X-Trace-Id", traceId))
                .andExpect(header().string("X-Trace-Id", traceId))
                .andExpect(jsonPath("$.traceId").value(traceId));
    }

    @Test
    void shouldHideUnknownPathsAndMethodsAsNotFound() throws Exception {
        String token = bearer(issueToken(OWNER));

        mockMvc.perform(get("/api/v1/does-not-exist").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(put("/api/v1/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.RequestBuilder tokenRequest(String email) {
        return post("/api/v1/dev/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}");
    }

    private String issueToken(String email) throws Exception {
        MvcResult result =
                mockMvc.perform(tokenRequest(email)).andExpect(status().isOk()).andReturn();
        String json = result.getResponse().getContentAsString();
        int start = json.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
        return json.substring(start, json.indexOf('"', start));
    }

    private JWTClaimsSet ownerClaims(java.util.function.Consumer<JWTClaimsSet.Builder> customizer) {
        return TestJwtFactory.claims(
                ISSUER, UUID.fromString(OWNER_SUB), OWNER, clock.instant(), customizer);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private int appUserRows(String subject) {
        Integer rows =
                jdbcTemplate.queryForObject(
                        "select count(*) from devpilot.app_user where external_auth_id = ?",
                        Integer.class,
                        UUID.fromString(subject));
        return rows == null ? 0 : rows;
    }

    private static String idOf(String json) {
        int start = json.indexOf("\"id\":\"") + "\"id\":\"".length();
        return json.substring(start, json.indexOf('"', start));
    }
}
