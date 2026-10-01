package com.devpilot.plan.domain;

import com.devpilot.common.web.AxisLevels;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 만들 수 있는 단계 판정 (docs/06 §11.4, ADR-060). 순수 규칙이다 — 입력은 단계별 관문 skill과 <b>근거 레벨</b>뿐이다.
 *
 * <p>자기평가는 세지 않는다. 이 화면은 "안다고 답한 것"이 아니라 "직접 해본 기록"으로 무엇을 만들 수 있는지에 답한다. 그래서 대시보드 타임라인의 "지금 단계"(계획
 * 레벨, ADR-044)와 다를 수 있고, 그 차이가 이 화면이 하는 말이다.
 */
public final class BuildableStepEvaluator {

    /** 한 단계에서 모자란 skill을 보여주는 상한. 다 늘어놓으면 무엇부터 할지가 안 보인다. */
    private static final int GAP_LIMIT = 5;

    private BuildableStepEvaluator() {}

    /**
     * 단계를 {@code sortOrder} 순으로 판정한다.
     *
     * @param steps sortOrder ASC로 정렬되어 들어온다
     * @param evidenceByCode skill code별 근거 레벨. 없는 code는 4축 0으로 본다
     */
    public static List<StepReadiness> evaluate(
            List<StepInput> steps, Map<String, AxisLevels> evidenceByCode) {
        List<StepReadiness> readiness = new ArrayList<>(steps.size());
        boolean nextTaken = false;
        for (StepInput step : steps) {
            List<Gap> gaps = gaps(step, evidenceByCode);
            BuildableStatus status;
            if (gaps.isEmpty()) {
                status = BuildableStatus.BUILDABLE;
            } else if (nextTaken) {
                status = BuildableStatus.NOT_YET;
            } else {
                status = BuildableStatus.NEXT;
                nextTaken = true;
            }
            readiness.add(
                    new StepReadiness(
                            step.milestoneId(),
                            status,
                            step.gate().size() - gaps.size(),
                            step.gate().size(),
                            gaps.stream().limit(GAP_LIMIT).toList()));
        }
        return List.copyOf(readiness);
    }

    /** 모자란 관문 skill. 모자란 칸이 많은 순, 같으면 code ASC. */
    private static List<Gap> gaps(StepInput step, Map<String, AxisLevels> evidenceByCode) {
        List<Gap> gaps = new ArrayList<>();
        for (GateSkill gate : step.gate()) {
            AxisLevels evidence = evidenceByCode.getOrDefault(gate.skillCode(), AxisLevels.ZERO);
            int shortfall = shortfall(evidence, gate.targets());
            if (shortfall > 0) {
                gaps.add(new Gap(gate.skillCode(), evidence, gate.targets(), shortfall));
            }
        }
        gaps.sort(Comparator.comparingInt(Gap::shortfall).reversed().thenComparing(Gap::skillCode));
        return gaps;
    }

    /** 네 축에서 모자란 칸 수의 합. 0이면 그 skill은 목표에 닿았다. */
    private static int shortfall(AxisLevels evidence, AxisLevels targets) {
        return Math.max(0, targets.knowledge() - evidence.knowledge())
                + Math.max(0, targets.implementation() - evidence.implementation())
                + Math.max(0, targets.explanation() - evidence.explanation())
                + Math.max(0, targets.debugging() - evidence.debugging());
    }

    /**
     * 한 단계의 입력.
     *
     * @param gate 이 단계를 여는 skill들. 비어 있으면 그 단계는 막는 것이 없어 {@code BUILDABLE}이다
     */
    public record StepInput(UUID milestoneId, List<GateSkill> gate) {

        public StepInput {
            gate = List.copyOf(gate);
        }
    }

    /** 관문 skill 1개와 그 목표 레벨. */
    public record GateSkill(String skillCode, AxisLevels targets) {}

    /**
     * 아직 목표에 닿지 않은 관문 skill 1개.
     *
     * @param shortfall 네 축에서 모자란 칸 수의 합
     */
    public record Gap(String skillCode, AxisLevels evidence, AxisLevels targets, int shortfall) {}

    /**
     * 한 단계의 판정.
     *
     * @param gaps 모자란 skill, 많이 모자란 순으로 최대 {@value #GAP_LIMIT}개
     */
    public record StepReadiness(
            UUID milestoneId, BuildableStatus status, int metCount, int gateCount, List<Gap> gaps) {

        public StepReadiness {
            gaps = List.copyOf(gaps);
        }
    }
}
