package com.devpilot.today.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import com.devpilot.today.domain.Checklist;
import com.devpilot.today.domain.TaskType;
import java.util.List;
import org.junit.jupiter.api.Test;

/** docs/19 §3.11: 등록은 key ASC로 하고, 맞는 것이 여럿이면 첫 번째 하나만 붙는다. skill이 없는 과제에는 붙지 않는다. */
@UnitTest
class ChecklistRegistryTest {

    private final ChecklistRegistry registry = new ChecklistRegistry();

    @Test
    void shouldBeEmptyBeforeRegistration() {
        assertThat(registry.all()).isEmpty();
        assertThat(registry.find(TaskType.CHALLENGE, "JAVA.EXCEPTION")).isEmpty();
    }

    @Test
    void shouldOrderByKeyAscending() {
        registry.register(List.of(checklist("CHK.B.ONE"), checklist("CHK.A.ONE")));

        assertThat(registry.all())
                .extracting(Checklist::key)
                .containsExactly("CHK.A.ONE", "CHK.B.ONE");
    }

    @Test
    void shouldAttachTheFirstMatchByKeyWhenTwoChecklistsMatch() {
        registry.register(List.of(checklist("CHK.B.ONE"), checklist("CHK.A.ONE")));

        assertThat(registry.find(TaskType.CHALLENGE, "JAVA.EXCEPTION"))
                .get()
                .extracting(Checklist::key)
                .isEqualTo("CHK.A.ONE");
    }

    @Test
    void shouldNotAttachWhenTaskTypeOrSkillDoesNotMatch() {
        registry.register(List.of(checklist("CHK.A.ONE")));

        assertThat(registry.find(TaskType.REVIEW, "JAVA.EXCEPTION")).isEmpty();
        assertThat(registry.find(TaskType.CHALLENGE, "JAVA.COLLECTION")).isEmpty();
    }

    @Test
    void shouldNotAttachWhenTaskHasNoSkill() {
        registry.register(List.of(checklist("CHK.A.ONE")));

        assertThat(registry.find(TaskType.CHALLENGE, null)).isEmpty();
    }

    private static Checklist checklist(String key) {
        return new Checklist(
                key,
                List.of(TaskType.CHALLENGE, TaskType.PROJECT_TASK),
                List.of("JAVA.EXCEPTION"),
                List.of("첫 번째 확인입니다", "두 번째 확인입니다", "세 번째 확인입니다"),
                List.of("네 번째 확인입니다", "다섯 번째 확인입니다", "여섯 번째 확인입니다"));
    }
}
