package com.devpilot.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import com.devpilot.user.infrastructure.ProgressResetRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 학습 진도 초기화 (docs/05 §3.7, ADR-056, BL-SEC-19).
 *
 * <p>계정은 남고 진도는 사라진다는 것, 그리고 <b>내가 쓴 글은 기본으로 남는다</b>는 것이 이 기능의 전부다.
 */
@IntegrationTest
class ProgressResetIntegrationTest extends ApiTestSupport {

    private static final String RESET = "/api/v1/me/reset";
    private static final String CONFIRMATION = "초기화합니다";

    /** 이 사용자가 남긴 진도가 실제로 있었는지 세는 자리. */
    private int progressRows(TestUser user) {
        return count(
                "select (select count(*) from devpilot.learning_goal where user_id = ?)"
                        + " + (select count(*) from devpilot.learning_plan where user_id = ?)"
                        + " + (select count(*) from devpilot.user_skill_state where user_id = ?)",
                userId(user),
                userId(user),
                userId(user));
    }

    @Test
    void shouldPutTheReaderBackBeforeOnboardingAndKeepTheAccount() throws Exception {
        TestUser user = onboardedOwner();
        assertThat(progressRows(user)).isPositive();

        JsonNode body =
                api.body(
                        api.postWithKey(user, TestApi.newKey(), RESET, request(false))
                                .andExpect(status().isOk()));

        assertThat(body.get("deletedRows").asInt()).isPositive();
        assertThat(body.get("projectsDeleted").asBoolean()).isFalse();
        assertThat(progressRows(user)).isZero();
        // 계정은 남고 온보딩만 안 한 상태가 된다
        assertThat(count("select count(*) from devpilot.app_user where id = ?", userId(user)))
                .isEqualTo(1);
        api.get(user, "/api/v1/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(false));
    }

    /** 진도가 아니라 <b>내가 쓴 글</b>이다 — 초기화했다고 몇 주치 기록이 사라지면 사고다. */
    @Test
    void shouldKeepTheProjectsAndTheirNotesByDefault() throws Exception {
        TestUser user = onboardedOwner();
        int projects =
                count("select count(*) from devpilot.side_project where user_id = ?", userId(user));
        assertThat(projects).isPositive();

        api.postWithKey(user, TestApi.newKey(), RESET, request(false)).andExpect(status().isOk());

        assertThat(
                        count(
                                "select count(*) from devpilot.side_project where user_id = ?",
                                userId(user)))
                .isEqualTo(projects);
    }

    @Test
    void shouldDeleteTheProjectsOnlyWhenAsked() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode body =
                api.body(
                        api.postWithKey(user, TestApi.newKey(), RESET, request(true))
                                .andExpect(status().isOk()));

        assertThat(body.get("projectsDeleted").asBoolean()).isTrue();
        assertThat(
                        count(
                                "select count(*) from devpilot.side_project where user_id = ?",
                                userId(user)))
                .isZero();
    }

    /** 되돌릴 수 없는 일이라 "예/아니오"로 받지 않는다 (docs/02 §3.2). */
    @Test
    void shouldRefuseWhenTheConfirmationDoesNotMatch() throws Exception {
        TestUser user = onboardedOwner();

        api.postWithKey(
                        user,
                        TestApi.newKey(),
                        RESET,
                        Map.of("confirmation", "초기화", "includeProjects", false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("confirmation"))
                .andExpect(jsonPath("$.errors[0].code").value("VALUE_NOT_ALLOWED"));
        assertThat(progressRows(user)).isPositive();
    }

    /** 이미 초기화한 사람이 다시 눌러도 오류가 아니다. */
    @Test
    void shouldSucceedWithNothingLeftToDelete() throws Exception {
        TestUser user = onboardedOwner();
        api.postWithKey(user, TestApi.newKey(), RESET, request(false)).andExpect(status().isOk());

        JsonNode second =
                api.body(
                        api.postWithKey(user, TestApi.newKey(), RESET, request(false))
                                .andExpect(status().isOk()));

        assertThat(second.get("deletedRows").asInt()).isZero();
    }

    /** 남의 진도는 건드리지 않는다. */
    @Test
    void shouldLeaveAnotherReaderUntouched() throws Exception {
        TestUser owner = onboardedOwner();
        TestUser other = onboardedOwner();
        int before = progressRows(other);

        api.postWithKey(owner, TestApi.newKey(), RESET, request(false)).andExpect(status().isOk());

        assertThat(progressRows(other)).isEqualTo(before);
    }

    /** 솔루션 콘텐츠는 남는다 — 초기화는 사용자 데이터에만 닿는다. */
    @Test
    void shouldNotTouchTheSeededContent() throws Exception {
        TestUser user = onboardedOwner();
        int skills = count("select count(*) from devpilot.skill", new Object[0]);
        int seedChallenges =
                count("select count(*) from devpilot.challenge where owner_user_id is null");

        api.postWithKey(user, TestApi.newKey(), RESET, request(false)).andExpect(status().isOk());

        assertThat(count("select count(*) from devpilot.skill", new Object[0])).isEqualTo(skills);
        assertThat(count("select count(*) from devpilot.challenge where owner_user_id is null"))
                .isEqualTo(seedChallenges);
    }

    /** 지우는 목록이 낡으면 지워야 할 것이 남는다. 새 사용자 테이블이 생기면 이 테스트가 먼저 깨진다 (ADR-056). */
    @Test
    void shouldCoverEveryTableThatCarriesAUserId() {
        List<String> withUserId =
                jdbc.queryForList(
                        "select table_name from information_schema.columns"
                                + " where table_schema = 'devpilot' and column_name = 'user_id'",
                        String.class);

        // 남기기로 정한 것 (ADR-056)
        Set<String> kept =
                Set.of("ai_call_log", "idempotency_record", "side_project", "side_project_note");
        Set<String> covered = new HashSet<>(ProgressResetRepository.progressTables());
        covered.addAll(kept);

        assertThat(covered)
                .as("user_id 를 가진 테이블은 지우거나, 남기기로 적어 두거나 둘 중 하나여야 한다")
                .containsAll(withUserId);
    }

    private static Map<String, Object> request(boolean includeProjects) {
        return Map.of("confirmation", CONFIRMATION, "includeProjects", includeProjects);
    }

    private int count(String sql) {
        return count(sql, new Object[0]);
    }
}
