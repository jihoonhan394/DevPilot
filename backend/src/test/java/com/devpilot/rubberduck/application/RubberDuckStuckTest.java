package com.devpilot.rubberduck.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * docs/09 §10.6.4 SK-1~SK-5 (docs/06 §9.5 RD-3). "모르겠다" 판정은 서버 규칙이라 fixture는 모두 {@code default}이고
 * 설명 텍스트만으로 갈린다. SK-6(CHALLENGE → Hint Ladder)은 training 모듈이 생기는 단계에서 붙인다.
 */
@IntegrationTest
class RubberDuckStuckTest extends ApiTestSupport {

    private static final String SESSIONS = "/api/v1/rubber-duck";
    private static final String TURNS = SESSIONS + "/{sessionId}/turns";
    private static final String NORMAL = "트랜잭션 경계는 서비스 메서드에서 시작한다고 이해했습니다.";
    private static final String DONT_KNOW = "모르겠어요";
    private static final String STILL_DONT_KNOW = "잘 모르겠습니다";

    @Test
    void shouldSuggestHintOnlyAfterTwoStuckTurns() throws Exception {
        // SK-1·SK-2
        TestUser user = onboardedOwner();
        String sessionId = start(user);

        assertThat(turn(user, sessionId, NORMAL).path("suggestHint").asBoolean()).isFalse();
        assertThat(turn(user, sessionId, DONT_KNOW).path("suggestHint").asBoolean()).isFalse();
        assertThat(turn(user, sessionId, STILL_DONT_KNOW).path("suggestHint").asBoolean()).isTrue();

        JsonNode session = api.body(api.get(user, SESSIONS + "/{sessionId}", sessionId));
        assertThat(session.path("suggestHint").asBoolean()).isTrue();
        assertThat(stuckFlags(session)).containsExactly(false, true, true);
    }

    @Test
    void shouldResetStuckCounterWhenNormalTurnComesBetween() throws Exception {
        // SK-3
        TestUser user = onboardedOwner();
        String sessionId = start(user);

        assertThat(turn(user, sessionId, DONT_KNOW).path("suggestHint").asBoolean()).isFalse();
        assertThat(turn(user, sessionId, NORMAL).path("suggestHint").asBoolean()).isFalse();
        assertThat(turn(user, sessionId, DONT_KNOW).path("suggestHint").asBoolean()).isFalse();

        JsonNode session = api.body(api.get(user, SESSIONS + "/{sessionId}", sessionId));
        assertThat(stuckFlags(session)).containsExactly(true, false, true);
    }

    @Test
    void shouldNotSuggestHintOnTheFirstTurn() throws Exception {
        // SK-4
        TestUser user = onboardedOwner();
        String sessionId = start(user);

        assertThat(turn(user, sessionId, DONT_KNOW).path("suggestHint").asBoolean()).isFalse();
    }

    @Test
    void shouldKeepAcceptingTurnsAfterHintSuggestion() throws Exception {
        // SK-5: RD-3은 턴을 막지 않는다
        TestUser user = onboardedOwner();
        String sessionId = start(user);
        turn(user, sessionId, DONT_KNOW);
        turn(user, sessionId, STILL_DONT_KNOW);

        assertThat(turn(user, sessionId, NORMAL).path("suggestHint").asBoolean()).isFalse();
        assertThat(turn(user, sessionId, NORMAL).path("remainingTurns").asInt()).isEqualTo(1);
        assertThat(turn(user, sessionId, NORMAL).path("remainingTurns").asInt()).isZero();
    }

    private String start(TestUser user) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("targetType", "CONCEPT");
        request.put("conceptKey", "SPRING.TRANSACTION.BOUNDARY");
        return api.body(api.post(user, SESSIONS, request)).path("session").path("id").asString();
    }

    private JsonNode turn(TestUser user, String sessionId, String explanation) throws Exception {
        return api.body(
                api.post(user, TURNS, Map.of("explanation", explanation), sessionId)
                        .andExpect(status().isCreated()));
    }

    private static List<Boolean> stuckFlags(JsonNode session) {
        List<Boolean> flags = new java.util.ArrayList<>();
        session.path("turns").forEach(turn -> flags.add(turn.path("learnerStuck").asBoolean()));
        return flags;
    }
}
