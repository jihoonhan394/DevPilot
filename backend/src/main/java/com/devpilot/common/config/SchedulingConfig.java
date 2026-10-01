package com.devpilot.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄 job 활성화 (docs/03 §6·§10, BL-FND-25). 단일 인스턴스 전제다. {@code test} profile에서는 꺼서 job이 자동으로 돌지 않게
 * 하고, 테스트는 job 메서드를 직접 부른다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {}
