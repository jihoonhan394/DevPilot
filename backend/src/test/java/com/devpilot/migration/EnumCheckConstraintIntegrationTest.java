package com.devpilot.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.learning.domain.EventSourceType;
import com.devpilot.learning.domain.LearningEventType;
import com.devpilot.learning.domain.TipFeedback;
import com.devpilot.project.domain.SideProjectKind;
import com.devpilot.project.domain.SideProjectNoteType;
import com.devpilot.project.domain.SideProjectStatus;
import com.devpilot.review.domain.ReviewItemSourceType;
import com.devpilot.skill.domain.SkillCategory;
import com.devpilot.skill.domain.TargetRole;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.today.domain.ReadingFeedback;
import com.devpilot.today.domain.TaskStatus;
import com.devpilot.today.domain.TaskType;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * docs/04 §3: enum 레지스트리가 유일한 기준이고 Java enum과 DB CHECK가 같은 값을 쓴다. 전체 migration(V10 포함)을 적용한 DB의 값
 * CHECK를 읽어 Java enum과 1:1인지 본다 — 새 값을 enum에만 넣고 migration을 빠뜨리면 여기서 잡힌다.
 */
@IntegrationTest
class EnumCheckConstraintIntegrationTest {

    private static final Pattern LITERAL = Pattern.compile("'([^']*)'");

    @Autowired private JdbcTemplate jdbcTemplate;

    static List<Arguments> enumColumns() {
        return List.of(
                Arguments.of("learning_goal", "target_role", TargetRole.class),
                Arguments.of("role_skill_target", "target_role", TargetRole.class),
                Arguments.of("skill", "category", SkillCategory.class),
                Arguments.of("learning_task", "task_type", TaskType.class),
                Arguments.of("learning_task", "status", TaskStatus.class),
                Arguments.of("learning_task", "reading_feedback", ReadingFeedback.class),
                Arguments.of("learning_event", "event_type", LearningEventType.class),
                Arguments.of("learning_event", "source_type", EventSourceType.class),
                Arguments.of("review_item", "source_type", ReviewItemSourceType.class),
                Arguments.of("side_project", "status", SideProjectStatus.class),
                Arguments.of("side_project", "kind", SideProjectKind.class),
                Arguments.of("side_project_note", "note_type", SideProjectNoteType.class),
                Arguments.of("user_daily_tip", "feedback", TipFeedback.class));
    }

    @ParameterizedTest(name = "[{index}] {0}.{1}")
    @MethodSource("enumColumns")
    void shouldAllowExactlyTheEnumValues(
            String table, String column, Class<? extends Enum<?>> type) {
        String definition = constraintDefinition(table, column);

        assertThat(literals(definition))
                .as(table + "." + column)
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(type.getEnumConstants()).map(Enum::name).toList());
    }

    private String constraintDefinition(String table, String column) {
        String name = table + "_" + column + "_check";
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject(
                        "select pg_get_constraintdef(oid) from pg_constraint"
                                + " where connamespace = 'devpilot'::regnamespace and conname = ?",
                        String.class,
                        name),
                name);
    }

    private static List<String> literals(String definition) {
        Matcher matcher = LITERAL.matcher(definition);
        return matcher.results().map(result -> result.group(1)).distinct().toList();
    }
}
