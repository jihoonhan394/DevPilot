package com.devpilot.rubberduck.presentation;

import com.devpilot.rubberduck.application.RubberDuckService.StartCommand;
import com.devpilot.rubberduck.domain.RubberDuckTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code POST /rubber-duck} 요청 (docs/05 §9.6).
 *
 * @param targetId {@code CONCEPT}이 아니면 필수
 * @param conceptKey {@code CONCEPT}이면 필수
 * @param skillCode 생략하면 대상에서 유도한다 (docs/05 §9.5 표, RD-7)
 */
public record RubberDuckStartRequest(
        @NotNull RubberDuckTargetType targetType,
        @Nullable UUID targetId,
        @Size(max = 120) @Pattern(regexp = "^[A-Z][A-Z0-9_]*(\\.[A-Z][A-Z0-9_]*)+$")
                @Nullable String conceptKey,
        @Size(max = 100) @Nullable String skillCode) {

    public StartCommand toCommand() {
        return new StartCommand(targetType, targetId, conceptKey, skillCode);
    }
}
