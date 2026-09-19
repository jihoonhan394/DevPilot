package com.devpilot.user.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.MutableClock;
import com.devpilot.testsupport.TestClockConfig;
import com.devpilot.testsupport.TestJwksServer;
import com.devpilot.testsupport.TestJwtFactory;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * test profile 기본값(auth-mode = supabase, JWKS 경로) 확인 — docs/09 §6.2 J-01·J-02·J-13 일부, §6.4 D-11.
 * JWT 검증 케이스 전체와 JIT 프로비저닝은 S1(BL-SEC-04/05)에서 JwtValidationIntegrationTest로 확장한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class SupabaseModeIntegrationTest {

    private static final String OWNER = "owner@devpilot.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private MutableClock clock;
    @Autowired private TestJwksServer jwksServer;

    @BeforeEach
    void resetClock() {
        clock.setInstant(TestClockConfig.DEFAULT_INSTANT);
    }

    @Test
    void shouldAcceptTokenSignedByJwksKey() throws Exception {
        UUID subject = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(token(subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.onboardingCompleted").value(false));
    }

    @Test
    void shouldRejectWhenIssuerDiffers() throws Exception {
        String token =
                TestJwtFactory.sign(
                        jwksServer.key(TestJwksServer.FIRST_KEY_ID),
                        TestJwtFactory.claims(
                                "http://127.0.0.1:1/auth/v1",
                                UUID.randomUUID(),
                                OWNER,
                                clock.instant(),
                                builder -> {}));

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void shouldNotRegisterDevTokenEndpointsWhenSupabaseMode() throws Exception {
        String token = bearer(token(UUID.randomUUID()));

        mockMvc.perform(
                        post("/api/v1/dev/token")
                                .header(HttpHeaders.AUTHORIZATION, token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + OWNER + "\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/dev/jwks.json").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    private String token(UUID subject) {
        return TestJwtFactory.sign(
                jwksServer.key(TestJwksServer.FIRST_KEY_ID),
                TestJwtFactory.claims(
                        jwksServer.issuer(), subject, OWNER, clock.instant(), builder -> {}));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
