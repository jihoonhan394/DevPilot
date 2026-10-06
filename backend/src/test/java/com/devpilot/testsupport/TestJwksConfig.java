package com.devpilot.testsupport;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
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

    /**
     * JWT 만료를 <b>테스트 시계</b>로 검증한다.
     *
     * <p>테스트는 토큰을 고정된 {@link MutableClock}으로 발급하는데, Spring Security 기본 {@code
     * JwtTimestampValidator}는 <b>실제 시계</b>를 쓴다. 그래서 테스트 시계의 시각이 지나가는 날부터 인증이 필요한 모든 통합 테스트가 401로
     * 깨졌다 — 코드를 하나도 바꾸지 않아도 날짜가 바뀌면 깨진다(2026-10-06에 그렇게 됐다: {@code Jwt expired at
     * 2026-10-05T11:00:00Z}).
     *
     * <p>토큰 시각을 실제 시계로 바꾸는 쪽은 안 된다 — {@code auth_time}·{@code iat}를 업무 시계와 비교하는 규칙이 있다(최근 로그인 확인).
     * 그래서 <b>검증 쪽을 업무 시계에 맞춘다.</b>
     *
     * <p>{@code supabase} 모드에만 만든다. {@code devtoken} 모드 테스트는 앱의 {@code devTokenJwtDecoder}를 쓰므로 조건
     * 없이 두면 {@code JwtDecoder}가 둘이 되어 컨텍스트가 뜨지 않는다.
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "devpilot.security",
            name = "auth-mode",
            havingValue = "supabase")
    public JwtDecoder testJwtDecoder(TestJwksServer testJwksServer, Clock clock) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(testJwksServer.jwksUri())
                        .jwsAlgorithm(SignatureAlgorithm.ES256)
                        .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        timestamps, new JwtIssuerValidator(testJwksServer.issuer())));
        return decoder;
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
