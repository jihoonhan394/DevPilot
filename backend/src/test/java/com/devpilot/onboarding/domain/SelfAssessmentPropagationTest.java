package com.devpilot.onboarding.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.onboarding.domain.SelfAssessmentPropagation.InitialState;
import com.devpilot.onboarding.domain.SelfAssessmentPropagation.TargetedSkill;
import com.devpilot.skill.domain.SkillCategory;
import com.devpilot.testsupport.UnitTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** docs/05 §4.1 6단계: category 자기평가 값을 그 category의 모든 목표 skill에 복사한다. 진단 모드는 null. */
@UnitTest
class SelfAssessmentPropagationTest {

    private static final UUID JAVA_SKILL = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID SPRING_SKILL =
            UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID TESTING_SKILL =
            UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private final SelfAssessmentPropagation propagation = new SelfAssessmentPropagation();

    private final List<TargetedSkill> skills =
            List.of(
                    new TargetedSkill(JAVA_SKILL, SkillCategory.JAVA),
                    new TargetedSkill(SPRING_SKILL, SkillCategory.SPRING),
                    new TargetedSkill(TESTING_SKILL, SkillCategory.TESTING));

    @Test
    void shouldCopyCategoryLevelWhenSelfAssessmentMode() {
        List<InitialState> states =
                propagation.propagate(
                        skills, Map.of(SkillCategory.JAVA, 3, SkillCategory.SPRING, 0), false);

        assertThat(states)
                .containsExactly(
                        new InitialState(JAVA_SKILL, 3),
                        new InitialState(SPRING_SKILL, 0),
                        new InitialState(TESTING_SKILL, null));
    }

    @Test
    void shouldLeaveEveryLevelNullWhenDiagnosticMode() {
        List<InitialState> states =
                propagation.propagate(skills, Map.of(SkillCategory.JAVA, 3), true);

        assertThat(states).extracting(InitialState::selfAssessedLevel).containsOnlyNulls();
    }
}
