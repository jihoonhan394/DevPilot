package com.devpilot.today.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 닷새를 실제로 살아 보는 시뮬레이션 (docs/06 §5.13, ADR-053).
 *
 * <p>규칙 하나하나는 vector가 덮는다. 여기서 보는 것은 <b>여러 날을 이어 붙였을 때 무엇이 쌓이는가</b>다 — 매일 main을 받아 끝내고, 그 결과가 다음 날
 * 제안과 학습 상태에 어떻게 반영되는지를 한 줄씩 기록한다.
 *
 * <p>측정하는 값 세 가지 (실력 향상을 데이터로 보려면 이 셋이 있어야 한다):
 *
 * <ul>
 *   <li><b>이어감</b> — 닷새 동안 몇 개의 skill에 손댔나. 적을수록 한 주제를 끝까지 간 것이다
 *   <li><b>가르친 뒤 시험했나</b> — 문제(CHALLENGE)가 나오기 전에 그 skill의 개념을 뗐나
 *   <li><b>쌓인 것</b> — 마친 학습 단위 수와 그 결과 오른 planning level
 * </ul>
 */
@IntegrationTest
class FiveDayStudyThreadSimulationTest extends ApiTestSupport {

    private static final String UNIT = "/api/v1/lessons/{lessonKey}/units/{unitKey}";
    private static final String TASKS = "/api/v1/today/tasks/{taskId}";
    private static final int DAYS = 5;
    private static final int AVAILABLE_MINUTES = 30;

    @Test
    void shouldStayOnOneTopicUntilItsNoteIsDoneAndOnlyThenTest() throws Exception {
        TestUser user = onboardedOwner();
        List<Day> log = new ArrayList<>();

        for (int day = 1; day <= DAYS; day++) {
            JsonNode main = api.generateToday(user, AVAILABLE_MINUTES, "NORMAL").path("mainTask");
            if (main.isNull() || main.isMissingNode()) {
                log.add(Day.empty(day));
                clock.advance(Duration.ofDays(1));
                continue;
            }
            Day today = Day.of(day, main);
            finishMain(user, main);
            log.add(today.withSolved(solvedUnitCount(user)));
            clock.advance(Duration.ofDays(1));
        }

        Metrics metrics = metrics(user, log);
        print(log, metrics);

        // docs/06 §12 "배우고 있나를 보는 네 지표"
        assertThat(metrics.learnedUnitCount()).as("배운 단위가 하나도 없다").isPositive();
        assertThat(metrics.topicSwitchesPerWeek()).as("주제가 너무 자주 바뀐다").isLessThanOrEqualTo(2);

        // ① 이어감: 닷새에 손댄 skill이 셋을 넘지 않는다. ADR-053 전에는 FATIGUE가 매일 주제를 바꿨다
        assertThat(distinctSkills(log)).isLessThanOrEqualTo(3);

        // ② 가르친 뒤 시험한다: 첫 CHALLENGE 앞에 같은 skill의 개념 익히기가 있었다
        Day firstChallenge =
                log.stream()
                        .filter(entry -> "CHALLENGE".equals(entry.taskType()))
                        .findFirst()
                        .orElse(null);
        if (firstChallenge != null) {
            assertThat(taughtBefore(log, firstChallenge))
                    .as("CHALLENGE %s 앞에 같은 skill의 개념 익히기가 있어야 한다", firstChallenge.skillCode())
                    .isTrue();
        }

        // ③ 쌓인 것: 마친 단위가 닷새 내내 늘기만 한다 (줄어들면 진행을 잃은 것이다)
        assertThat(solvedUnitCount(user)).isPositive();
        assertThat(log.stream().map(Day::solvedUnits).toList()).isSorted();
    }

    /** docs/06 §12의 네 지표를 그대로 계산한다. {@code MetricsCalculator}(S6)가 생기기 전까지 이 값의 실행 가능한 정의다. */
    private Metrics metrics(TestUser user, List<Day> log) {
        Integer learned =
                jdbc.queryForObject(
                        """
                        select count(distinct (payload ->> 'lessonKey', payload ->> 'unitKey'))
                          from devpilot.learning_event
                         where user_id = ?::uuid and event_type = 'UNIT_SOLVED'
                        """,
                        Integer.class,
                        userId(user).toString());
        int switches = 0;
        String previous = null;
        for (Day entry : log) {
            if (entry.skillCode().isEmpty()) {
                continue;
            }
            if (previous != null && !previous.equals(entry.skillCode())) {
                switches++;
            }
            previous = entry.skillCode();
        }
        long taughtDays = log.stream().filter(Day::isLesson).count();
        return new Metrics(
                learned == null ? 0 : learned,
                switches * 7.0 / DAYS,
                (int) taughtDays,
                distinctSkills(log));
    }

