package com.devpilot.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * docs/09 §11 E2E-02 Seed 복습 답변 → 간격 (AC-05, AC-10 S5). 7번의 skill 레벨 변화(K1_ANY_EVENT)는 {@code
 * SkillStateUpdater}가 이벤트를 구독하는 S3(BL-SKL-05)부터 확인한다.
 */
@IntegrationTest
class SeedReviewFlowTest extends ApiTestSupport {

    private static final String ANSWER = "/api/v1/reviews/{reviewItemId}/answer";

    @Test
    void shouldScheduleSeedCardsByRatingAndHint() throws Exception {
        // 1. 온보딩
        TestUser user = onboardedOwner();
        UUID userId = userId(user);

        // 2. 오늘 due 5장
        JsonNode due = api.body(api.get(user, "/api/v1/reviews/due?limit=20"));
        assertThat(due.path("items")).hasSize(5);
        for (JsonNode item : due.path("items")) {
            assertThat(item.path("prompt").asString()).isNotBlank();
            assertThat(item.path("expectedAnswer").asString()).isNotBlank();
            assertThat(item.path("rubric")).isNotEmpty();
        }
        String cardX = due.path("items").get(0).path("reviewItemId").asString();
        String cardY = due.path("items").get(1).path("reviewItemId").asString();
        String cardZ = due.path("items").get(2).path("reviewItemId").asString();

        // 3. X: GOOD, hint 없음 → 간격 1 → 2, 다음 due start(2026-10-07)
        String key = TestApi.newKey();
        JsonNode x =
                api.body(
                        api.postWithKey(
                                        user,
                                        key,
                                        ANSWER,
                                        TestApi.answerRequest("GOOD", "SELF_EXPLAIN"),
                                        cardX)
                                .andExpect(status().isOk()));
        assertThat(x.path("finalRating").asString()).isEqualTo("GOOD");
        assertThat(x.path("adjustedBy")).isEmpty();
        assertThat(x.path("intervalBefore").asInt()).isEqualTo(1);
        assertThat(x.path("intervalAfter").asInt()).isEqualTo(2);
        assertThat(x.path("nextDueDate").asString()).isEqualTo("2026-10-07");
        assertThat(
                        jdbc.queryForObject(
                                "select due_at = timestamptz '2026-10-06T19:00:00Z' from"
                                        + " devpilot.review_item where id = ?::uuid",
                                Boolean.class,
                                cardX))
                .isTrue();

        // 4. Y: EASY + CONCEPT_HINT → HARD
        JsonNode y = api.answerReview(user, cardY, "EASY", "CONCEPT_HINT");
        assertThat(y.path("finalRating").asString()).isEqualTo("HARD");
        assertThat(y.path("adjustedBy").get(0).asString()).isEqualTo("HINT_CAP_HARD");
        assertThat(y.path("intervalAfter").asInt()).isEqualTo(2);

        // 5. Z: GOOD + FULL_EXAMPLE → AGAIN
        JsonNode z = api.answerReview(user, cardZ, "GOOD", "FULL_EXAMPLE");
        assertThat(z.path("finalRating").asString()).isEqualTo("AGAIN");
        assertThat(z.path("adjustedBy").get(0).asString()).isEqualTo("HINT_CAP_AGAIN");
        assertThat(z.path("intervalAfter").asInt()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select consecutive_failures from devpilot.review_item where id ="
                                        + " ?::uuid",
                                Integer.class,
                                cardZ))
                .isEqualTo(1);

        // 6. 남은 due 2장
        assertThat(api.body(api.get(user, "/api/v1/reviews/due")).path("items")).hasSize(2);

        // 7. 저장 결과
        assertThat(count("select count(*) from devpilot.review_answer where user_id = ?", userId))
                .isEqualTo(3);
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_event where user_id = ? and"
                                        + " event_type = 'REVIEW_ANSWERED'",
                                userId))
                .isEqualTo(3);

        // 8. 같은 키·같은 body 재전송
        api.postWithKey(user, key, ANSWER, TestApi.answerRequest("GOOD", "SELF_EXPLAIN"), cardX)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"));
        assertThat(count("select count(*) from devpilot.review_answer where user_id = ?", userId))
                .isEqualTo(3);
    }
}
