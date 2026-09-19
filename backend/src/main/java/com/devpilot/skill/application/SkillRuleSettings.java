package com.devpilot.skill.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.skill.domain.SkillLevelRules;

/**
 * {@code devpilot.skill.*}·{@code devpilot.rubberduck.evidence-coverage-bp} → 규칙 설정 (docs/03 §9).
 */
public final class SkillRuleSettings {

    private SkillRuleSettings() {}

    public static SkillLevelRules.Settings levelRules(DevPilotProperties properties) {
        DevPilotProperties.Skill skill = properties.skill();
        return new SkillLevelRules.Settings(
                skill.ruleWindowDays(),
                skill.axisChangeCooldown(),
                skill.diagnosticMaxLevel(),
                properties.rubberduck().evidenceCoverageBp());
    }
}
