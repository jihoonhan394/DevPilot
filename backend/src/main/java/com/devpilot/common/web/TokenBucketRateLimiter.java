package com.devpilot.common.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 키별 token bucket (docs/07 §12.3, BL-SEC-11). 메모리에만 두고 단일 인스턴스를 전제한다. 토큰은 정수 나노초 단위로 센다(부동소수점 없음):
 * 버킷은 최대 {@code capacity × 토큰당 보충 시간}만큼 채워지고, 요청 1건이 토큰 1개(= 토큰당 보충 시간)를 쓴다. 시각은 주입된 {@link Clock}을
 * 쓴다(테스트의 {@code MutableClock}).
 *
 * <p>버킷 맵은 {@code maxBuckets}개까지다. {@code cleanupEvery} 요청마다 {@code idleTimeout} 동안 쓰이지 않은 버킷을 지우고,
 * 최대치에 닿으면 새 키 요청을 거절한다.
 */
public final class TokenBucketRateLimiter {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final int capacity;
    private final long nanosPerToken;
    private final Clock clock;
    private final int maxBuckets;
    private final Duration idleTimeout;
    private final int cleanupEvery;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong requests = new AtomicLong();

    /**
     * @param capacity 버킷 용량 = 한 주기에 허용하는 요청 수
     * @param period 용량만큼 다시 채우는 데 걸리는 시간 (예: 120개 / 1분 → 초당 2개)
     */
    public TokenBucketRateLimiter(int capacity, Duration period, Clock clock, Limits limits) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.nanosPerToken = Math.max(1, period.toNanos() / capacity);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxBuckets = limits.maxBuckets();
        this.idleTimeout = limits.idleTimeout();
        this.cleanupEvery = limits.cleanupEvery();
    }

    /** 요청 1건. 토큰이 있으면 쓴다. */
    public Decision tryAcquire(String key) {
        Instant now = clock.instant();
        if (requests.incrementAndGet() % cleanupEvery == 0) {
            evictIdle(now);
        }
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= maxBuckets) {
                return new Decision(false, 1);
            }
            bucket =
                    buckets.computeIfAbsent(
                            key, ignored -> new Bucket(capacity * nanosPerToken, now));
        }
        return bucket.take(now, capacity * nanosPerToken, nanosPerToken);
    }

    private void evictIdle(Instant now) {
        Instant threshold = now.minus(idleTimeout);
        buckets.values().removeIf(bucket -> bucket.lastUsedBefore(threshold));
    }

    /** 현재 버킷 수 (테스트·운영 확인용). */
    public int bucketCount() {
        return buckets.size();
    }

    /**
     * 판정 결과.
     *
     * @param retryAfterSeconds 거절이면 다음 토큰까지 초 (올림, 최소 1). 허용이면 0
     */
    public record Decision(boolean allowed, long retryAfterSeconds) {}

    /**
     * 메모리 관리 한도 (docs/07 §12.3).
     *
     * @param maxBuckets 버킷 맵 최대 크기 (10,000)
     * @param idleTimeout 이 시간 동안 쓰이지 않은 버킷은 정리 대상 (10분)
     * @param cleanupEvery 정리 주기 (요청 1,000건마다)
     */
    public record Limits(int maxBuckets, Duration idleTimeout, int cleanupEvery) {

        /** docs/07 §12.3 기본값. */
        public static final Limits DEFAULT = new Limits(10_000, Duration.ofMinutes(10), 1_000);
    }

    /** 키 하나의 버킷. 남은 양은 나노초 단위 정수다. */
    private static final class Bucket {

        private long availableNanos;
        private Instant lastRefill;
        private Instant lastUsed;

        Bucket(long availableNanos, Instant now) {
            this.availableNanos = availableNanos;
            this.lastRefill = now;
            this.lastUsed = now;
        }

        synchronized Decision take(Instant now, long maxNanos, long nanosPerToken) {
            if (now.isAfter(lastRefill)) {
                long elapsed = Duration.between(lastRefill, now).toNanos();
                availableNanos = Math.min(maxNanos, availableNanos + elapsed);
                lastRefill = now;
            }
            lastUsed = now;
            if (availableNanos >= nanosPerToken) {
                availableNanos -= nanosPerToken;
                return new Decision(true, 0);
            }
            long missing = nanosPerToken - availableNanos;
            return new Decision(false, Math.max(1, Math.ceilDiv(missing, NANOS_PER_SECOND)));
        }

        synchronized boolean lastUsedBefore(Instant threshold) {
            return lastUsed.isBefore(threshold);
        }
    }
}
