package com.devpilot.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.common.math.FixedPointMath;
import com.devpilot.testsupport.TestProperties;
import com.devpilot.testsupport.UnitTest;
import java.time.Duration;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * docs/06 §1 N-6, docs/03 §9: 운영 {@code application.yml} 기본값이 바인딩되고, 소수 설정은 bp/micro 정수로 떨어져야 하며,
 * 가중치 합·threshold 순서가 틀리면 기동이 실패한다.
 */
@UnitTest
class DevPilotPropertiesBindingTest {

    private static final Map<String, Object> REQUIRED =
            Map.of("APP_BASE_URL", "http://localhost:5173");

    @Test
    void shouldBindProductionDefaultsWhenApplicationYamlIsLoaded() {
        DevPilotProperties properties = TestProperties.defaults(REQUIRED);

        assertThat(properties.time().defaultZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
        assertThat(properties.time().defaultDayStartHour()).isEqualTo(4);
        assertThat(properties.skill().selfAssessmentCap()).isEqualTo(3);
        assertThat(properties.privacy().idempotencyTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(properties.security().accountDeletionMaxTokenAge())
                .isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.ai().provider()).isEqualTo("deepseek");
        assertThat(properties.ai().monthlyBudgetMicroUsd()).isEqualTo(3_000_000L);
        assertThat(properties.content().seedChallenges()).isFalse();
        assertThat(
                        FixedPointMath.toBasisPoints(
                                properties.planner().weights().practicalImportance()))
                .isEqualTo(2_500);
    }

    @Test
    void shouldUseTestBudgetWhenTestProfileIsLoaded() {
        DevPilotProperties properties = TestProperties.testProfile();

        assertThat(properties.ai().provider()).isEqualTo("fake");
        assertThat(properties.ai().monthlyBudgetMicroUsd()).isEqualTo(25_000_000L);
        assertThat(properties.content().location()).isEqualTo("classpath:test-content/");
    }

    @Test
    void shouldNormalizeTrustedHostsWhenBound() {
        DevPilotProperties properties =
                TestProperties.defaults(
                        with("devpilot.ai.trusted-source-hosts", " Docs.Spring.IO ,,openjdk.org"));

        assertThat(properties.ai().trustedSourceHosts())
                .containsExactly("docs.spring.io", "openjdk.org");
    }

    @Test
    void shouldFailWhenWeightsDoNotSumToOne() {
        assertThatThrownBy(
                        () ->
                                TestProperties.defaults(
                                        with(
                                                "devpilot.planner.weights.practical-importance",
                                                "0.30")))
                .hasStackTraceContaining("must sum to 1.0");
    }

    @ParameterizedTest(name = "[{index}] {0}={1}")
    @CsvSource({
        "devpilot.planner.modifiers.risk-high-must, 1.20005",
        "devpilot.planner.overrun-tolerance, 1.100001",
        "devpilot.budget.axis-cost.knowledge, 0.12345",
        "devpilot.budget.completion-default-rate, -0.7",
        "devpilot.planner.default-practical-importance, 0.0000001",
        "devpilot.ai.monthly-budget-usd, 3.0000001"
    })
    void shouldFailWhenDecimalSettingIsNotFixedPoint(String key, String value) {
        assertThatThrownBy(() -> TestProperties.defaults(with(key, value)))
                .hasStackTraceContaining("(N-6)");
    }

    @Test
    void shouldFailWhenRiskThresholdsAreNotOrdered() {
        assertThatThrownBy(
                        () ->
                                TestProperties.defaults(
                                        with("devpilot.budget.risk-thresholds.medium-max", "1.30")))
                .hasStackTraceContaining("low-max < medium-max < high-max");
    }

    @Test
    void shouldFailWhenReasonThresholdsAreNotOrdered() {
        assertThatThrownBy(
                        () ->
                                TestProperties.defaults(
                                        with("devpilot.planner.reason-medium-threshold", "0.70")))
                .hasStackTraceContaining("reason-medium-threshold must be lower");
    }

    @Test
    void shouldFailWhenBudgetWarningRatioExceedsOne() {
        assertThatThrownBy(
                        () ->
                                TestProperties.defaults(
                                        with("devpilot.ai.budget-warning-ratio", "1.5")))
                .hasStackTraceContaining("budget-warning-ratio must be <= 1.0");
    }

    private static Map<String, Object> with(String key, String value) {
        Map<String, Object> overrides = new HashMap<>(REQUIRED);
        overrides.put(key, value);
        return overrides;
    }
}
