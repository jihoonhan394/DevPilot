package com.devpilot.review.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 용어 사전 (docs/05 §20.5~§20.7, BL-TRM-01~04).
 *
 * <p>표기를 하나로 모으는 것과, 고르면 <b>양방향 2장</b>이 복습으로 돌아오는 것이 이 기능의 전부다.
 */
@IntegrationTest
class TermIntegrationTest extends ApiTestSupport {

    private static final String TERMS = "/api/v1/terms";
    private static final String TERM = "/api/v1/terms/{termKey}";
    private static final String CARD = "/api/v1/terms/{termKey}/card";

    private static final String CHECKED_EXCEPTION = "TERM.JAVA.CHECKED_EXCEPTION";
    private static final String RETIRED_TERM = "TERM.TESTING.OLD_WORD";

    /** 별칭으로 찾아도 화면에 나오는 것은 대표 표기 하나다 (docs/19 §3.10). */
    @Test
    void shouldFindATermByItsAliasAndAnswerWithTheRepresentativeSpelling() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode page = api.body(api.get(user, TERMS + "?q=검사").andExpect(status().isOk()));

        assertThat(keys(page)).containsExactly(CHECKED_EXCEPTION);
        assertThat(page.get("items").get(0).get("representative").asString()).isEqualTo("체크 예외");
    }

    @Test
    void shouldMatchTheEnglishSpellingWithoutCase() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode page = api.body(api.get(user, TERMS + "?q=IDEMPO").andExpect(status().isOk()));

        assertThat(keys(page)).containsExactly("TERM.WEB_HTTP.IDEMPOTENCY");
    }

    /** 은퇴한 용어는 검색되지 않지만 조회는 된다 — 이미 만든 카드가 그 key를 가리킨다 (docs/19 §8.2). */
    @Test
    void shouldHideARetiredTermFromSearchButStillOpenIt() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode page = api.body(api.get(user, TERMS).andExpect(status().isOk()));
        assertThat(keys(page)).doesNotContain(RETIRED_TERM);

        api.get(user, TERM, RETIRED_TERM)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termKey").value(RETIRED_TERM));
    }

    @Test
    void shouldFilterBySkillAndReturnNothingForASkillNobodyCovers() throws Exception {
        TestUser user = onboardedOwner();
        UUID javaException = skillId("JAVA.EXCEPTION");
        UUID docker = skillId("DEVOPS.DOCKER");

        assertThat(keys(api.body(api.get(user, TERMS + "?skillId=" + javaException))))
                .containsExactly(CHECKED_EXCEPTION);
        assertThat(keys(api.body(api.get(user, TERMS + "?skillId=" + docker)))).isEmpty();
    }

    /** 표기를 보고 뜻이 떠오르는 것과 뜻을 보고 표기가 떠오르는 것은 다른 일이다 — 그래서 두 장이다 (docs/05 §20.7). */
    @Test
    void shouldCreateBothDirectionsOfTheCard() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode response =
                api.body(
                        api.postWithKey(user, TestApi.newKey(), CARD, Map.of(), CHECKED_EXCEPTION)
                                .andExpect(status().isCreated()));

        assertThat(response.get("createdCount").asInt()).isEqualTo(2);
        assertThat(conceptKeys(response))
                .containsExactly(
                        "TERM:" + CHECKED_EXCEPTION, "TERM:" + CHECKED_EXCEPTION + ":REVERSE");
        assertThat(
                        count(
                                "select count(*) from devpilot.review_item"
                                        + " where user_id = ? and concept_key like ?"
                                        + " and source_type = 'TERM' and review_type = 'RECALL'"
                                        + " and origin = 'MANUAL'",
                                userId(user),
                                "TERM:" + CHECKED_EXCEPTION + "%"))
                .isEqualTo(2);
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_event"
                                        + " where user_id = ? and event_type ="
                                        + " 'TERM_CARD_CREATED'",
                                userId(user)))
                .isEqualTo(1);
    }

    /** 뜻을 보고 표기를 말하는 카드다 — 답이 문제에 들어 있으면 맞혀도 아무것도 말해 주지 않는다 (CV-106). */
    @Test
    void shouldAskForTheSpellingWithoutPuttingItInThePrompt() throws Exception {
        TestUser user = onboardedOwner();
        api.postWithKey(user, TestApi.newKey(), CARD, Map.of(), CHECKED_EXCEPTION)
                .andExpect(status().isCreated());

        Map<String, Object> reverse =
                jdbc.queryForMap(
                        "select prompt, expected_answer from devpilot.review_item"
                                + " where user_id = ? and concept_key = ?",
                        userId(user),
                        "TERM:" + CHECKED_EXCEPTION + ":REVERSE");

        assertThat(String.valueOf(reverse.get("prompt"))).doesNotContain("체크 예외");
        assertThat(String.valueOf(reverse.get("expected_answer"))).contains("체크 예외", "검사 예외");
    }

    /** 이미 있는 카드는 문항을 덮어쓰지 않는다 (docs/05 §11.5). 두 장 다 있으면 200이다. */
    @Test
    void shouldNotCreateTheSameCardTwice() throws Exception {
        TestUser user = onboardedOwner();
        api.postWithKey(user, TestApi.newKey(), CARD, Map.of(), CHECKED_EXCEPTION)
                .andExpect(status().isCreated());

        JsonNode second =
                api.body(
                        api.postWithKey(user, TestApi.newKey(), CARD, Map.of(), CHECKED_EXCEPTION)
                                .andExpect(status().isOk()));

        assertThat(second.get("createdCount").asInt()).isZero();
        assertThat(second.get("cards")).hasSize(2);
    }

    @Test
    void shouldMarkTheTermAsAlreadyCardedInTheList() throws Exception {
        TestUser user = onboardedOwner();
        api.postWithKey(user, TestApi.newKey(), CARD, Map.of(), CHECKED_EXCEPTION)
                .andExpect(status().isCreated());

        JsonNode page = api.body(api.get(user, TERMS + "?q=체크"));

        assertThat(page.get("items").get(0).get("cardCreated").asBoolean()).isTrue();
    }

    /** 카드는 그 사용자의 것이다 — 남이 만들었다고 내 목록에 표시되지 않는다. */
    @Test
    void shouldNotShowAnotherUsersCard() throws Exception {
        TestUser owner = onboardedOwner();
        TestUser other = onboardedOwner();
        api.postWithKey(owner, TestApi.newKey(), CARD, Map.of(), CHECKED_EXCEPTION)
                .andExpect(status().isCreated());

        JsonNode detail = api.body(api.get(other, TERM, CHECKED_EXCEPTION));

        assertThat(detail.get("cards")).isEmpty();
    }

    @Test
    void shouldExpandConfusablePairsOnTheDetail() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode detail = api.body(api.get(user, TERM, "TERM.DATABASE.INDEX"));

        assertThat(detail.get("confusableWith").get(0).get("termKey").asString())
                .isEqualTo("TERM.DATABASE.LOCK");
        assertThat(detail.get("skills").get(0).get("code").asString()).isEqualTo("DATABASE.INDEX");
    }

    @Test
    void shouldRejectAKeyThatIsNotInTheRegistry() throws Exception {
        TestUser user = onboardedOwner();

        api.get(user, TERM, "TERM.DATABASE.NOT_THERE").andExpect(status().isNotFound());
        api.postWithKey(user, TestApi.newKey(), CARD, Map.of(), "TERM.DATABASE.NOT_THERE")
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectAKeyThatBreaksThePattern() throws Exception {
        TestUser user = onboardedOwner();

        api.get(user, TERM, "term.database.index").andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectAnonymousAccess() throws Exception {
        api.mockMvc().perform(get(TERMS)).andExpect(status().isUnauthorized());
    }

    private UUID skillId(String code) {
        return jdbc.queryForObject(
                "select id from devpilot.skill where code = ?", UUID.class, code);
    }

    private static List<String> keys(JsonNode page) {
        List<String> keys = new ArrayList<>();
        page.get("items").forEach(item -> keys.add(item.get("termKey").asString()));
        return keys;
    }

    private static List<String> conceptKeys(JsonNode response) {
        List<String> keys = new ArrayList<>();
        response.get("cards").forEach(card -> keys.add(card.get("conceptKey").asString()));
        return keys;
    }
}
