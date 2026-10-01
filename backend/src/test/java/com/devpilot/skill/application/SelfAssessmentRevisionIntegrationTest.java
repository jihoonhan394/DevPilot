package com.devpilot.skill.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * docs/05 §6.5: 자기평가 수정. 온보딩에서 한 번 적고 끝이던 값을 나중에 고칠 수 있게 한다 — 그러지 못하면 잘못 적은 것을 깨달은 사람이 진도 전체를 초기화해야
 * 한다.
 */
@IntegrationTest
class SelfAssessmentRevisionIntegrationTest extends ApiTestSupport {

    private static final String SELF_ASSESSMENT = "/api/v1/skills/me/self-assessment";
    private static final String MY_SKILLS = "/api/v1/skills/me";

    /** 기본 온보딩은 SPRING을 2로 적는다. 1로 고치면 그 category의 skill만 바뀌고 나머지는 그대로다. */
    @Test
    void shouldReviseOnlyTheCategoriesInTheRequest() throws Exception {
        TestUser user = onboardedUser();
        assertThat(selfAssessedLevel(user, "SPRING")).isEqualTo(2);
        assertThat(selfAssessedLevel(user, "JAVA")).isEqualTo(3);

        api.put(user, SELF_ASSESSMENT, body(Map.of("category", "SPRING", "level", 1)))
                .andExpect(status().isOk());

        assertThat(selfAssessedLevel(user, "SPRING")).isEqualTo(1);
        assertThat(selfAssessedLevel(user, "JAVA")).isEqualTo(3);
    }

    /** 응답이 GET /skills/me 와 같은 모양이라 화면이 한 번 부르고 바로 다시 그릴 수 있다. */
    @Test
    void shouldAnswerWithTheSameShapeAsMySkills() throws Exception {
        TestUser user = onboardedUser();

        JsonNode response =
                api.body(
                        api.put(
                                        user,
                                        SELF_ASSESSMENT,
                                        body(Map.of("category", "SPRING", "level", 1)))
                                .andExpect(status().isOk()));

        assertThat(response.path("items").isArray()).isTrue();
        assertThat(response.path("items")).isNotEmpty();
    }

    /** 같은 category를 두 번 보내면 400이다 — 어느 값을 쓸지 서버가 고르지 않는다. */
    @Test
    void shouldRejectTheSameCategoryTwice() throws Exception {
        TestUser user = onboardedUser();

        api.put(
                        user,
                        SELF_ASSESSMENT,
                        body(
                                Map.of("category", "SPRING", "level", 1),
                                Map.of("category", "SPRING", "level", 2)))
                .andExpect(status().isBadRequest());
    }

    /** 온보딩 전에는 409다. 처음 값은 온보딩이 받는다(docs/05 §4.1). */
    @Test
    void shouldRequireOnboardingFirst() throws Exception {
        TestUser user = TestUser.owner();

        api.put(user, SELF_ASSESSMENT, body(Map.of("category", "SPRING", "level", 1)))
                .andExpect(status().isConflict());
    }

    private TestUser onboardedUser() throws Exception {
        TestUser user = TestUser.owner();
        api.onboard(user, TestApi.onboardingRequest());
        return user;
    }

    private static Map<String, Object> body(Map<String, Object>... assessments) {
        return Map.of("assessments", List.of(assessments));
    }

    private int selfAssessedLevel(TestUser user, String category) throws Exception {
        JsonNode items =
                api.body(api.get(user, MY_SKILLS).andExpect(status().isOk())).path("items");
        return items.valueStream()
                .filter(item -> category.equals(item.path("skill").path("category").asString()))
                .map(item -> item.path("selfAssessedLevel"))
                .filter(level -> !level.isNull())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no self-assessed skill in " + category))
                .asInt();
    }
}