    /** 닷새 요약. docs/06 §12의 이름을 그대로 쓴다. */
    private record Metrics(
            int learnedUnitCount, double topicSwitchesPerWeek, int lessonDays, int touchedSkills) {}

    /** 그날 main을 실제로 끝낸다. 개념 익히기면 낸 단위를 풀고, 그 밖이면 상태만 옮긴다. */
    private void finishMain(TestUser user, JsonNode main) throws Exception {
        String taskId = main.path("id").asString();
        String readingKey = main.path("readingKey").asString();
        api.patch(user, TASKS, Map.of("status", "IN_PROGRESS", "version", 0), taskId)
                .andExpect(status().isOk());
        if (readingKey.startsWith("LESSON.")) {
            solveNextUnit(user, readingKey);
        }
        satisfyCodeReadingCondition(user, main);
        Map<String, Object> complete = new LinkedHashMap<>();
        complete.put("status", "COMPLETED");
        complete.put("version", 1);
        if ("REDO".equals(main.path("taskType").asString())) {
            // 재현 과제는 답이 있어야 완료된다 (docs/06 §5.10 RE-6). 혼자 해냈다고 답한다
            complete.put("redoWithoutAi", true);
        }
        api.patch(user, TASKS, complete, taskId).andExpect(status().isOk());
    }

    /** 그 노트의 아직 안 푼 첫 단위를 푼다 (docs/05 §21.7). 도움 없이 푼 것으로 기록한다. */
    private void solveNextUnit(TestUser user, String lessonKey) throws Exception {
        JsonNode lesson = api.body(api.get(user, "/api/v1/lessons/{key}", lessonKey));
        for (JsonNode unit : lesson.path("units")) {
            if (!unit.path("progress").path("solved").asBoolean(false)) {
                Map<String, Object> request = new LinkedHashMap<>();
                request.put("helpLevel", "NONE");
                request.put("selfChecksMet", null);
                api.postWithKey(
                                user,
                                UUID.randomUUID().toString(),
                                UNIT + "/finish",
                                request,
                                lessonKey,
                                unit.path("unitKey").asString())
                        .andExpect(status().isOk());
                return;
            }
        }
    }

    private int solvedUnitCount(TestUser user) {
        Integer count =
                jdbc.queryForObject(
                        """
                        select count(distinct payload ->> 'unitKey')
                          from devpilot.learning_event
                         where user_id = ?::uuid and event_type = 'UNIT_SOLVED'
                        """,
                        Integer.class,
                        userId(user).toString());
        return count == null ? 0 : count;
    }

    private static int distinctSkills(List<Day> log) {
        return (int)
                log.stream().map(Day::skillCode).filter(code -> !code.isEmpty()).distinct().count();
    }

    /** 그 CHALLENGE 이전에 같은 skill의 개념 익히기(READING + LESSON key)가 있었나. */
    private static boolean taughtBefore(List<Day> log, Day challenge) {
        for (Day entry : log) {
            if (entry.day() >= challenge.day()) {
                return false;
            }
            if (entry.skillCode().equals(challenge.skillCode()) && entry.isLesson()) {
                return true;
            }
        }
        return false;
    }

    private static void print(List<Day> log, Metrics metrics) {
        StringBuilder table = new StringBuilder("\n닷새 시뮬레이션 (docs/06 §5.13)\n");
        table.append(
                "day | skill                | type      | 자료                         | 마친 단위\n");
        for (Day entry : log) {
            table.append(
                    String.format(
                            "%3d | %-20s | %-9s | %-28s | %d%n",
                            entry.day(),
                            entry.skillCode(),
                            entry.taskType(),
                            entry.readingKey(),
                            entry.solvedUnits()));
        }
        table.append("\n지표 (docs/06 §12)\n")
                .append("  learnedUnitCount      ")
                .append(metrics.learnedUnitCount())
                .append("\n  topicSwitchesPerWeek  ")
                .append(String.format("%.1f", metrics.topicSwitchesPerWeek()))
                .append("\n  개념 익히기를 한 날      ")
                .append(metrics.lessonDays())
                .append(" / ")
                .append(DAYS)
                .append("\n  손댄 skill 수           ")
                .append(metrics.touchedSkills())
                .append('\n');
        System.out.println(table);
    }

    /** 하루의 기록. */
    private record Day(
            int day, String skillCode, String taskType, String readingKey, int solvedUnits) {

        static Day of(int day, JsonNode main) {
            return new Day(
                    day,
                    main.path("skillCode").asString(""),
                    main.path("taskType").asString(""),
                    main.path("readingKey").asString(""),
                    0);
        }

        static Day empty(int day) {
            return new Day(day, "", "(없음)", "", 0);
        }

        Day withSolved(int solved) {
            return new Day(day, skillCode, taskType, readingKey, solved);
        }

        boolean isLesson() {
            return readingKey.startsWith("LESSON.");
        }
    }
}
