package com.devpilot.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import com.nimbusds.jwt.JWTClaimsSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/**
 * docs/07 §16 ST-06, docs/05 §3.4: {@code amr[].timestamp} 최댓값(없으면 {@code iat})이 5분 이내여야 하고, 미래
 * 60초를 넘으면 거부한다.
 */
@IntegrationTest
class RecentLoginRequiredTest extends ApiTestSupport {

    @Test
    void shouldAcceptWhenAmrTimestampIsExactlyFiveMinutesOld() throws Exception {
        long authTime = clock.instant().minusSeconds(300).getEpochSecond();

        deleteMe(TestUser.owner(), builder -> builder.claim("amr", amr(authTime)))
                .andExpect(status().isAccepted());
    }

    @Test
    void shouldRejectWhenAmrTimestampIsFiveMinutesAndOneSecondOld() throws Exception {
        long authTime = clock.instant().minusSeconds(301).getEpochSecond();

        deleteMe(TestUser.owner(), builder -> builder.claim("amr", amr(authTime)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECENT_LOGIN_REQUIRED"));
    }

    @Test
    void shouldUseLatestAmrEntryWhenSeveralExist() throws Exception {
        long old = clock.instant().minusSeconds(3_000).getEpochSecond();
        long recent = clock.instant().minusSeconds(60).getEpochSecond();

        deleteMe(
                        TestUser.owner(),
                        builder ->
                                builder.claim(
                                        "amr",
                                        List.of(
                                                Map.of("method", "otp", "timestamp", old),
                                                Map.of("method", "password", "timestamp", recent))))
                .andExpect(status().isAccepted());
    }

    @Test
    void shouldFallBackToIatWhenAmrIsMissing() throws Exception {
        Instant oldIat = clock.instant().minusSeconds(600);

        deleteMe(TestUser.owner(), builder -> builder.claim("iat", oldIat.getEpochSecond()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECENT_LOGIN_REQUIRED"));
    }

    @Test
    void shouldRejectWhenAuthTimeIsTooFarInFuture() throws Exception {
        long future = clock.instant().plusSeconds(61).getEpochSecond();

        deleteMe(TestUser.owner(), builder -> builder.claim("amr", amr(future)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECENT_LOGIN_REQUIRED"));
    }

    private ResultActions deleteMe(TestUser user, Consumer<JWTClaimsSet.Builder> customizer)
            throws Exception {
        String token = api.token(user, customizer);
        return mockMvc.perform(
                delete("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static List<Map<String, Object>> amr(long timestamp) {
        return List.of(Map.of("method", "devtoken", "timestamp", timestamp));
    }
}
