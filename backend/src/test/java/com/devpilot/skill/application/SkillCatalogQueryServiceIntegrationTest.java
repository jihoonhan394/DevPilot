package com.devpilot.skill.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** docs/05 §6.1·§6.2 (BL-SKL-01, BL-SKL-02). 테스트 catalog: root 14 + non-root 10. */
@IntegrationTest
class SkillCatalogQueryServiceIntegrationTest extends ApiTestSupport {

    @Test
    void shouldReturnWholeCatalogWithRoleTargets() throws Exception {
        JsonNode tree =
                api.body(api.get(TestUser.owner(), "/api/v1/skills/tree?role=JAVA_BACKEND"));

        assertThat(tree.path("role").asString()).isEqualTo("JAVA_BACKEND");
        assertThat(tree.path("catalogVersion").asInt()).isEqualTo(1);
        JsonNode skills = tree.path("skills");
        assertThat(skills).hasSize(24);
        assertThat(skills.get(0).path("code").asString()).isEqualTo("JAVA");
        assertThat(skills.get(0).path("parentCode").isNull()).isTrue();
        assertThat(skills.get(0).path("roleTarget").isNull()).isTrue();

        JsonNode transaction = node(skills, "SPRING.TRANSACTION");
        assertThat(transaction.path("parentCode").asString()).isEqualTo("SPRING");
        assertThat(transaction.path("category").asString()).isEqualTo("SPRING");
        assertThat(transaction.path("minutesPerLevelStep").asInt()).isEqualTo(150);
        assertThat(texts(transaction.path("prerequisiteCodes"))).containsExactly("JAVA.EXCEPTION");
        assertThat(transaction.path("roleTarget").path("priority").asString()).isEqualTo("MUST");
        assertThat(transaction.path("roleTarget").path("practicalImportanceBp").asInt())
                .isEqualTo(9_500);
        assertThat(transaction.path("roleTarget").path("targets").path("implementation").asInt())
                .isEqualTo(4);
        assertThat(
                        node(skills, "ALGORITHM.SORT_SEARCH")
                                .path("roleTarget")
                                .path("priority")
                                .asString())
                .isEqualTo("SHOULD");
    }

    @Test
    void shouldDefaultToJavaBackendAndRejectUnknownRole() throws Exception {
        TestUser user = TestUser.owner();

        api.get(user, "/api/v1/skills/tree")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("JAVA_BACKEND"));
        api.get(user, "/api/v1/skills/tree?role=FRONTEND")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ENUM_VALUE"));
    }

    @Test
    void shouldListEveryActiveSkillWithPlanTargetsForUser() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode items = api.body(api.get(user, "/api/v1/skills/me")).path("items");

        assertThat(items).hasSize(24);
        JsonNode root = item(items, "JAVA");
        assertThat(root.path("selfAssessedLevel").isNull()).isTrue();
        assertThat(root.path("target").isNull()).isTrue();
        JsonNode index = item(items, "DATABASE.INDEX");
        assertThat(index.path("selfAssessedLevel").asInt()).isEqualTo(2);
        assertThat(index.path("selfAssessmentActive").asBoolean()).isTrue();
        assertThat(index.path("planningLevels").path("knowledge").asInt()).isEqualTo(2);
        assertThat(index.path("evidenceLevels").path("knowledge").asInt()).isZero();
        assertThat(index.path("target").path("priority").asString()).isEqualTo("MUST");
        assertThat(index.path("evidenceCount").asInt()).isZero();
    }

    private static JsonNode node(JsonNode skills, String code) {
        for (JsonNode skill : skills) {
            if (code.equals(skill.path("code").asString())) {
                return skill;
            }
        }
        throw new AssertionError("no skill " + code);
    }

    private static JsonNode item(JsonNode items, String code) {
        for (JsonNode item : items) {
            if (code.equals(item.path("skill").path("code").asString())) {
                return item;
            }
        }
        throw new AssertionError("no skill state " + code);
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asString()));
        return values;
    }
}
