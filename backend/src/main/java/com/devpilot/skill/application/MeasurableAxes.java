package com.devpilot.skill.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.web.AxisLevels;
import com.devpilot.skill.domain.SkillAxis;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 지금 근거를 쌓을 수 있는 축 (docs/06 §7.6, ADR-061). {@code devpilot.skill.measurable-axes}를 {@link
 * SkillAxis}로 옮기고, 진도 판정에 들어가는 목표에서 <b>잴 수 없는 축을 지운다</b>.
 *
 * <p>DEBUGGING은 {@code COACH_*} 이벤트로만 오르는데(docs/06 §7.2) coach 모듈이 아직 없다. 목표에 그대로 두면 어떤 skill도 "목표에
 * 닿음"이 될 수 없고, 그러면 <b>어떤 milestone도 끝나지 않아 Today가 첫 단계에서 멈춘다.</b>
 *
 * <p>예산·위험도는 건드리지 않는다(docs/06 §4) — 나중에 들일 시간은 지금도 계획에 들어 있어야 한다.
 */
@Component
public class MeasurableAxes {

    private final Set<SkillAxis> axes;

    /** 생성자가 둘이라 Spring이 고를 것을 표시한다. */
    @Autowired
    MeasurableAxes(DevPilotProperties properties) {
        this(resolve(properties.skill().measurableAxes()));
    }

    MeasurableAxes(Set<SkillAxis> axes) {
        if (axes.isEmpty()) {
            throw new IllegalArgumentException("devpilot.skill.measurable-axes must not be empty");
        }
        this.axes = EnumSet.copyOf(axes);
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
     * 진도 판정에 쓸 목표. 잴 수 없는 축은 0으로 내린다 — 그 축은 "이미 닿은 것"으로 보고 넘어간다.
     *
     * <p>목표를 바꾸는 것이 아니라 <b>지금 판정에서 빼는 것</b>이다. 저장된 {@code plan_skill_target}은 그대로다.
     */
    public AxisLevels forProgress(AxisLevels targets) {
        AxisLevels clamped = targets;
        for (SkillAxis axis : unmeasured()) {
            clamped = axis.withLevel(clamped, 0);
        }
        return clamped;
    }
}
