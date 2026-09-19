package com.devpilot.user.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** docs/05 §3.1·§3.2 (BL-FND-21), AC-17 S5. */
@IntegrationTest
class ProfileServiceIntegrationTest extends ApiTestSupport {

    private static final String ME = "/api/v1/me";

    @Test
    void shouldReturnDefaultProfileWhenUserIsNew() throws Exception {
        api.get(TestUser.owner(), ME)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.displayName").value("owner"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.dayStartHour").value(4))
                .andExpect(jsonPath("$.weekdayStudyMinutes").value(45))
                .andExpect(jsonPath("$.weekendStudyMinutes").value(240))
                .andExpect(jsonPath("$.experienceProfile").doesNotExist())
                .andExpect(jsonPath("$.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.today").value("2026-10-05"))
                .andExpect(jsonPath("$.calendarSubscribed").value(false))
                .andExpect(jsonPath("$.aiStatus").value("ENABLED"))
                .andExpect(jsonPath("$.aiUsage.todayCalls").value(0))
                .andExpect(jsonPath("$.aiUsage.dailyCallLimit").value(60))
                .andExpect(jsonPath("$.aiUsage.monthCostUsd").value(monthCostUsd()))
                .andExpect(jsonPath("$.aiUsage.monthlyBudgetUsd").value("25.00"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.sub").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    /** 월 비용은 전체 사용자 합계다(docs/17 §8.1). 같은 컨테이너의 다른 테스트가 남긴 호출을 포함한다. */
    private String monthCostUsd() {
        Long micro =
                jdbc.queryForObject(
                        "select coalesce(sum(cost_micro_usd), 0) from devpilot.ai_call_log"
                                + " where created_at >= ? and created_at < ?",
                        Long.class,
                        OffsetDateTime.parse("2026-10-01T00:00:00+09:00"),
                        OffsetDateTime.parse("2026-11-01T00:00:00+09:00"));
        return ProfileService.usd(micro == null ? 0 : micro);
    }

    @Test
    void shouldMoveTodayBackWhenDayStartHourIsRaised() throws Exception {
        TestUser user = TestUser.owner();
        clock.setInstant(Instant.parse("2026-10-05T20:30:00Z"));
        api.get(user, ME).andExpect(jsonPath("$.today").value("2026-10-06"));

        api.patch(user, ME, Map.of("dayStartHour", 6, "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayStartHour").value(6))
                .andExpect(jsonPath("$.version").value(1));

        api.get(user, ME).andExpect(jsonPath("$.today").value("2026-10-05"));
    }

    @Test
    void shouldUpdateTimezoneAndStudyMinutesWhenValid() throws Exception {
        TestUser user = TestUser.owner();

        api.patch(
                        user,
                        ME,
                        Map.of(
                                "timezone", "America/New_York",
                                "weekdayStudyMinutes", 0,
                                "weekendStudyMinutes", 720,
                                "displayName", "새 이름",
                                "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.weekdayStudyMinutes").value(0))
                .andExpect(jsonPath("$.weekendStudyMinutes").value(720))
                .andExpect(jsonPath("$.displayName").value("새 이름"))
                .andExpect(jsonPath("$.today").value("2026-10-05"));
    }

    @Test
    void shouldKeepVersionWhenNothingChanges() throws Exception {
        TestUser user = TestUser.owner();

        api.patch(user, ME, Map.of("dayStartHour", 4, "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void shouldRejectDayStartHourOutOfRange() throws Exception {
        api.patch(TestUser.owner(), ME, Map.of("dayStartHour", 7, "version", 0))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("dayStartHour"))
                .andExpect(jsonPath("$.errors[0].code").value("Max"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void shouldRejectUnknownTimezone() throws Exception {
        api.patch(TestUser.owner(), ME, Map.of("timezone", "Mars/Olympus", "version", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("timezone"))
                .andExpect(jsonPath("$.errors[0].code").value("TIMEZONE_INVALID"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void shouldRejectBlankDisplayNameWhenPresent() throws Exception {
        api.patch(TestUser.owner(), ME, Map.of("displayName", "   ", "version", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("displayName"))
                .andExpect(jsonPath("$.errors[0].code").value("NOT_BLANK_IF_PRESENT"));
    }

    @Test
    void shouldRequireVersion() throws Exception {
        api.patch(TestUser.owner(), ME, Map.of("dayStartHour", 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"))
                .andExpect(jsonPath("$.errors[0].code").value("NotNull"));
    }

    @Test
    void shouldRejectStaleVersionWithoutChangingProfile() throws Exception {
        TestUser user = TestUser.owner();
        api.patch(user, ME, Map.of("dayStartHour", 5, "version", 0)).andExpect(status().isOk());

        api.patch(user, ME, Map.of("dayStartHour", 6, "version", 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        api.get(user, ME).andExpect(jsonPath("$.dayStartHour").value(5));
    }

    @Test
    void shouldRejectUnknownPropertyAsMalformed() throws Exception {
        api.patch(TestUser.owner(), ME, Map.of("role", "ADMIN", "version", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        api.patch(TestUser.owner(), ME, Map.of("experienceProfile", "OTHER", "version", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void shouldRequireTokenForProfile() throws Exception {
        mockMvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
}
