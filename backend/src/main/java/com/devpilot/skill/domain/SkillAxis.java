package com.devpilot.skill.domain;

import com.devpilot.common.web.AxisLevels;

/** skill 레벨 축 (docs/04 §3). 선언 순서 = {@code AxisLevels} 필드 순서이고, 규칙의 동점 처리 순서(K, I, E, D)다. */
public enum SkillAxis {
    KNOWLEDGE,
    IMPLEMENTATION,
    EXPLANATION,
    DEBUGGING;

    /** 이 축의 레벨. */
    public int levelOf(AxisLevels levels) {
        return switch (this) {
            case KNOWLEDGE -> levels.knowledge();
            case IMPLEMENTATION -> levels.implementation();
            case EXPLANATION -> levels.explanation();
            case DEBUGGING -> levels.debugging();
        };
    }

    /** 이 축만 {@code level}로 바꾼 새 값. */
    public AxisLevels withLevel(AxisLevels levels, int level) {
        return switch (this) {
            case KNOWLEDGE ->
                    new AxisLevels(
                            level,
                            levels.implementation(),
                            levels.explanation(),
                            levels.debugging());
            case IMPLEMENTATION ->
                    new AxisLevels(
                            levels.knowledge(), level, levels.explanation(), levels.debugging());
            case EXPLANATION ->
                    new AxisLevels(
                            levels.knowledge(), levels.implementation(), level, levels.debugging());
            case DEBUGGING ->
                    new AxisLevels(
                            levels.knowledge(),
                            levels.implementation(),
                            levels.explanation(),
                            level);
        };
    }
}
