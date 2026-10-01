package com.devpilot.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.testsupport.MutableClock;
import com.devpilot.testsupport.UnitTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** BL-SEC-11 token bucket (docs/07 §12.3). 시각은 {@link MutableClock}으로만 움직인다. */
@UnitTest
class TokenBucketRateLimiterTest {

    private static final Instant START = Instant.parse("2026-10-05T10:00:00Z");

    private final MutableClock clock = new MutableClock(START);

    @Test
    void shouldAllowCapacityRequestsThenRejectWithRetryAfter() {
        TokenBucketRateLimiter limiter = perMinute(120);

        for (int i = 0; i < 120; i++) {
            assertThat(limiter.tryAcquire("user-a").allowed()).as("request %d", i + 1).isTrue();
        }
        TokenBucketRateLimiter.Decision rejected = limiter.tryAcquire("user-a");

        assertThat(rejected.allowed()).isFalse();
        // 120개 / 60초 → 토큰 1개 = 0.5초, 올림해서 1초
        assertThat(rejected.retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void shouldRefillOneTokenPerInterval() {
        TokenBucketRateLimiter limiter = perMinute(120);
        for (int i = 0; i < 120; i++) {
            limiter.tryAcquire("user-a");
        }

        clock.advance(Duration.ofMillis(499));
        assertThat(limiter.tryAcquire("user-a").allowed()).isFalse();
        clock.advance(Duration.ofMillis(1));
        assertThat(limiter.tryAcquire("user-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user-a").allowed()).isFalse();
    }

    @Test
    void shouldNotExceedCapacityAfterLongIdle() {
        TokenBucketRateLimiter limiter = perMinute(3);
        limiter.tryAcquire("user-a");

        clock.advance(Duration.ofHours(5));

        assertThat(limiter.tryAcquire("user-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user-a").allowed()).isFalse();
    }

    @Test
    void shouldKeepSeparateBucketPerKey() {
        TokenBucketRateLimiter limiter = perMinute(1);

        assertThat(limiter.tryAcquire("user-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user-a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("user-b").allowed()).isTrue();
    }

    @Test
    void shouldReportSecondsUntilNextTokenForHourlyLimit() {
        TokenBucketRateLimiter limiter =
                new TokenBucketRateLimiter(
                        30, Duration.ofHours(1), clock, TokenBucketRateLimiter.Limits.DEFAULT);
        for (int i = 0; i < 30; i++) {
            limiter.tryAcquire("127.0.0.1");
        }

        TokenBucketRateLimiter.Decision rejected = limiter.tryAcquire("127.0.0.1");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(120);

        clock.advance(Duration.ofSeconds(119));
        assertThat(limiter.tryAcquire("127.0.0.1").retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void shouldRejectNewKeysWhenBucketMapIsFull() {
        TokenBucketRateLimiter limiter =
                new TokenBucketRateLimiter(
                        10,
                        Duration.ofMinutes(1),
                        clock,
                        new TokenBucketRateLimiter.Limits(2, Duration.ofMinutes(10), 1_000));

        assertThat(limiter.tryAcquire("user-a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user-b").allowed()).isTrue();
        assertThat(limiter.tryAcquire("user-c").allowed()).isFalse();
        assertThat(limiter.tryAcquire("user-a").allowed()).isTrue();
        assertThat(limiter.bucketCount()).isEqualTo(2);
    }

    @Test
    void shouldEvictIdleBucketsOnCleanup() {
        TokenBucketRateLimiter limiter =
                new TokenBucketRateLimiter(
                        10,
                        Duration.ofMinutes(1),
                        clock,
                        new TokenBucketRateLimiter.Limits(2, Duration.ofMinutes(10), 1));
        limiter.tryAcquire("user-a");
        limiter.tryAcquire("user-b");

        clock.advance(Duration.ofMinutes(11));

        assertThat(limiter.tryAcquire("user-c").allowed()).isTrue();
        assertThat(limiter.bucketCount()).isEqualTo(1);
    }

    @Test
    void shouldRejectNonPositiveCapacity() {
        assertThatThrownBy(() -> perMinute(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private TokenBucketRateLimiter perMinute(int capacity) {
        return new TokenBucketRateLimiter(
                capacity, Duration.ofMinutes(1), clock, TokenBucketRateLimiter.Limits.DEFAULT);
    }
}
