package com.devpilot.skill.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.domain.SkillAxis;
import com.devpilot.testsupport.UnitTest;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 지금 잴 수 있는 축과 레벨 (docs/06 §7.6 MA-1~MA-5 · §7.6b EC-1·EC-11, ADR-061·ADR-070). */
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

    // --- ADR-070 레벨 상한 ---

    private static final AxisLevels CEILING = new AxisLevels(4, 3, 3, 0);

    private static MeasurableAxes withCeiling() {
        return new MeasurableAxes(EnumSet.allOf(SkillAxis.class), CEILING);
    }

    /** EC-1: 현재 판정 목표는 축별 min(원래 목표, 상한)이다. */
    @Test
    void shouldClampEachAxisToItsCeiling() {
        assertThat(withCeiling().forProgress(new AxisLevels(4, 4, 4, 3)))
                .isEqualTo(new AxisLevels(4, 3, 3, 0));
    }

    /** 상한 아래의 목표는 그대로다 — 상한은 목표를 올리지 않는다. */
    @Test
    void shouldNotRaiseATargetThatIsBelowTheCeiling() {
        assertThat(withCeiling().forProgress(new AxisLevels(2, 2, 1, 0)))
                .isEqualTo(new AxisLevels(2, 2, 1, 0));
    }

    /** 축 제외는 레벨 상한의 특수한 경우다 — 잴 수 없는 축은 상한과 무관하게 0이다. */
    @Test
    void shouldStillDropAnAxisThatCannotBeMeasuredAtAll() {
        MeasurableAxes axes =
                new MeasurableAxes(
                        EnumSet.of(
                                SkillAxis.KNOWLEDGE,
                                SkillAxis.IMPLEMENTATION,
                                SkillAxis.EXPLANATION),
                        new AxisLevels(4, 3, 3, 5));

        assertThat(axes.forProgress(new AxisLevels(4, 4, 4, 3)).debugging()).isZero();
    }

    /** 보류 몫은 상한 위에 남은 원래 목표다. 상한 이하인 축은 0 — 보류가 없다는 뜻이다. */
    @Test
    void shouldReportWhatIsPendingAboveTheCeiling() {
        assertThat(withCeiling().pendingAbove(new AxisLevels(4, 4, 4, 3)))
                .isEqualTo(new AxisLevels(0, 4, 4, 3));
    }

    @Test
    void shouldReportNothingPendingWhenEveryTargetIsBelowTheCeiling() {
        assertThat(withCeiling().pendingAbove(new AxisLevels(3, 3, 3, 0)))
                .isEqualTo(AxisLevels.ZERO);
    }

    /** EC-11: 축이 하나라도 빠지면 기동 실패다 — 빠진 축의 목표가 조용히 사라지면 안 된다. */
    @Test
    void shouldRefuseACeilingThatIsMissingAnAxis() {
        assertThatThrownBy(
                        () ->
                                MeasurableAxes.resolveCeiling(
                                        Map.of("KNOWLEDGE", 4, "IMPLEMENTATION", 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EXPLANATION");
    }

    /** EC-11: 모르는 축 이름도 기동 실패다. */
    @Test
    void shouldRefuseACeilingAxisNameThatDoesNotExist() {
        assertThatThrownBy(
                        () ->
                                MeasurableAxes.resolveCeiling(
                                        Map.of(
                                                "KNOWLEDGE", 4,
                                                "IMPLEMENTATION", 3,
                                                "EXPLANATION", 3,
                                                "DEBUGING", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEBUGING");
    }

    /** 설정 키는 kebab-case로도 쓸 수 있다 — Spring relaxed binding과 같은 모양이다. */
    @Test
    void shouldAcceptRelaxedAxisNames() {
        assertThat(
                        MeasurableAxes.resolveCeiling(
                                Map.of(
                                        "knowledge", 4,
                                        "implementation", 3,
                                        "explanation", 3,
                                        "debugging", 0)))
                .isEqualTo(CEILING);
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
