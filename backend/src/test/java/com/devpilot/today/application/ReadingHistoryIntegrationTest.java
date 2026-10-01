package com.devpilot.today.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 내가 받은 읽기 (docs/05 §19.14). 완료한 reading은 다음 제안에서 빠지므로(docs/06 §5.3), 이 목록이 없으면 읽었던 코드로 돌아갈 길이 없다
 * (2026-09-29 전수조사).
 */
@IntegrationTest
class ReadingHistoryIntegrationTest extends ApiTestSupport {

    private static final String LIST = "/api/v1/readings";
    private static final String CODE_READING = "READ.TESTREPO.ORDER_SERVICE.001";
    private static final String CONCEPT_READING = "DOC.TESTJAVA.EXCEPTION.001";

    @Test
    void shouldBeEmptyBeforeAnyReadingTask() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode body = api.body(api.get(user, LIST).andExpect(status().isOk()));

        assertThat(body.path("readings")).isEmpty();
    }

    /** 끝낸 읽기와 아직 안 끝낸 읽기가 모두 남고, 최근에 받은 것이 앞이다. */
    @Test
    void shouldKeepEveryReadingItEverHandedOutNewestFirst() throws Exception {
        TestUser user = onboardedOwner();
        String older = CODE_READING;
        String newer = CONCEPT_READING;
        giveReadingTask(user, older, LocalDate.parse("2026-09-10"), "COMPLETED");
        giveReadingTask(user, newer, LocalDate.parse("2026-09-20"), "PLANNED");

        JsonNode readings = api.body(api.get(user, LIST)).path("readings");

        assertThat(keys(readings)).containsExactly(newer, older);
        assertThat(readings.get(0).path("completed").asBoolean()).isFalse();
        assertThat(readings.get(1).path("completed").asBoolean()).isTrue();
        assertThat(readings.get(0).path("title").asString()).isNotEmpty();
        assertThat(readings.get(0).path("lastPlanDate").asString()).isEqualTo("2026-09-20");
    }

    /** 같은 읽기를 여러 번 받았으면 한 줄로 묶고 가장 최근 날짜를 쓴다. */
    @Test
    void shouldFoldRepeatsIntoOneLine() throws Exception {
        TestUser user = onboardedOwner();
        String key = CODE_READING;
        giveReadingTask(user, key, LocalDate.parse("2026-09-01"), "COMPLETED");
        giveReadingTask(user, key, LocalDate.parse("2026-09-15"), "PLANNED");

        JsonNode readings = api.body(api.get(user, LIST)).path("readings");

        assertThat(readings).hasSize(1);
        assertThat(readings.get(0).path("lastPlanDate").asString()).isEqualTo("2026-09-15");
        assertThat(readings.get(0).path("completed").asBoolean()).isTrue();
    }

    /** 남의 읽기 기록은 내 목록에 없다 (격리 catalog E85). */
    @Test
    void shouldNotShowSomeoneElsesReadings() throws Exception {
        TestUser owner = onboardedOwner();
        giveReadingTask(owner, CODE_READING, LocalDate.parse("2026-09-10"), "COMPLETED");
        TestUser other = TestUser.invited();
        api.onboard(other);

        JsonNode readings =
                api.body(api.get(other, LIST).andExpect(status().isOk())).path("readings");

        assertThat(readings).isEmpty();
    }

    /** 콘텐츠에서 사라진 key는 조용히 건너뛴다 — 목록이 깨지지 않는다. */
    @Test
    void shouldSkipAKeyThatIsNoLongerInTheCatalog() throws Exception {
        TestUser user = onboardedOwner();
        String known = CODE_READING;
        giveReadingTask(user, known, LocalDate.parse("2026-09-10"), "COMPLETED");
        giveReadingTask(user, "READ.GONE.999", LocalDate.parse("2026-09-20"), "COMPLETED");

        JsonNode readings = api.body(api.get(user, LIST)).path("readings");

        assertThat(keys(readings)).containsExactly(known);
    }

    /** {@code READ_CODE} 과제 한 건을 직접 넣는다. 제안 규칙을 거치지 않고 목록만 시험한다. */
    private void giveReadingTask(
            TestUser user, String readingKey, LocalDate planDate, String status) {
        UUID userId = userId(user);
        UUID planId = UUID.randomUUID();
        jdbc.update(
                "insert into devpilot.daily_plan (id, user_id, plan_date, available_minutes,"
                        + " energy_level) values (?, ?, ?, 60, 'NORMAL') on conflict do nothing",
                planId,
                userId,
                planDate);
        UUID existing =
                jdbc.queryForObject(
                        "select id from devpilot.daily_plan where user_id = ? and plan_date = ?",
                        UUID.class,
                        userId,
                        planDate);
        jdbc.update(
                "insert into devpilot.learning_task (id, daily_plan_id, user_id, task_type,"
                        + " reading_key, title, estimated_minutes, status, sort_order) values"
                        + " (gen_random_uuid(), ?, ?, 'READ_CODE', ?, '읽기', 20, ?, 0)",
                existing,
                userId,
                readingKey,
                status);
    }

    private static List<String> keys(JsonNode readings) {
        return readings.valueStream().map(item -> item.path("readingKey").asString()).toList();
    }
}
