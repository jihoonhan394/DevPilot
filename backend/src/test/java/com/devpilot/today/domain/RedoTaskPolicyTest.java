package com.devpilot.today.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import com.devpilot.testsupport.VectorLoader;
import com.devpilot.today.domain.RedoTaskPolicy.RedoAttempt;
import com.devpilot.today.domain.RedoTaskPolicy.RedoCandidate;
import com.devpilot.today.domain.RedoTaskPolicy.RedoOrigin;
import com.devpilot.today.domain.RedoTaskPolicy.RedoSettings;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** docs/06 §5.10 RE-1~RE-4의 test vector 전부 (RE-V1~RE-V11). */
@UnitTest
class RedoTaskPolicyTest {

    private static final String VECTOR_FILE = "06-05-redo-candidate.yaml";
    private static final LocalDate TODAY = LocalDate.parse("2026-10-20");

    private final RedoTaskPolicy policy = new RedoTaskPolicy(new RedoSettings(3, 7, 2));

    @ParameterizedTest(name = "[{index}] {0} {1}")
    @MethodSource("vectors")
    void shouldMatchVectorWhenRedoCandidatesAreSelected(
            String id, String caseName, Map<String, Object> row) {
        Map<String, UUID> ids = new LinkedHashMap<>();
        List<RedoOrigin> originals = originals(row, ids);
        List<RedoAttempt> redos = redos(row, ids);

        Map<String, RedoCandidate> selected = policy.selectBySkill(TODAY, originals, redos);

        List<String> expected = expected(row);
        assertThat(selected.values())
                .as(id + " " + caseName)
                .extracting(candidate -> nameOf(ids, candidate.origin().taskId()))
                .containsExactlyElementsOf(expected);
    }

    /** 같은 skill에 여럿이면 하나만 남는다 — vector RE-V10의 경계를 한 번 더 못박는다. */
    @Test
    void shouldKeepOneCandidatePerSkill() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        List<RedoOrigin> originals =
                List.of(
                        origin(second, "S", LocalDate.parse("2026-10-16"), 30),
                        origin(first, "S", LocalDate.parse("2026-10-15"), 35));

        Map<String, RedoCandidate> selected = policy.selectBySkill(TODAY, originals, List.of());

        assertThat(selected).hasSize(1);
        assertThat(selected.get("S").origin().taskId()).isEqualTo(first);
        // 문구에 쓸 "며칠 전"은 원본을 끝낸 날부터 센다 (docs/06 §5.3)
        assertThat(selected.get("S").daysAfter()).isEqualTo(5);
    }

    /** DEFERRED는 아직 해 보지 않은 것이라 시도로 세지 않는다 (RE-3). */
    @Test
    void shouldNotCountDeferredRedoAsAnAttempt() {
        UUID taskId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        List<RedoOrigin> originals =
                List.of(origin(taskId, "S", LocalDate.parse("2026-10-16"), 35));
        List<RedoAttempt> deferredTwice =
                List.of(
                        new RedoAttempt(taskId, TaskStatus.DEFERRED, null, null),
                        new RedoAttempt(taskId, TaskStatus.DEFERRED, null, null));

        assertThat(policy.selectBySkill(TODAY, originals, deferredTwice)).hasSize(1);
    }

    static Stream<Arguments> vectors() {
        return VectorLoader.yamlRows(VECTOR_FILE).stream()
                .map(row -> Arguments.of(row.get("id"), row.get("case"), row));
    }

    private static RedoOrigin origin(
            UUID taskId, String skill, LocalDate completedOn, int minutes) {
        return new RedoOrigin(
                taskId, skill, TaskType.CHALLENGE, "원본", "설명", minutes, 2, completedOn);
    }

    @SuppressWarnings("unchecked")
    private static List<RedoOrigin> originals(Map<String, Object> row, Map<String, UUID> ids) {
        List<RedoOrigin> originals = new ArrayList<>();
        for (Object item : (List<Object>) row.get("originals")) {
            Map<String, Object> values = (Map<String, Object>) item;
            String name = (String) values.get("id");
            UUID taskId = ids.computeIfAbsent(name, key -> UUID.randomUUID());
            originals.add(
                    new RedoOrigin(
                            taskId,
                            (String) values.get("skill"),
                            TaskType.valueOf((String) values.get("type")),
                            "원본 " + name,
                            "설명",
                            (Integer) values.get("estimated"),
                            (Integer) values.get("difficulty"),
                            LocalDate.parse((String) values.get("completedOn"))));
        }
        return originals;
    }

    @SuppressWarnings("unchecked")
    private static List<RedoAttempt> redos(Map<String, Object> row, Map<String, UUID> ids) {
        List<RedoAttempt> redos = new ArrayList<>();
        for (Object item : (List<Object>) row.get("redos")) {
            Map<String, Object> values = (Map<String, Object>) item;
            String completedOn = (String) values.get("completedOn");
            redos.add(
                    new RedoAttempt(
                            ids.get((String) values.get("source")),
                            TaskStatus.valueOf((String) values.get("status")),
                            completedOn == null ? null : LocalDate.parse(completedOn),
                            (Boolean) values.get("withoutAi")));
        }
        return redos;
    }

    @SuppressWarnings("unchecked")
    private static List<String> expected(Map<String, Object> row) {
        return (List<String>) row.get("expected");
    }

    private static String nameOf(Map<String, UUID> ids, UUID taskId) {
        return ids.entrySet().stream()
                .filter(entry -> entry.getValue().equals(taskId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();
    }
}
