package com.devpilot.plan.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** docs/05 §7.4 (BL-GOL-04), docs/06 §11.1, AC-01 S2·S4, AC-24 S4. */
@IntegrationTest
class PlanCommandServiceIntegrationTest extends ApiTestSupport {

    private static final String MILESTONE = "/api/v1/plans/{planId}/milestones/{milestoneId}";

    @Test
    void shouldUpdateMilestoneInPlaceWithoutNewVersion() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode plan = activePlan(user);
        String milestoneId = plan.path("milestones").get(0).path("id").asString();

        api.patch(user, MILESTONE, patch("IN_PROGRESS", null, null, 0), planId(plan), milestoneId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(milestoneId))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.version").value(1));

        api.get(user, "/api/v1/plans")
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].planVersion").value(1));
        assertThat(activePlan(user).path("milestones").get(0).path("status").asString())
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    void shouldUpdateDescriptionAndSortOrderWhenGiven() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode plan = activePlan(user);
        String milestoneId = plan.path("milestones").get(1).path("id").asString();

        api.patch(user, MILESTONE, patch(null, "주문 생성부터 만든다", 7, 0), planId(plan), milestoneId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("주문 생성부터 만든다"))
                .andExpect(jsonPath("$.sortOrder").value(7))
                .andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    void shouldRejectStaleMilestoneVersion() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode plan = activePlan(user);
        String milestoneId = plan.path("milestones").get(0).path("id").asString();
        api.patch(user, MILESTONE, patch("IN_PROGRESS", null, null, 0), planId(plan), milestoneId)
                .andExpect(status().isOk());

        api.patch(user, MILESTONE, patch("DONE", null, null, 0), planId(plan), milestoneId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void shouldRejectPatchOnSupersededPlan() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        String milestoneId = v1.path("milestones").get(0).path("id").asString();
        api.post(user, "/api/v1/plans/{planId}/replan", replanRequest(v1, "재배치"), planId(v1))
                .andExpect(status().isCreated());
        JsonNode v2Before = activePlan(user);

        api.patch(user, MILESTONE, patch("DONE", null, null, 0), planId(v1), milestoneId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_ACTIVE"));

        assertThat(activePlan(user).path("milestones")).isEqualTo(v2Before.path("milestones"));
        assertThat(api.body(api.get(user, "/api/v1/plans/{planId}", planId(v1))).path("milestones"))
                .isEqualTo(v1.path("milestones"));
    }

    @Test
    void shouldHideOtherUsersPlanAndUnknownMilestone() throws Exception {
        TestUser owner = onboardedOwner();
        TestUser other = onboardedOwner();
        JsonNode plan = activePlan(owner);
        String milestoneId = plan.path("milestones").get(0).path("id").asString();

        api.patch(other, MILESTONE, patch("DONE", null, null, 0), planId(plan), milestoneId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
        api.patch(owner, MILESTONE, patch("DONE", null, null, 0), planId(plan), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(activePlan(owner).path("milestones").get(0).path("status").asString())
                .isEqualTo("PLANNED");
    }

    @Test
    void shouldValidateMilestonePatchBody() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode plan = activePlan(user);
        String milestoneId = plan.path("milestones").get(0).path("id").asString();

        api.patch(user, MILESTONE, Map.of("status", "DONE"), planId(plan), milestoneId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"))
                .andExpect(jsonPath("$.errors[0].code").value("NotNull"));
        api.patch(
                        user,
                        MILESTONE,
                        Map.of("status", "FINISHED", "version", 0),
                        planId(plan),
                        milestoneId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ENUM_VALUE"));
        api.patch(user, MILESTONE, Map.of("title", "새 제목", "version", 0), planId(plan), milestoneId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        api.patch(user, MILESTONE, Map.of("sortOrder", -1, "version", 0), planId(plan), milestoneId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sortOrder"))
                .andExpect(jsonPath("$.errors[0].code").value("Min"));
    }

    private static String planId(JsonNode plan) {
        return plan.path("id").asString();
    }

    private static Map<String, Object> patch(
            String status, String description, Integer sortOrder, long version) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (status != null) {
            body.put("status", status);
        }
        if (description != null) {
            body.put("description", description);
        }
        if (sortOrder != null) {
            body.put("sortOrder", sortOrder);
        }
        body.put("version", version);
        return body;
    }
}
