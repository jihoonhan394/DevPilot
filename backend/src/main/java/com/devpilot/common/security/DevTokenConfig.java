package com.devpilot.common.security;

import com.devpilot.common.config.DevPilotProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * devtoken 모드 전용 bean (docs/03 §4.2, 결정 A). 서명 키, 발급용 {@link JwtEncoder}, 검증용 {@link JwtDecoder}를
 * 만든다. 검증은 메모리의 공개 JWK로만 하고 JWKS HTTP 조회가 없다.
 *
 * <p>prod profile에서 {@code DEVPILOT_DEV_JWT_KEY}가 비면 기동을 막고, 키가 있으면 "비공개 네트워크 전용" WARN을 1줄
 * 남긴다(docs/09 §6.4).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "devpilot.security",
        name = "auth-mode",
        havingValue = "devtoken",
        matchIfMissing = true)
public class DevTokenConfig {

    public static final String AUDIENCE = "authenticated";

    private static final Logger log = LoggerFactory.getLogger(DevTokenConfig.class);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    @Bean
    public DevTokenSigningKey devTokenSigningKey(
            DevPilotProperties properties, Environment environment) {
        String pem = properties.security().devtoken().privateKeyPem();
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        if (prod) {
            log.warn(
                    "auth-mode=devtoken: expose this instance only on a private network such as the"
                            + " tailnet; switch DEVPILOT_AUTH_MODE before any public deployment"
                            + " (docs/03 §4.2)");
        }
        if (pem == null || pem.isBlank()) {
            if (prod) {
                throw new IllegalStateException(
                        "DEVPILOT_DEV_JWT_KEY is required in the prod profile (docs/03 §4.2)");
            }
            log.warn(
                    "DEVPILOT_DEV_JWT_KEY is empty: generated a new devtoken signing key, tokens"
                            + " issued before this restart are invalid");
            return DevTokenSigningKey.generate();
        }
        return DevTokenSigningKey.fromPem(pem);
    }

    @Bean
    public JwtEncoder devTokenJwtEncoder(DevTokenSigningKey signingKey) {
        return new NimbusJwtEncoder(
                new ImmutableJWKSet<SecurityContext>(new JWKSet(signingKey.signingJwk())));
    }

    @Bean
    public JwtDecoder devTokenJwtDecoder(
            DevTokenSigningKey signingKey, DevPilotProperties properties, Clock clock) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSource(
                                new ImmutableJWKSet<SecurityContext>(
                                        new JWKSet(signingKey.publicJwk())))
                        .jwsAlgorithm(SignatureAlgorithm.ES256)
                        .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(CLOCK_SKEW);
        timestampValidator.setClock(clock);
        OAuth2TokenValidator<Jwt> validator =
                new DelegatingOAuth2TokenValidator<>(
                        List.of(
                                timestampValidator,
                                new JwtIssuerValidator(properties.security().devtoken().issuer()),
                                new JwtClaimValidator<List<String>>(
                                        JwtClaimNames.AUD,
                                        audience ->
                                                audience != null && audience.contains(AUDIENCE)),
                                new JwtClaimValidator<String>(
                                        JwtClaimNames.SUB, DevTokenConfig::isUuid)));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private static boolean isUuid(String subject) {
        if (subject == null) {
            return false;
        }
        try {
            return UUID.fromString(subject)
                    .toString()
                    .equals(subject.toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
