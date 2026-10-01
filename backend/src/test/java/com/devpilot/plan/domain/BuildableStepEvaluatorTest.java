package com.devpilot.plan.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.plan.domain.BuildableStepEvaluator.GateSkill;
import com.devpilot.plan.domain.BuildableStepEvaluator.StepInput;
import com.devpilot.plan.domain.BuildableStepEvaluator.StepReadiness;
import com.devpilot.testsupport.UnitTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 만드는 순서 중 어디까지 만들 수 있나 (docs/06 §11.4, ADR-060). BS-1~BS-5·BS-8·BS-9는 여기서, 관문을 고르는 BS-6·BS-7과
 * 자기평가를 빼는 BS-10은 {@code BuildableStepServiceIntegrationTest}에서 본다.
 */
@UnitTest
class BuildableStepEvaluatorTest {

    private static final UUID STEP_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STEP_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final AxisLevels TARGET = AxisLevels.uniform(3);

    /** BS-1: 관문이 없는 단계는 막는 것이 없다. */
    @Test
    void shouldBuildAStepWithoutGateSkills() {
        StepReadiness step = only(new StepInput(STEP_1, List.of()), Map.of());

        assertThat(step.status()).isEqualTo(BuildableStatus.BUILDABLE);
        assertThat(step.gateCount()).isZero();
        assertThat(step.gaps()).isEmpty();
    }

    /** BS-2: 관문이 모두 목표에 닿으면 만들 수 있다. */
    @Test
    void shouldBuildAStepWhenEveryGateSkillIsMet() {
        StepReadiness step =
                only(
                        step(STEP_1, "JAVA.OOP", "SPRING.MVC_REST"),
                        Map.of(
                                "JAVA.OOP", AxisLevels.uniform(3),
                                "SPRING.MVC_REST", AxisLevels.uniform(4)));

        assertThat(step.status()).isEqualTo(BuildableStatus.BUILDABLE);
        assertThat(step.metCount()).isEqualTo(2);
        assertThat(step.gateCount()).isEqualTo(2);
    }

    /** BS-3: 한 축이 한 칸 모자라도 아직이다 — 목표는 축별로 본다. */
    @Test
    void shouldNotBuildWhenOneAxisIsShort() {
        StepReadiness step =
                only(
                        step(STEP_1, "JAVA.OOP", "SPRING.MVC_REST"),
                        Map.of(
                                "JAVA.OOP",
                                AxisLevels.uniform(3),
                                "SPRING.MVC_REST",
                                new AxisLevels(3, 3, 3, 2)));

        assertThat(step.status()).isEqualTo(BuildableStatus.NEXT);
        assertThat(step.metCount()).isEqualTo(1);
        assertThat(step.gaps())
                .singleElement()
                .extracting(gap -> gap.skillCode())
                .isEqualTo("SPRING.MVC_REST");
    }

    /** BS-4: 앞 단계가 아직이어도 뒤 단계가 닿아 있으면 그대로 말한다 — 순서가 성적표는 아니다. */
    @Test
    void shouldStillCallALaterStepBuildable() {
        List<StepReadiness> steps =
                BuildableStepEvaluator.evaluate(
                        List.of(step(STEP_1, "JAVA.OOP"), step(STEP_2, "SPRING.MVC_REST")),
                        Map.of("SPRING.MVC_REST", AxisLevels.uniform(3)));

        assertThat(steps.get(0).status()).isEqualTo(BuildableStatus.NEXT);
        assertThat(steps.get(1).status()).isEqualTo(BuildableStatus.BUILDABLE);
    }

    /** BS-5: 지금 만들 차례는 하나뿐이다. 그 뒤로 미달인 단계는 전부 아직이다. */
    @Test
    void shouldMarkOnlyTheFirstUnmetStepAsNext() {
        List<StepReadiness> steps =
                BuildableStepEvaluator.evaluate(
                        List.of(step(STEP_1, "JAVA.OOP"), step(STEP_2, "SPRING.MVC_REST")),
                        Map.of());

        assertThat(steps.get(0).status()).isEqualTo(BuildableStatus.NEXT);
        assertThat(steps.get(1).status()).isEqualTo(BuildableStatus.NOT_YET);
    }

    /** BS-8: 근거 행이 없으면 4축 0으로 본다 — 없는 것은 0이지 면제가 아니다. */
    @Test
    void shouldCountAMissingSkillAsZero() {
        StepReadiness step = only(step(STEP_1, "JAVA.OOP"), Map.of());

        assertThat(step.status()).isEqualTo(BuildableStatus.NEXT);
        assertThat(step.gaps())
                .singleElement()
                .satisfies(
                        gap -> {
                            assertThat(gap.evidence()).isEqualTo(AxisLevels.ZERO);
                            assertThat(gap.shortfall()).isEqualTo(12);
                        });
    }

    /** BS-9: 많이 모자란 것부터 5개까지. 다 늘어놓으면 무엇부터 할지가 안 보인다. */
    @Test
    void shouldShowTheWidestGapsFirstAndAtMostFive() {
        List<GateSkill> gate =
                List.of(
                        new GateSkill("A.ONE", AxisLevels.uniform(1)),
                        new GateSkill("B.TWO", AxisLevels.uniform(2)),
                        new GateSkill("C.THREE", AxisLevels.uniform(3)),
                        new GateSkill("D.FOUR", AxisLevels.uniform(4)),
                        new GateSkill("E.FIVE", AxisLevels.uniform(5)),
                        new GateSkill("F.SIX", AxisLevels.uniform(1)));

        StepReadiness step = only(new StepInput(STEP_1, gate), Map.of());

        assertThat(step.gaps()).hasSize(5);
        assertThat(step.gaps().stream().map(BuildableStepEvaluator.Gap::skillCode))
                .containsExactly("E.FIVE", "D.FOUR", "C.THREE", "B.TWO", "A.ONE");
        assertThat(step.metCount()).isZero();
        assertThat(step.gateCount()).isEqualTo(6);
    }

    private static StepInput step(UUID milestoneId, String... skillCodes) {
        return new StepInput(
                milestoneId,
                java.util.Arrays.stream(skillCodes)
                        .map(code -> new GateSkill(code, TARGET))
                        .toList());
    }

    private static StepReadiness only(StepInput step, Map<String, AxisLevels> evidence) {
        return BuildableStepEvaluator.evaluate(List.of(step), evidence).get(0);
    }
}
