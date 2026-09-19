package com.devpilot.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** 테스트용 시계 (docs/09 §4.1). zone은 UTC 고정, 스레드 안전. */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> instant;

    public MutableClock(Instant initial) {
        this.instant = new AtomicReference<>(initial);
    }

    public void setInstant(Instant value) {
        instant.set(value);
    }

    public void advance(Duration duration) {
        instant.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("MutableClock is fixed to UTC");
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
