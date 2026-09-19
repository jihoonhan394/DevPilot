package com.devpilot.common.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 정수 고정소수점 연산 (docs/06 §1 N-1 ~ N-7). 규칙 계산은 이 클래스의 나눗셈과 변환만 쓴다. 인자가 음수이거나 분모가 0 이하이면 {@link
 * IllegalArgumentException}, 곱셈·덧셈 overflow는 {@link ArithmeticException}이다.
 */
public final class FixedPointMath {

    /** basis point 배율: 1.0 = 10_000 (N-2). */
    public static final long BP_SCALE = 10_000L;

    /** micro 배율: 1.0 = 1_000_000 (N-3). */
    public static final long MICRO_SCALE = 1_000_000L;

    private static final int BP_DIGITS = 4;
    private static final int MICRO_DIGITS = 6;

    private FixedPointMath() {}

    /** {@code floor(a / b)}, a ≥ 0, b > 0 (N-4). */
    public static long floorDiv(long dividend, long divisor) {
        requireOperands(dividend, divisor);
        return Math.floorDiv(dividend, divisor);
    }

    /** {@code ceil(a / b)}, a ≥ 0, b > 0 (N-4). */
    public static long ceilDiv(long dividend, long divisor) {
        requireOperands(dividend, divisor);
        return Math.ceilDiv(dividend, divisor);
    }

    /** {@code floorDiv(2a + b, 2b)} — 0.5는 올린다 (N-4). */
    public static long roundHalfUpDiv(long dividend, long divisor) {
        requireOperands(dividend, divisor);
        long doubled = Math.multiplyExact(dividend, 2L);
        return Math.floorDiv(Math.addExact(doubled, divisor), Math.multiplyExact(divisor, 2L));
    }

    /** {@code floorDiv(value × multiplierBp, 10_000)} (N-5). */
    public static long applyMultiplierBp(long value, long multiplierBp) {
        return floorDiv(Math.multiplyExact(value, multiplierBp), BP_SCALE);
    }

    /**
     * 설정의 소수 값을 bp 정수로 바꾼다 (N-6). {@code 0.25} → 2500. 정수로 떨어지지 않거나 음수이면 {@link
     * IllegalArgumentException} — 기동 실패로 이어진다.
     */
    public static int toBasisPoints(BigDecimal value) {
        return Math.toIntExact(scaleExact(value, BP_DIGITS));
    }

    /** 설정의 소수 값을 micro 정수로 바꾼다 (N-6). {@code 1.5} → 1_500_000. */
    public static long toMicros(BigDecimal value) {
        return scaleExact(value, MICRO_DIGITS);
    }

    private static long scaleExact(BigDecimal value, int digits) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("fixed-point value must not be negative: " + value);
        }
        BigDecimal scaled = value.movePointRight(digits);
        if (scaled.setScale(0, RoundingMode.DOWN).compareTo(scaled) != 0) {
            throw new IllegalArgumentException(
                    "value " + value + " is not an integer after scaling by 10^" + digits);
        }
        return scaled.longValueExact();
    }

    private static void requireOperands(long dividend, long divisor) {
        if (dividend < 0) {
            throw new IllegalArgumentException("dividend must not be negative: " + dividend);
        }
        if (divisor <= 0) {
            throw new IllegalArgumentException("divisor must be positive: " + divisor);
        }
    }
}
