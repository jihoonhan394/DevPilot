package com.devpilot.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * supabase 모드 JWT 검증 경로를 로컬 {@link TestJwksServer}로 연결한다 (docs/09 §6.2). issuer·JWKS URI·알고리즘을
 * {@link DynamicPropertyRegistrar}로 직접 등록한다 — application.yml의 {@code SUPABASE_URL} placeholder에
 * 의존하지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwksConfig {

    @Bean(destroyMethod = "close")
    public TestJwksServer testJwksServer() {
        return TestJwksServer.start();
    }

    @Bean
    public DynamicPropertyRegistrar testJwksProperties(TestJwksServer testJwksServer) {
        return registry -> {
            registry.add(
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri", testJwksServer::issuer);
            registry.add(
                    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                    testJwksServer::jwksUri);
            registry.add("spring.security.oauth2.resourceserver.jwt.jws-algorithms", () -> "ES256");
        };
    }
}
