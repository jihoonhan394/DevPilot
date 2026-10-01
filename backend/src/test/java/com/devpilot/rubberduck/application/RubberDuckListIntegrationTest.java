package com.devpilot.rubberduck.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 내 지난 설명 (docs/05 §9.11). 세션이 끝나면 대화가 화면에서 사라져 "무엇을 설명하지 못했나"의 근거를 다시 볼 수 없었다 (2026-09-29 전수조사).
 */
@IntegrationTest
class RubberDuckListIntegrationTest extends ApiTestSupport {

    private static final String LIST = "/api/v1/rubber-duck";

    @Test
    void shouldListMySessionsNewestFirst() throws Exception {
        TestUser user = onboardedOwner();
        Instant start = clock.instant();
        String first = startDuck(user);
        // 같은 순간에 시작하면 정렬이 id로 갈린다. 시작 순서를 재려면 시각이 달라야 한다.
        clock.setInstant(start.plusSeconds(60));
        String second = startDuck(user);
        clock.setInstant(start);

        JsonNode page = api.body(api.get(user, LIST).andExpect(status().isOk()));

        assertThat(ids(page.path("items"))).containsExactly(second, first);
        JsonNode newest = page.path("items").get(0);
        assertThat(newest.path("turnCount").asInt()).isZero();
        assertThat(newest.path("startedAt").asString()).isNotEmpty();
        assertThat(newest.has("turns")).isFalse();
    }

    /** 목록은 설명 원문을 담지 않는다 — 마스킹본이라도 늘어놓을 이유가 없다. */
    @Test
    void shouldNotCarryTheExplanationText() throws Exception {
        TestUser user = onboardedOwner();
        startDuck(user);

        String body = api.get(user, LIST).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("userText").doesNotContain("turns");
    }

    /** 남의 세션은 내 목록에 없다 (격리 catalog E84). */
    @Test
    void shouldNotShowSomeoneElsesSessions() throws Exception {
        TestUser owner = onboardedOwner();
        startDuck(owner);
        TestUser other = TestUser.invited();
        api.onboard(other);

        JsonNode page = api.body(api.get(other, LIST).andExpect(status().isOk()));

        assertThat(page.path("items")).isEmpty();
    }

    @Test
    void shouldRejectABrokenCursor() throws Exception {
        TestUser user = onboardedOwner();

        api.get(user, LIST + "?cursor=not-a-cursor")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    /** 대상 없이 개념만 놓고 시작하는 세션 하나. id를 돌려준다. */
    private String startDuck(TestUser user) throws Exception {
        JsonNode response =
                api.body(
                        api.postWithKey(
                                user,
                                java.util.UUID.randomUUID().toString(),
                                "/api/v1/rubber-duck",
                                java.util.Map.of(
                                        "targetType",
                                        "CONCEPT",
                                        "conceptKey",
                                        "SPRING.TRANSACTION.BOUNDARY")));
        return response.path("session").path("id").asString();
    }

    private static List<String> ids(JsonNode items) {
        return items.valueStream().map(item -> item.path("id").asString()).toList();
    }
}
