package com.devpilot.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * docs/07 §16 ST-12, docs/05 §1.7 (BL-FND-13), AC-23: 인증 POST의 Idempotency-Key. 사이드 프로젝트 생성으로 확인한다.
 */
@IntegrationTest
class IdempotencyTest extends ApiTestSupport {

    private static final String PROJECTS = "/api/v1/side-projects";

    @Autowired private IdempotencyService idempotencyService;

    @Test
    void shouldRequireKeyOnAuthenticatedPost() throws Exception {
        TestUser user = onboardedOwner();

        mockMvc.perform(
                        post(PROJECTS)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + api.token(user))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(api.toJson(TestApi.sideProjectRequest("키 없음"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void shouldRejectMalformedKey() throws Exception {
        TestUser user = onboardedOwner();

        api.postWithKey(user, "short", PROJECTS, TestApi.sideProjectRequest("형식"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.errors[0].code").value("Pattern"));
        api.postWithKey(user, "bad key with spaces", PROJECTS, TestApi.sideProjectRequest("형식"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReplayStoredResponseWhenSameRequestRepeats() throws Exception {
        TestUser user = onboardedOwner();
        String key = TestApi.newKey();
        String first =
                api.postWithKey(user, key, PROJECTS, TestApi.sideProjectRequest("재생"))
                        .andExpect(status().isCreated())
                        .andExpect(header().doesNotExist("Idempotent-Replayed"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String replay =
                api.postWithKey(user, key, PROJECTS, TestApi.sideProjectRequest("재생"))
                        .andExpect(status().isCreated())
                        .andExpect(header().string("Idempotent-Replayed", "true"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(jsonMapper.readTree(replay)).isEqualTo(jsonMapper.readTree(first));
        assertThat(projects(user)).isEqualTo(1);
    }

    @Test
    void shouldRejectSameKeyWithDifferentBody() throws Exception {
        TestUser user = onboardedOwner();
        String key = TestApi.newKey();
        api.postWithKey(user, key, PROJECTS, TestApi.sideProjectRequest("첫 요청"))
                .andExpect(status().isCreated());

        api.postWithKey(user, key, PROJECTS, TestApi.sideProjectRequest("다른 요청"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(projects(user)).isEqualTo(1);
    }

    @Test
    void shouldAnswerInProgressWhenSameRequestIsStillRunning() throws Exception {
        TestUser user = onboardedOwner();
        String key = TestApi.newKey();
        Map<String, Object> body = TestApi.sideProjectRequest("처리 중");
        jdbc.update(
                "insert into devpilot.idempotency_record (user_id, idempotency_key, request_method,"
                        + " request_path, request_hash, created_at, expires_at) values (?, ?,"
                        + " 'POST', ?, ?, ?, ?)",
                userId(user),
                key,
                PROJECTS,
                idempotencyService.requestHash("POST", PROJECTS, body),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(
                        clock.instant().plus(Duration.ofHours(24)), ZoneOffset.UTC));

        api.postWithKey(user, key, PROJECTS, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_IN_PROGRESS"));
        assertThat(projects(user)).isZero();
    }

    @Test
    void shouldReleaseKeyWhenRequestFails() throws Exception {
        TestUser user = onboardedOwner();
        String key = TestApi.newKey();
        Map<String, Object> invalid = TestApi.sideProjectRequest("실패");
        invalid.put("repoUrl", "ftp://repo.example.invalid");
        api.postWithKey(user, key, PROJECTS, invalid).andExpect(status().isBadRequest());

        assertThat(
                        count(
                                "select count(*) from devpilot.idempotency_record where user_id = ?"
                                        + " and idempotency_key = ?",
                                userId(user),
                                key))
                .isZero();
        api.postWithKey(user, key, PROJECTS, TestApi.sideProjectRequest("재시도"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
    }

    @Test
    void shouldScopeKeysPerUser() throws Exception {
        TestUser first = onboardedOwner();
        TestUser second = onboardedOwner();
        String key = TestApi.newKey();
        api.postWithKey(first, key, PROJECTS, TestApi.sideProjectRequest("같은 키"))
                .andExpect(status().isCreated());

        api.postWithKey(second, key, PROJECTS, TestApi.sideProjectRequest("같은 키"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));

        assertThat(projects(first)).isEqualTo(1);
        assertThat(projects(second)).isEqualTo(1);
    }

    @Test
    void shouldTreatExpiredRecordAsNewRequest() throws Exception {
        TestUser user = onboardedOwner();
        String key = TestApi.newKey();
        api.postWithKey(user, key, PROJECTS, TestApi.sideProjectRequest("만료 전"))
                .andExpect(status().isCreated());
        clock.advance(Duration.ofHours(24).plusSeconds(1));

        api.postWithKey(user, key, PROJECTS, TestApi.sideProjectRequest("만료 후"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.name").value("만료 후"));
        assertThat(projects(user)).isEqualTo(2);
    }

    @Test
    void shouldCheckAuthenticationBeforeIdempotency() throws Exception {
        String key = TestApi.newKey();

        mockMvc.perform(
                        post(PROJECTS)
                                .header(TestApi.IDEMPOTENCY_KEY, key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(api.toJson(TestApi.sideProjectRequest("무인증"))))
                .andExpect(status().isUnauthorized());
        assertThat(
                        count(
                                "select count(*) from devpilot.idempotency_record where"
                                        + " idempotency_key = ?",
                                key))
                .isZero();
    }

    private int projects(TestUser user) {
        UUID userId = userId(user);
        return count("select count(*) from devpilot.side_project where user_id = ?", userId);
    }
}
