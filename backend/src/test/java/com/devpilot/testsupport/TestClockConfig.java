package com.devpilot.testsupport;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * {@code @Primary Clock} = {@link MutableClock} (docs/09 §4.1). 초기값 2026-10-05T10:00:00Z(KST
 * 19:00). {@code MutableClock}은 {@code Clock}의 하위 타입이므로 {@code Clock} 주입도 이 bean이 받는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    public static final Instant DEFAULT_INSTANT = Instant.parse("2026-10-05T10:00:00Z");

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(DEFAULT_INSTANT);
    }
}
