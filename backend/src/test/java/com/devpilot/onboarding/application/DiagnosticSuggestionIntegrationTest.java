package com.devpilot.onboarding.application;

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
 * docs/05 §4.2 (AC-11, BL-TRN-13): 진단 제안의 category 제외 규칙. **진단을 실제로 받은 경우에만** 제외한다 — 시작만 하고 나온
 * attempt는 제외하지 않고 이어서 풀도록 그대로 제안한다.
 */
@IntegrationTest
class DiagnosticSuggestionIntegrationTest extends ApiTestSupport {

    private static final String SUGGESTIONS = "/api/v1/diagnostics/suggestions";
    private static final String START_ATTEMPT = "/api/v1/challenges/{challengeId}/attempts";
    private static final String ABANDON = "/api/v1/challenge-attempts/{attemptId}/abandon";

    /**
     * 진단을 열었다가 나오기만 해도 그 category가 영영 제안되지 않던 결함의 재현 테스트다(2026-09-21 실사용에서 확인). 그때는 그 category의 모든
     * skill이 0에서 시작해 계획에서 뒤로 밀렸다.
     */
    @Test
    void shouldKeepSuggestingWhenTheAttemptWasOnlyStarted() throws Exception {
        TestUser user = diagnosticModeUser();
        JsonNode first = suggestions(user);
        assertThat(first).isNotEmpty();
        String challengeId = first.get(0).path("challengeId").asString();
        String category = first.get(0).path("category").asString();
        assertThat(first.get(0).path("activeAttemptId").isNull()).isTrue();

        String attemptId =
                api.body(api.post(user, START_ATTEMPT, null, challengeId)).path("id").asString();

        JsonNode afterStart = suggestions(user);
        assertThat(categories(afterStart)).contains(category);
        assertThat(sameChallenge(afterStart, challengeId).path("activeAttemptId").asString())
                .isEqualTo(attemptId);
    }

    /** 제출 없이 그만둔 attempt도 진단 결과가 없으므로 제외하지 않는다. */
    @Test
    void shouldKeepSuggestingWhenTheAttemptWasAbandoned() throws Exception {
        TestUser user = diagnosticModeUser();
        JsonNode first = suggestions(user);
        String challengeId = first.get(0).path("challengeId").asString();
        String category = first.get(0).path("category").asString();
        String attemptId =
                api.body(api.post(user, START_ATTEMPT, null, challengeId)).path("id").asString();
        api.post(user, ABANDON, null, attemptId).andExpect(status().isOk());

        JsonNode afterAbandon = suggestions(user);
        assertThat(categories(afterAbandon)).contains(category);
        // 그만둔 attempt는 이어서 풀 수 없다 — 새로 시작한다.
        assertThat(sameChallenge(afterAbandon, challengeId).path("activeAttemptId").isNull())
                .isTrue();
    }

    /** 제출까지 갔으면 진단을 받은 것이므로 그 category를 제외한다. */
    @Test
    void shouldStopSuggestingTheCategoryWhenTheAnswerWasSubmitted() throws Exception {
        TestUser user = diagnosticModeUser();
        JsonNode first = suggestions(user);
        String challengeId = first.get(0).path("challengeId").asString();
        String category = first.get(0).path("category").asString();
        String attemptId =
                api.body(api.post(user, START_ATTEMPT, null, challengeId)).path("id").asString();
        api.post(
                user,
                "/api/v1/challenge-attempts/{attemptId}/self-explanation",
                Map.of("text", "먼저 어떤 규칙을 어겼는지 찾고 그 근거를 코드에서 짚겠습니다.", "skipped", false),
                attemptId);
        api.post(
                user,
                "/api/v1/challenge-attempts/{attemptId}/submissions",
                Map.of("answerText", "세 곳 모두 같은 규칙을 어긴 자리이고 아래에서 하나씩 짚습니다."),
                attemptId);

        assertThat(categories(suggestions(user))).doesNotContain(category);
    }

    private TestUser diagnosticModeUser() throws Exception {
        TestUser user = TestUser.owner();
        Map<String, Object> request = TestApi.onboardingRequest();
        request.put("runDiagnostic", true);
        request.put("selfAssessments", List.of());
        api.onboard(user, request);
        return user;
    }

    private JsonNode suggestions(TestUser user) throws Exception {
        return api.body(api.get(user, SUGGESTIONS).andExpect(status().isOk())).path("items");
    }

    private static List<String> categories(JsonNode items) {
        return items.valueStream().map(item -> item.path("category").asString()).toList();
    }

    private static JsonNode sameChallenge(JsonNode items, String challengeId) {
        return items.valueStream()
                .filter(item -> challengeId.equals(item.path("challengeId").asString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("challenge not suggested: " + challengeId));
    }
}
