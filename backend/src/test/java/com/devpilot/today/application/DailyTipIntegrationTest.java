package com.devpilot.today.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 오늘의 팁 (docs/05 §20, docs/06 §5.12, BL-TIP-01~05).
 *
 * <p>하루 하나라는 약속과 "새로 알았어요 → 복습 카드"가 실제로 이어지는지 본다 — 이 둘이 이 기능의 전부다.
 */
@IntegrationTest
class DailyTipIntegrationTest extends ApiTestSupport {

    private static final String TODAY_TIP = "/api/v1/tips/today";
    private static final String FEEDBACK = "/api/v1/tips/{tipKey}/feedback";

    @Test
    void shouldReturnTheSameTipAllDayAndANewOneTomorrow() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode first = api.body(api.get(user, TODAY_TIP).andExpect(status().isOk()));
        JsonNode again = api.body(api.get(user, TODAY_TIP).andExpect(status().isOk()));
        assertThat(again.get("tipKey").asString()).isEqualTo(first.get("tipKey").asString());
        assertThat(first.get("shownOn").isNull()).isFalse();
        assertThat(first.get("feedback").isNull()).isTrue();

        clock.advance(Duration.ofDays(1));
        JsonNode tomorrow = api.body(api.get(user, TODAY_TIP).andExpect(status().isOk()));
        // TIP-2: 이미 받은 팁은 다시 오지 않는다
        assertThat(tomorrow.get("tipKey").asString()).isNotEqualTo(first.get("tipKey").asString());
    }

    /** TIP-5. 읽고 끝나는 대신 "새로 알았어요"를 고르면 그 내용이 복습으로 돌아온다. */
    @Test
    void shouldCreateAReviewCardWhenTheTipIsMarkedAsLearned() throws Exception {
        TestUser user = onboardedOwner();
        String tipKey = api.body(api.get(user, TODAY_TIP)).get("tipKey").asString();
        String conceptKey = "TIP:" + tipKey;

        api.postWithKey(user, TestApi.newKey(), FEEDBACK, feedback("LEARNED"), tipKey)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.feedback").value("LEARNED"));

        assertThat(
                        count(
                                "select count(*) from devpilot.review_item"
                                        + " where user_id = ? and concept_key = ?"
                                        + " and source_type = 'TIP' and review_type = 'EXPLAIN'",
                                userId(user),
                                conceptKey))
                .isEqualTo(1);
    }

    /** 같은 팁에 두 번 고르면 덮어쓰지 않고 200으로 원래 값을 돌려준다 (docs/05 §20.3 3단계). */
    @Test
    void shouldKeepTheFirstFeedbackWhenChosenTwice() throws Exception {
        TestUser user = onboardedOwner();
        String tipKey = api.body(api.get(user, TODAY_TIP)).get("tipKey").asString();

        api.postWithKey(user, TestApi.newKey(), FEEDBACK, feedback("KNEW_IT"), tipKey)
                .andExpect(status().isCreated());
        api.postWithKey(user, TestApi.newKey(), FEEDBACK, feedback("LEARNED"), tipKey)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").value("KNEW_IT"));
    }

    /** TIP-6. 표시한 그날은 붙지 않고 다음 plan-day부터 Today 응답에 실험 후보로 보인다. */
    @Test
    void shouldAttachTheExperimentCandidateFromTheNextPlanDay() throws Exception {
        TestUser user = onboardedOwner();
        String tipKey = api.body(api.get(user, TODAY_TIP)).get("tipKey").asString();
        api.postWithKey(user, TestApi.newKey(), FEEDBACK, feedback("WILL_TRY"), tipKey)
                .andExpect(status().isCreated());

        JsonNode sameDay = api.generateToday(user, 60, "NORMAL");
        assertThat(sameDay.get("tipExperiment").isNull()).isTrue();

        clock.advance(Duration.ofDays(1));
        JsonNode nextDay = api.generateToday(user, 60, "NORMAL");
        assertThat(nextDay.get("tipExperiment").get("tipKey").asString()).isEqualTo(tipKey);
        assertThat(nextDay.get("tipExperiment").get("estimatedMinutes").asInt()).isEqualTo(25);
    }

    /** 아직 받은 적 없는 팁에는 피드백을 고를 수 없다 — 읽기 전에 고르는 일은 없다. */
    @Test
    void shouldRejectFeedbackForATipThatWasNeverShown() throws Exception {
        TestUser user = onboardedOwner();

        api.postWithKey(
                        user,
                        TestApi.newKey(),
                        FEEDBACK,
                        feedback("KNEW_IT"),
                        "TIP.CONVENTION.NAMING.001")
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectAnonymousAccess() throws Exception {
        api.mockMvc().perform(get(TODAY_TIP)).andExpect(status().isUnauthorized());
    }

    private static Map<String, Object> feedback(String value) {
        return Map.of("feedback", value);
    }
}
