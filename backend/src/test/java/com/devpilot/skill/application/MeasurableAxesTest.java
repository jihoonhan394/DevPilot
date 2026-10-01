package com.devpilot.skill.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.domain.SkillAxis;
import com.devpilot.testsupport.UnitTest;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 지금 잴 수 있는 축 (docs/06 §7.6 MA-1~MA-5, ADR-061). */
@UnitTest
class MeasurableAxesTest {

    private static final AxisLevels TARGETS = new AxisLevels(3, 3, 3, 2);

    private static MeasurableAxes withoutDebugging() {
        return new MeasurableAxes(
                EnumSet.of(SkillAxis.KNOWLEDGE, SkillAxis.IMPLEMENTATION, SkillAxis.EXPLANATION));
    }

    /** MA-1: 잴 수 없는 축은 목표에서 0으로 내린다. */
    @Test
    void shouldDropTheTargetOfAnAxisThatCannotBeMeasured() {
        assertThat(withoutDebugging().forProgress(TARGETS)).isEqualTo(new AxisLevels(3, 3, 3, 0));
    }

    /** MA-2: 그래서 디버깅이 0이어도 목표에 닿는다 — 지금은 그 축을 쌓을 길이 없다. */
    @Test
    void shouldLetASkillReachItsTargetWithoutTheUnmeasuredAxis() {
        AxisLevels evidence = new AxisLevels(3, 3, 3, 0);
        AxisLevels forProgress = withoutDebugging().forProgress(TARGETS);

        assertThat(evidence.knowledge()).isGreaterThanOrEqualTo(forProgress.knowledge());
        assertThat(evidence.debugging()).isGreaterThanOrEqualTo(forProgress.debugging());
    }

    /** MA-3: 잴 수 있는 축이 모자라면 그대로 미달이다 — 문을 열어 준 것이 아니다. */
    @Test
    void shouldStillRequireTheAxesThatCanBeMeasured() {
        AxisLevels forProgress = withoutDebugging().forProgress(TARGETS);

        assertThat(forProgress.implementation()).isEqualTo(3);
    }

    /** MA-4: 네 축을 다 잴 수 있으면 목표는 그대로다. */
    @Test
    void shouldLeaveTargetsAloneWhenEveryAxisCanBeMeasured() {
        MeasurableAxes all = new MeasurableAxes(EnumSet.allOf(SkillAxis.class));

        assertThat(all.forProgress(TARGETS)).isEqualTo(TARGETS);
        assertThat(all.unmeasured()).isEmpty();
    }

    /** MA-5: 비어 있으면 기동 실패다 — 아무 축도 안 세면 모든 것이 끝난 것이 된다. */
    @Test
    void shouldRefuseAnEmptyAxisSet() {
        assertThatThrownBy(() -> new MeasurableAxes(Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("measurable-axes");
    }

    /** MA-5: 모르는 축 이름도 기동 실패다 — 오타 하나로 축이 조용히 빠지면 안 된다. */
    @Test
    void shouldRefuseAnAxisNameThatDoesNotExist() {
        assertThatThrownBy(() -> MeasurableAxes.resolve(Set.of("KNOWLEDGE", "DEBUGING")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEBUGING");
    }

    @Test
    void shouldReportTheAxesItCountsAndTheOnesItDoesNot() {
        MeasurableAxes axes = withoutDebugging();

        assertThat(axes.axes())
                .containsExactly(
                        SkillAxis.KNOWLEDGE, SkillAxis.IMPLEMENTATION, SkillAxis.EXPLANATION);
        assertThat(axes.unmeasured()).containsExactly(SkillAxis.DEBUGGING);
    }
}
