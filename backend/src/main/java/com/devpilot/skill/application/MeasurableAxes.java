package com.devpilot.skill.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.domain.SkillAxis;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 지금 근거를 쌓을 수 있는 범위 (docs/06 §7.6·§7.6b, ADR-061·ADR-070). {@code devpilot.skill.measurable-axes}가
 * <b>축</b>을, {@code devpilot.skill.evidence-ceiling}이 <b>축 안의 레벨</b>을 정한다. 진도 판정에 들어가는 목표를 그 범위로
 * 낮춘다.
 *
 * <p>DEBUGGING은 {@code COACH_*} 이벤트로만 오르는데(docs/06 §7.2) coach 모듈이 아직 없다. 목표에 그대로 두면 어떤 skill도 "목표에
 * 닿음"이 될 수 없고, 그러면 <b>어떤 milestone도 끝나지 않아 Today가 첫 단계에서 멈춘다.</b>
 *
 * <p>레벨도 막힌다: I4는 {@code EVIDENCE_ACCEPTED}(S6), E4는 variant 답변({@code REVIEW_VARIANT}는 Later),
 * K·I·E의 5는 difficulty 5 challenge가 필요한데 seed에 없다(docs/06 §7.2 "도달 가능 상한"). 그래서 기본 트랙 MUST 43개 중
 * 32개가 영구 미달이 되고 1단계를 넘어가지 못했다.
 *
 * <p>예산·위험도는 축을 빼지 않고 {@code now}/{@code later}로 나눈다(docs/06 §4.2, ADR-062) — 나중에 들일 시간은 지금도 계획에 들어
 * 있어야 한다.
 */
@Component
public class MeasurableAxes {

    private static final String CEILING = "devpilot.skill.evidence-ceiling";

    private final Set<SkillAxis> axes;
    private final AxisLevels ceiling;

    /** 생성자가 둘이라 Spring이 고를 것을 표시한다. */
    @Autowired
    MeasurableAxes(DevPilotProperties properties) {
        this(
                resolve(properties.skill().measurableAxes()),
                resolveCeiling(properties.skill().evidenceCeiling()));
    }

    /** 레벨 상한이 없는 경우 (축 제외만 본다). ADR-061 시절 동작이고 테스트에서 쓴다. */
    MeasurableAxes(Set<SkillAxis> axes) {
        this(axes, AxisLevels.uniform(5));
    }

    MeasurableAxes(Set<SkillAxis> axes, AxisLevels ceiling) {
        if (axes.isEmpty()) {
            throw new IllegalArgumentException("devpilot.skill.measurable-axes must not be empty");
        }
        this.axes = EnumSet.copyOf(axes);
        this.ceiling = ceiling;
    }

    static Set<SkillAxis> resolve(Set<String> names) {
        EnumSet<SkillAxis> resolved = EnumSet.noneOf(SkillAxis.class);
        for (String name : names) {
            try {
                resolved.add(SkillAxis.valueOf(name.trim()));
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException(
                        "devpilot.skill.measurable-axes has no such axis: " + name, unknown);
            }
        }
        return resolved;
    }

    /**
     * 설정 map을 {@link AxisLevels}로 옮긴다. 네 축이 모두 있어야 하고 모르는 이름이 있으면 기동 실패다 (docs/06 §7.6b EC-11) — 빠진
     * 축을 0으로 두면 그 축의 목표가 조용히 사라진다.
     */
    static AxisLevels resolveCeiling(Map<String, Integer> levels) {
        EnumMap<SkillAxis, Integer> resolved = new EnumMap<>(SkillAxis.class);
        for (Map.Entry<String, Integer> entry : levels.entrySet()) {
            String name = entry.getKey().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            SkillAxis axis;
            try {
                axis = SkillAxis.valueOf(name);
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException(
                        CEILING + " has no such axis: " + entry.getKey(), unknown);
            }
            resolved.put(axis, entry.getValue());
        }
        for (SkillAxis axis : SkillAxis.values()) {
            if (!resolved.containsKey(axis)) {
                throw new IllegalArgumentException(CEILING + " is missing axis: " + axis);
            }
        }
        return new AxisLevels(
                resolved.get(SkillAxis.KNOWLEDGE),
                resolved.get(SkillAxis.IMPLEMENTATION),
                resolved.get(SkillAxis.EXPLANATION),
                resolved.get(SkillAxis.DEBUGGING));
    }

    /** 지금 제품이 어느 레벨까지 근거를 만들 수 있는가 (ADR-070). 전역이고 skill별이 아니다. */
    public AxisLevels evidenceCeiling() {
        return ceiling;
    }

    /** 지금 잴 수 있는 축. {@code SkillAxis} 선언 순서다. */
    public List<SkillAxis> axes() {
        return List.copyOf(axes);
    }

    /** 아직 잴 수 없는 축. 화면이 "왜 이 축은 안 세나"를 말할 때 쓴다. */
    public List<SkillAxis> unmeasured() {
        EnumSet<SkillAxis> rest = EnumSet.allOf(SkillAxis.class);
        rest.removeAll(axes);
        return List.copyOf(rest);
    }

    /**
     * 진도 판정에 쓸 목표 = 축별 {@code min(원래 목표, evidenceCeiling)} (ADR-070). 잴 수 없는 축은 상한이 0이라 그대로 0이 된다 —
     * ADR-061의 축 제외가 이 식의 특수한 경우다.
     *
     * <p>목표를 바꾸는 것이 아니라 <b>지금 판정에서 낮추는 것</b>이다. 저장된 {@code plan_skill_target}은 그대로다.
     */
    public AxisLevels forProgress(AxisLevels targets) {
        AxisLevels clamped = targets;
        for (SkillAxis axis : SkillAxis.values()) {
            int cap = axis.levelOf(ceiling);
            if (!axes.contains(axis)) {
                cap = 0;
            }
            if (axis.levelOf(clamped) > cap) {
                clamped = axis.withLevel(clamped, cap);
            }
        }
        return clamped;
    }

    /**
     * 원래 목표에서 <b>상한 위에 남은 몫</b>. 화면이 "기능이 열려야 갈 수 있는 곳"을 말할 때 쓴다 (docs/05 §7.10 {@code
     * capabilityPending}).
     *
     * @return 축별 원래 목표. 상한 이하인 축은 0이다 — 보류가 없다는 뜻이다
     */
    public AxisLevels pendingAbove(AxisLevels targets) {
        AxisLevels pending = AxisLevels.ZERO;
        AxisLevels current = forProgress(targets);
        for (SkillAxis axis : SkillAxis.values()) {
            if (axis.levelOf(targets) > axis.levelOf(current)) {
                pending = axis.withLevel(pending, axis.levelOf(targets));
            }
        }
        return pending;
    }
}
