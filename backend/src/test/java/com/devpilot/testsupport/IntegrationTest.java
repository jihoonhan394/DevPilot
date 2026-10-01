package com.devpilot.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 통합 테스트 조합 (docs/09 §3.3). Spring context 캐시가 재사용되도록 설정을 이 애노테이션 하나로 고정한다. MockMvc를 여기서 켜서 모든 통합
 * 테스트가 같은 context를 쓴다. AI provider는 test profile의 {@code fake}다(FakeAiProvider).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestcontainersConfig.class, TestClockConfig.class, TestJwksConfig.class})
public @interface IntegrationTest {}
