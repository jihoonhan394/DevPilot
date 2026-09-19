package com.devpilot.common.jpa;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.cfg.MappingSettings;
import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * JPA 공통 설정 (docs/03 §3.1 {@code common.jpa}, docs/08 §5.4).
 *
 * <ul>
 *   <li>Auditing 시각은 주입된 {@link Clock}에서 온다(무인자 now() 금지, ARCH-08).
 *   <li>jsonb 컬럼은 애플리케이션 {@link JsonMapper}(Jackson 3) 설정으로 직렬화한다. Hibernate 7은 classpath에 Jackson
 *       2가 있으면 Jackson 2 mapper를 먼저 고르므로(2026-09-19 확인, hibernate-core 7.4.5) 직접 지정한다. 알 수 없는 JSON
 *       필드는 읽을 때 무시한다(과거 데이터 호환).
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }

    @Bean
    public HibernatePropertiesCustomizer jsonFormatMapperCustomizer(JsonMapper jsonMapper) {
        JsonMapper storageMapper =
                jsonMapper
                        .rebuild()
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build();
        return properties ->
                properties.put(
                        MappingSettings.JSON_FORMAT_MAPPER,
                        new Jackson3JsonFormatMapper(storageMapper));
    }
}
