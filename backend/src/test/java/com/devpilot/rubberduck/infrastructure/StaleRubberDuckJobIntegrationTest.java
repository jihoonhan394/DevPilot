package com.devpilot.rubberduck.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.common.job.PerUserJob.JobResult;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * docs/09 §10.6.5 SR-1~SR-6 (docs/03 §6): {@code started_at < now − stale-after}(24h)인 {@code
 * IN_PROGRESS} 세션만 {@code ABANDONED}가 된다. 비교가 엄격해 정확히 24시간은 아직 방치가 아니다.
 */
@IntegrationTest
class StaleRubberDuckJobIntegrationTest extends ApiTestSupport {

    private static final String SESSIONS = "/api/v1/rubber-duck";
    private static final Instant STARTED = Instant.parse("2026-10-05T10:00:00Z");

    @Autowired private StaleRubberDuckJob job;

    @Test
    void shouldKeepSessionJustBeforeAndAtTheBoundary() throws Exception {
        // SR-1·SR-2
        TestUser user = onboardedOwner();
        String sessionId = start(user);

        clock.setInstant(STARTED.plus(Duration.ofHours(24)).minusSeconds(1));
        job.runOnce();
        assertThat(sessionStatus(sessionId)).isEqualTo("IN_PROGRESS");

        clock.setInstant(STARTED.plus(Duration.ofHours(24)));
        job.runOnce();
        assertThat(sessionStatus(sessionId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void shouldAbandonSessionAfterTheBoundaryWithoutAiOrCards() throws Exception {
        // SR-3·SR-4: 기준은 마지막 턴이 아니라 started_at이다
        TestUser user = onboardedOwner();
        String sessionId = start(user);
        clock.setInstant(STARTED.plus(Duration.ofHours(23)));
        api.post(
                        user,
                        SESSIONS + "/{sessionId}/turns",
                        Map.of("explanation", "트랜잭션 경계는 서비스 메서드에서 시작합니다."),
                        sessionId)
                .andExpect(status().isCreated());
        int summaryCalls = fakeAi().callCount(AiOperation.RUBBER_DUCK_SUMMARY);

        clock.setInstant(STARTED.plus(Duration.ofHours(24)).plusSeconds(1));
        JobResult result = job.runOnce();

        assertThat(result.failed()).isZero();
        assertThat(sessionStatus(sessionId)).isEqualTo("ABANDONED");
        assertThat(
                        jdbc.queryForObject(
                                "select completed_at at time zone 'UTC' from"
                                        + " devpilot.rubber_duck_session where id = ?::uuid",
                                String.class,
                                sessionId))
                .startsWith("2026-10-06 10:00:01");
        assertThat(fakeAi().callCount(AiOperation.RUBBER_DUCK_SUMMARY)).isEqualTo(summaryCalls);
        assertThat(
                        count(
                                "select count(*) from devpilot.review_item where user_id = ? and"
                                        + " source_type = 'RUBBER_DUCK'",
                                userId(user)))
                .isZero();
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_event where user_id = ? and"
                                        + " event_type = 'RUBBER_DUCK_COMPLETED'",
                                userId(user)))
                .isZero();
        assertThat(
                        count(
                                "select count(*) from devpilot.rubber_duck_turn where session_id ="
                                        + " ?::uuid",
                                sessionId))
                .isEqualTo(1);
    }

    @Test
    void shouldIgnoreSessionsThatAlreadyEnded() throws Exception {
        // SR-5
        TestUser user = onboardedOwner();
        String sessionId = start(user);
        api.post(user, SESSIONS + "/{sessionId}/abandon", null, sessionId)
                .andExpect(status().isOk());
        String completedAt = completedAt(sessionId);

        clock.setInstant(STARTED.plus(Duration.ofDays(3)));
        job.runOnce();

        assertThat(sessionStatus(sessionId)).isEqualTo("ABANDONED");
        assertThat(completedAt(sessionId)).isEqualTo(completedAt);
    }

    @Test
    void shouldAbandonStaleSessionsOfEveryUser() throws Exception {
        // SR-6
        TestUser first = onboardedOwner();
        TestUser second = onboardedOwner();
        String firstSession = start(first);
        String secondSession = start(second);

        clock.setInstant(STARTED.plus(Duration.ofHours(25)));
        JobResult result = job.runOnce();

        assertThat(result.processed()).isGreaterThanOrEqualTo(2);
        assertThat(sessionStatus(firstSession)).isEqualTo("ABANDONED");
        assertThat(sessionStatus(secondSession)).isEqualTo("ABANDONED");
    }

    private String start(TestUser user) throws Exception {
        clock.setInstant(STARTED);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("targetType", "CONCEPT");
        request.put("conceptKey", "SPRING.TRANSACTION.BOUNDARY");
        return api.body(api.post(user, SESSIONS, request)).path("session").path("id").asString();
    }

    private String sessionStatus(String sessionId) {
        return jdbc.queryForObject(
                "select status from devpilot.rubber_duck_session where id = ?::uuid",
                String.class,
                sessionId);
    }

    private String completedAt(String sessionId) {
        return jdbc.queryForObject(
                "select completed_at::text from devpilot.rubber_duck_session where id = ?::uuid",
                String.class,
                sessionId);
    }
}
