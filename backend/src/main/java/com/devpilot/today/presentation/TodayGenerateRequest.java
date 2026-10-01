package com.devpilot.today.presentation;

import com.devpilot.today.domain.EnergyLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * {@code POST /today/generate} 요청 (docs/05 §8.2). {@code fail-on-null-for-primitives} 설정에서 record의
 * primitive 필드는 생략해도 400이 되므로 wrapper로 받는다: {@code force}는 생략(또는 null)하면 false, {@code
 * availableMinutes}는 생략하면 400 {@code VALIDATION_FAILED}다.
 *
 * @param force 생략 시 false. 진행 중·완료한 main이 있어도 새 main을 만든다(docs/06 §5.9)
 */
public record TodayGenerateRequest(
        @NotNull @Min(5) @Max(720) Integer availableMinutes,
        @NotNull EnergyLevel energyLevel,
        @Nullable Boolean force) {

    /** 생략하면 false. */
    boolean forceOrDefault() {
        return Boolean.TRUE.equals(force);
    }
}
