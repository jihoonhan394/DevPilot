package com.devpilot.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 애플리케이션의 유일한 시계 (docs/08 §3.9). 현재 시각은 이 {@link Clock}으로만 얻는다. 테스트는 {@code MutableClock}으로 교체한다.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
