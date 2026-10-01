package com.devpilot.integration.ai.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.integration.ai.api.AiUsage;
import com.devpilot.testsupport.TestProperties;
import com.devpilot.testsupport.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** docs/17 §8.4 K1~K7, AC-13 S8. 피크 ×2를 항상 곱하고 정수만 쓴다. 단가가 없는 모델은 기동 실패다(설정 바인딩 테스트와 함께). */
@UnitTest
class AiCostCalculatorTest {

    private final AiCostCalculator calculator = new AiCostCalculator(TestProperties.testProfile());

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({
        "K1, deepseek-flash, 3000, 0, 2000, 3300",
        "K2, deepseek-flash, 3000, 2000, 1500, 2112",
        "K3, deepseek-flash, 1200, 1200, 0, 8",
        "K4, deepseek-flash, 1, 1, 0, 1",
        "K5, deepseek-v4-pro, 1, 0, 1, 6",
        "K6, deepseek-flash, 14000, 0, 32000, 42600",
        "K7, deepseek-flash, 1000000, 0, 100000, 420000",
        "AC13-S8, deepseek-flash, 1000000, 800000, 100000, 184800"
    })
    void shouldComputeMicroUsdWhenUsageIsGiven(
            String id, String model, int input, int cached, int output, long expected) {
        assertThat(calculator.costMicroUsd(model, new AiUsage(input, cached, output, 0)))
                .as(id)
                .isEqualTo(expected);
    }

    @Test
    void shouldIgnoreReasoningTokensBecauseTheyAreInsideOutput() {
        assertThat(calculator.costMicroUsd(new AiUsage(3000, 0, 2000, 1500))).isEqualTo(3300);
    }

    @Test
    void shouldFailWhenConfiguredModelHasNoPrice() {
        assertThatThrownBy(
                        () ->
                                new AiCostCalculator(
                                        TestProperties.testProfile().ai().pricing(),
                                        "unknown-model"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldFailWhenModelIsUnknownForExplicitCall() {
        assertThatThrownBy(() -> calculator.costMicroUsd("unknown-model", AiUsage.NONE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
