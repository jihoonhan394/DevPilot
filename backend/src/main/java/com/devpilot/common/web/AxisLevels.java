package com.devpilot.common.web;

/** 축별 레벨 (docs/05 §2.2). {@code SkillAxis} 선언 순서, 각 0~5. 레벨 규칙 입력·출력과 응답에 같이 쓴다. */
public record AxisLevels(int knowledge, int implementation, int explanation, int debugging) {

    /** 네 축 모두 0. */
    public static final AxisLevels ZERO = new AxisLevels(0, 0, 0, 0);

    /** 네 축이 같은 값. */
    public static AxisLevels uniform(int level) {
        return new AxisLevels(level, level, level, level);
    }
}
