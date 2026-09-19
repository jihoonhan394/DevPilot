package com.devpilot.user.presentation;

import com.devpilot.user.application.ProfileService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * {@code PATCH /me} 요청 (docs/05 §3.2). {@code null}(또는 생략) 필드는 변경하지 않는다. {@code experienceProfile},
 * {@code experienceStartDate}를 보내면 알 수 없는 속성으로 400 {@code MALFORMED_REQUEST}다.
 */
public record UpdateMeRequest(
        @Nullable @Size(min = 1, max = 100) String displayName,
        @Nullable @Size(max = 50) String timezone,
        @Nullable @Min(0) @Max(6) Integer dayStartHour,
        @Nullable @Min(0) @Max(720) Integer weekdayStudyMinutes,
        @Nullable @Min(0) @Max(720) Integer weekendStudyMinutes,
        @NotNull Long version) {

    ProfileService.UpdateSettingsCommand toCommand() {
        return new ProfileService.UpdateSettingsCommand(
                displayName,
                timezone,
                dayStartHour,
                weekdayStudyMinutes,
                weekendStudyMinutes,
                version);
    }
}
