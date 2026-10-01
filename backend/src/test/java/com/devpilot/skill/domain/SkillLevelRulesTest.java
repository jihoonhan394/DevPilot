package com.devpilot.skill.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.skill.domain.SkillLevelRules.LevelChange;
import com.devpilot.skill.domain.SkillLevelRules.Outcome;
import com.devpilot.skill.domain.SkillLevelRules.RuleInput;
import com.devpilot.testsupport.TestRuleSettings;
import com.devpilot.testsupport.UnitTest;
import com.devpilot.testsupport.VectorLoader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * docs/06 §7.1~§7.4 (BL-SKL-04): §7.6 vector #1~#12·#15 전부. #13·#14는 {@code
 * PlanningLevelPolicyTest}.
 */
@UnitTest
class SkillLevelRulesTest {

    private static final String FILE = "06-07-skill-level.yaml";
    private static final Instant NOW = Instant.parse("2026-10-15T09:00:00Z");

    private final SkillLevelRules rules = new SkillLevelRules(TestRuleSettings.skillLevel());

    static Stream<Map<String, Object>> vectors() {
        return VectorLoader.yamlRows(FILE).stream();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("vectors")
    void shouldApplyRulesWhenVectorIsApplied(Map<String, Object> vector) {
        List<RuleEvent> events = events(vector);
        UUID trigger = events.isEmpty() ? null : events.getFirst().id();

        Outcome outcome =
                rules.evaluate(
                        new RuleInput(
                                levels((String) vector.get("current")),
                                events,
                                trigger,
                                changedAt(vector),
                                LocalDate.parse((String) vector.get("today")),
                                NOW));

        assertThat(describe(outcome.changes()))
                .as("%s", vector.get("case"))
                .isEqualTo(expectedChanges(vector));
        assertThat(outcome.deactivateSelfAssessment())
                .isEqualTo(Boolean.TRUE.equals(vector.get("expectedDeactivate")));
    }

    private static List<String> describe(List<LevelChange> changes) {
        return changes.stream()
                .map(
                        change ->
                                change.axis()
                                        + " "
                                        + change.fromLevel()
                                        + "->"
                                        + change.toLevel()
                                        + " "
                                        + change.ruleCode())
                .toList();
    }

    private static List<String> expectedChanges(Map<String, Object> vector) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> expected =
                (List<Map<String, Object>>) vector.get("expectedChanges");
        return expected.stream()
                .map(
                        change ->
                                change.get("axis")
                                        + " "
                                        + change.get("from")
                                        + "->"
                                        + change.get("to")
                                        + " "
                                        + change.get("ruleCode"))
                .toList();
    }

    private static List<RuleEvent> events(Map<String, Object> vector) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) vector.get("events");
        List<RuleEvent> events = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) row.get("payload");
            events.add(
                    new RuleEvent(
                            UUID.randomUUID(),
                            LearningEventType.valueOf((String) row.get("type")),
                            LocalDate.parse((String) row.get("planDate")),
                            NOW.minus((Integer) row.get("hoursAgo"), ChronoUnit.HOURS),
                            payload));
        }
        return List.copyOf(events);
    }

    private static Map<SkillAxis, Instant> changedAt(Map<String, Object> vector) {
        @SuppressWarnings("unchecked")
        Map<String, Integer> rows = (Map<String, Integer>) vector.get("changedAtHoursAgo");
        Map<SkillAxis, Instant> changedAt = new EnumMap<>(SkillAxis.class);
        if (rows != null) {
            rows.forEach(
                    (axis, hours) ->
                            changedAt.put(
                                    SkillAxis.valueOf(axis), NOW.minus(hours, ChronoUnit.HOURS)));
        }
        return changedAt;
    }

    private static AxisLevels levels(String value) {
        String[] parts = value.split("\\|");
        return new AxisLevels(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]));
    }
}
