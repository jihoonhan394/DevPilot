package com.devpilot.common.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

/** docs/05 §1.7 timezone(IANA region ID), docs/07 §5.5 저장 전용 URL 규칙. */
@UnitTest
class InputRulesTest {

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
        "Asia/Seoul, true",
        "America/New_York, true",
        "Europe/London, true",
        "+09:00, false",
        "asia/seoul, false",
        "Mars/Phobos, false",
        "KST, false",
        "'', false"
    })
    void shouldAcceptOnlyIanaRegionIdsWhenTimezoneIsChecked(String value, boolean expected) {
        assertThat(InputRules.isIanaRegionId(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    void shouldRejectWhenTimezoneIsNull(String value) {
        assertThat(InputRules.isIanaRegionId(value)).isFalse();
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
        "https://repo.example.invalid/order-service, true",
        "http://localhost:8080/path, true",
        "HTTPS://repo.example.invalid, true",
        "ftp://repo.example.invalid/file, false",
        "javascript:alert(1), false",
        "not a url, false",
        "https://, false",
        "/relative/path, false",
        "'', false"
    })
    void shouldAcceptOnlyHttpUrlsWithHostWhenUrlIsChecked(String value, boolean expected) {
        assertThat(InputRules.isHttpUrl(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    void shouldRejectWhenUrlIsNull(String value) {
        assertThat(InputRules.isHttpUrl(value)).isFalse();
    }
}
