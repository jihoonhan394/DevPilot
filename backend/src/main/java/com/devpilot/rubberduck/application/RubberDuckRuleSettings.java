package com.devpilot.rubberduck.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.rubberduck.domain.RubberDuckPolicy;

/** {@code devpilot.rubberduck.*} → {@link RubberDuckPolicy} 설정 (docs/03 §9, docs/06 §9.5). */
public final class RubberDuckRuleSettings {

    private RubberDuckRuleSettings() {}

    public static RubberDuckPolicy policy(DevPilotProperties properties) {
        DevPilotProperties.Rubberduck settings = properties.rubberduck();
        return new RubberDuckPolicy(
                new RubberDuckPolicy.Settings(
                        settings.maxTurns(),
                        settings.stuckTurnsBeforeHint(),
                        settings.dontKnowMaxChars(),
                        settings.dontKnowPhrases()));
    }
}
