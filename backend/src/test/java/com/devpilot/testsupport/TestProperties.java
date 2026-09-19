package com.devpilot.testsupport;

import com.devpilot.common.config.DevPilotProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Spring context 없이 {@link DevPilotProperties}를 만든다. 실제 {@code application.yml}(+ 선택적으로 {@code
 * application-test.yml})을 Boot {@link Binder}로 바인딩하므로 기본값·변환·compact constructor 검증이 운영과 같다. 시스템 환경
 * 변수는 읽지 않는다.
 */
public final class TestProperties {

    private TestProperties() {}

    /** {@code application.yml} + {@code application-test.yml} (test profile 값). */
    public static DevPilotProperties testProfile() {
        return bind(true, Map.of());
    }

    /**
     * {@code application.yml}만 + override. {@code APP_BASE_URL}처럼 기본값 없는 placeholder는 override로
     * 채운다.
     */
    public static DevPilotProperties defaults(Map<String, Object> overrides) {
        return bind(false, overrides);
    }

    public static DevPilotProperties testProfile(Map<String, Object> overrides) {
        return bind(true, overrides);
    }

    /** security 설정만 바꾼 test profile 값. */
    public static DevPilotProperties withSecurity(DevPilotProperties.Security security) {
        DevPilotProperties base = testProfile();
        return new DevPilotProperties(
                security,
                base.web(),
                base.time(),
                base.planner(),
                base.budget(),
                base.review(),
                base.skill(),
                base.privacy(),
                base.content(),
                base.ai(),
                base.rubberduck(),
                base.coach());
    }

    private static DevPilotProperties bind(boolean testProfile, Map<String, Object> overrides) {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new MapPropertySource("overrides", Map.copyOf(overrides)));
        if (testProfile) {
            load("application-test.yml").forEach(sources::addLast);
        }
        load("application.yml").forEach(sources::addLast);
        Binder binder =
                new Binder(
                        ConfigurationPropertySources.from(sources),
                        new PropertySourcesPlaceholdersResolver(sources));
        return binder.bind("devpilot", DevPilotProperties.class).get();
    }

    private static List<PropertySource<?>> load(String name) {
        try {
            return new YamlPropertySourceLoader().load(name, new ClassPathResource(name));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
