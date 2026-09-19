package com.devpilot.common.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.testsupport.UnitTest;
import java.math.BigDecimal;
import java.util.function.LongBinaryOperator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;

/** docs/06 §1 N-4 ~ N-7. vector: {@code 06-01-fixed-point-math.csv}. */
@UnitTest
class FixedPointMathTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvFileSource(resources = "/vectors/06-01-fixed-point-math.csv", numLinesToSkip = 2)
    void shouldMatchVectorWhenOperationIsApplied(
            String id, String operation, long left, long right, String expected) {
        LongBinaryOperator function = operation(operation);

        switch (expected) {
            case "IllegalArgumentException" ->
                    assertThatThrownBy(() -> function.applyAsLong(left, right))
                            .isInstanceOf(IllegalArgumentException.class);
            case "ArithmeticException" ->
                    assertThatThrownBy(() -> function.applyAsLong(left, right))
                            .isInstanceOf(ArithmeticException.class);
            default ->
                    assertThat(function.applyAsLong(left, right))
                            .as(id)
                            .isEqualTo(Long.parseLong(expected));
        }
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}bp")
    @CsvSource({"0.25, 2500", "1.2, 12000", "0, 0", "1.00, 10000", "0.0001, 1", "1.15, 11500"})
    void shouldConvertToBasisPointsWhenValueIsExact(String value, int expected) {
        assertThat(FixedPointMath.toBasisPoints(new BigDecimal(value))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1} micro")
    @CsvSource({"1.5, 1500000", "0.000001, 1", "25, 25000000", "0.30, 300000"})
    void shouldConvertToMicrosWhenValueIsExact(String value, long expected) {
        assertThat(FixedPointMath.toMicros(new BigDecimal(value))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({"0.00005", "-0.1", "0.12345"})
    void shouldRejectBasisPointsWhenValueIsNotExactOrNegative(String value) {
        assertThatThrownBy(() -> FixedPointMath.toBasisPoints(new BigDecimal(value)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({"0.0000001", "-1"})
    void shouldRejectMicrosWhenValueIsNotExactOrNegative(String value) {
        assertThatThrownBy(() -> FixedPointMath.toMicros(new BigDecimal(value)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static LongBinaryOperator operation(String name) {
        return switch (name) {
            case "floorDiv" -> FixedPointMath::floorDiv;
            case "ceilDiv" -> FixedPointMath::ceilDiv;
            case "roundHalfUpDiv" -> FixedPointMath::roundHalfUpDiv;
            case "applyMultiplierBp" -> FixedPointMath::applyMultiplierBp;
            default -> throw new IllegalArgumentException("unknown operation " + name);
        };
    }
}
