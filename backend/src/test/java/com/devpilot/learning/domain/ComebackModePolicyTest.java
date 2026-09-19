package com.devpilot.learning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.testsupport.TestRuleSettings;
import com.devpilot.testsupport.UnitTest;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/** docs/06 §5.5 comebackMode vector ({@code 06-05-comeback-mode.csv}, 6행), AC-02 S7. */
@UnitTest
class ComebackModePolicyTest {

    private final ComebackModePolicy policy = TestRuleSettings.comeback();

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(
            resources = "/vectors/06-05-comeback-mode.csv",
            numLinesToSkip = 2,
            nullValues = "null")
    void shouldMatchVectorWhenComebackModeIsDecided(
            String id, LocalDate today, @Nullable String completedPlanDates, boolean expected) {
        List<LocalDate> dates =
                completedPlanDates == null
                        ? List.of()
                        : Arrays.stream(completedPlanDates.split("\\|"))
                                .map(LocalDate::parse)
                                .toList();

        assertThat(policy.isComebackMode(today, dates)).as(id).isEqualTo(expected);
    }

    @Test
    void shouldExposeWindowStart() {
        assertThat(policy.windowStart(LocalDate.parse("2026-10-10")))
                .isEqualTo(LocalDate.parse("2026-10-07"));
    }

    @Test
    void shouldRejectNonPositiveInactiveDays() {
        assertThatThrownBy(() -> new ComebackModePolicy(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
