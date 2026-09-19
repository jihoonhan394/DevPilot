package com.devpilot.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

/** docs/05 §19 (BL-PRJ-01), docs/09 SP-T1~SP-T8·SP-T10 (SP-T9·SP-T11은 task·러버덕이 생기는 단계). */
@IntegrationTest
class SideProjectServiceIntegrationTest extends ApiTestSupport {

    private static final String PROJECTS = "/api/v1/side-projects";
    private static final String PROJECT = "/api/v1/side-projects/{projectId}";

    @Test
    void shouldCreateActiveProjectAndNormalizeEmptyStrings() throws Exception {
        TestUser user = onboardedOwner();
        Map<String, Object> request = TestApi.sideProjectRequest("주문 서비스");
        request.put("description", "");
        request.put("stack", "");

        api.post(user, PROJECTS, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("주문 서비스"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(
                        jsonPath("$.repoUrl").value("https://repo.example.invalid/order-service"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.stack").doesNotExist())
                .andExpect(jsonPath("$.createdAt").value("2026-10-05T10:00:00Z"));

        assertThat(
                        count(
                                "select count(*) from devpilot.side_project where user_id = ? and"
                                        + " description is null and stack is null",
                                userId(user)))
                .isEqualTo(1);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"ftp://repo.example.invalid/x", "not a url", "https://", "/relative"})
    void shouldRejectInvalidRepoUrl(String repoUrl) throws Exception {
        TestUser user = onboardedOwner();
        Map<String, Object> request = TestApi.sideProjectRequest("주문 서비스");
        request.put("repoUrl", repoUrl);

        api.post(user, PROJECTS, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("repoUrl"))
                .andExpect(jsonPath("$.errors[0].code").value("URL"));
        assertThat(
                        count(
                                "select count(*) from devpilot.side_project where user_id = ?",
                                userId(user)))
                .isZero();
    }

    @Test
    void shouldRejectOverlongFields() throws Exception {
        TestUser user = onboardedOwner();

        api.post(user, PROJECTS, with("repoUrl", "https://repo.example.invalid/" + "a".repeat(472)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("repoUrl"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"));
        api.post(user, PROJECTS, with("name", "n".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
        api.post(user, PROJECTS, with("description", "d".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
        api.post(user, PROJECTS, with("stack", "s".repeat(301)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("stack"));
    }

    @Test
    void shouldKeepVersionWhenPatchChangesNothing() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode created = create(user, "주문 서비스");
        clock.advance(Duration.ofMinutes(10));

        api.patch(user, PROJECT, Map.of("version", 0), id(created))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.updatedAt").value(created.path("updatedAt").asString()));
        api.patch(user, PROJECT, Map.of("name", "주문 서비스", "version", 0), id(created))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void shouldClearDescriptionAndRejectBlankName() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode created = create(user, "주문 서비스");

        api.patch(user, PROJECT, Map.of("description", "", "version", 0), id(created))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.version").value(1));
        api.patch(user, PROJECT, Map.of("name", "  ", "version", 1), id(created))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("NOT_BLANK_IF_PRESENT"));
        api.patch(user, PROJECT, Map.of("repoUrl", "ftp://x.invalid", "version", 1), id(created))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("URL"));
    }

    @Test
    void shouldRejectStaleVersion() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode created = create(user, "주문 서비스");
        api.patch(user, PROJECT, Map.of("stack", "Spring", "version", 0), id(created))
                .andExpect(status().isOk());

        api.patch(user, PROJECT, Map.of("stack", "Kotlin", "version", 0), id(created))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void shouldAllowEveryStatusTransition() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode created = create(user, "주문 서비스");
        long version = 0;
        for (String status : List.of("PAUSED", "ACTIVE", "DONE", "PAUSED", "DONE", "ACTIVE")) {
            JsonNode updated =
                    api.body(
                            api.patch(
                                            user,
                                            PROJECT,
                                            Map.of("status", status, "version", version),
                                            id(created))
                                    .andExpect(status().isOk())
                                    .andExpect(jsonPath("$.status").value(status)));
            version = updated.path("version").asLong();
        }
        assertThat(version).isEqualTo(6);
    }

    @Test
    void shouldListByStatusNewestFirstAndPageWithCursor() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode first = create(user, "첫 번째");
        clock.advance(Duration.ofMinutes(1));
        JsonNode second = create(user, "두 번째");
        clock.advance(Duration.ofMinutes(1));
        JsonNode paused = create(user, "멈춤");
        api.patch(user, PROJECT, Map.of("status", "PAUSED", "version", 0), id(paused))
                .andExpect(status().isOk());

        JsonNode active = api.body(api.get(user, PROJECTS + "?status=ACTIVE"));
        assertThat(ids(active.path("items"))).containsExactly(id(second), id(first));

        JsonNode page = api.body(api.get(user, PROJECTS + "?limit=2"));
        assertThat(ids(page.path("items"))).containsExactly(id(paused), id(second));
        JsonNode next =
                api.body(
                        api.get(
                                user,
                                PROJECTS + "?limit=2&cursor={cursor}",
                                page.path("nextCursor").asString()));
        assertThat(ids(next.path("items"))).containsExactly(id(first));
        assertThat(next.path("nextCursor").isNull()).isTrue();

        api.get(user, PROJECTS + "?status=ARCHIVED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ENUM_VALUE"));
    }

    @Test
    void shouldReturnNotFoundAfterDelete() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode created = create(user, "지울 프로젝트");

        api.delete(user, PROJECT, id(created)).andExpect(status().isNoContent());

        api.delete(user, PROJECT, id(created))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        api.get(user, PROJECT, id(created))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldHideOtherUsersProject() throws Exception {
        TestUser owner = onboardedOwner();
        TestUser other = onboardedOwner();
        JsonNode created = create(owner, "남의 프로젝트");

        api.get(other, PROJECT, id(created)).andExpect(status().isNotFound());
        api.patch(other, PROJECT, Map.of("name", "탈취", "version", 0), id(created))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        api.delete(other, PROJECT, id(created)).andExpect(status().isNotFound());

        api.get(owner, PROJECT, id(created))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("남의 프로젝트"))
                .andExpect(jsonPath("$.version").value(0));
    }

    private JsonNode create(TestUser user, String name) throws Exception {
        return api.body(
                api.post(user, PROJECTS, TestApi.sideProjectRequest(name))
                        .andExpect(status().isCreated()));
    }

    private static Map<String, Object> with(String key, String value) {
        Map<String, Object> request = new HashMap<>(TestApi.sideProjectRequest("주문 서비스"));
        request.put(key, value);
        return request;
    }

    private static String id(JsonNode node) {
        return node.path("id").asString();
    }

    private static List<String> ids(JsonNode items) {
        List<String> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.path("id").asString()));
        return ids;
    }
}
