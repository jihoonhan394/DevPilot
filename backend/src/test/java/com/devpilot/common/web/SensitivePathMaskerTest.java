package com.devpilot.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** docs/07 §4.6 · BL-SEC-06: 캘린더 피드 토큰 경로는 {@code …/calendar/****.ics}로 가린다. */
@UnitTest
class SensitivePathMaskerTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({
        "/api/v1/calendar/abcDEF123_-.ics, /api/v1/calendar/****.ics",
        "/api/v1/calendar/.ics, /api/v1/calendar/****.ics",
        "/api/v1/me, /api/v1/me",
        "/api/v1/calendar/nested/token.ics, /api/v1/calendar/nested/token.ics",
        "/api/v1/plans/0b5a2f0e-3c1d-4e7a-9f00-1a2b3c4d5e6f,"
                + " /api/v1/plans/0b5a2f0e-3c1d-4e7a-9f00-1a2b3c4d5e6f"
    })
    void shouldMaskOnlyCalendarTokenWhenPathIsMasked(String path, String expected) {
        assertThat(SensitivePathMasker.mask(path)).isEqualTo(expected);
    }
}
