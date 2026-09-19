package com.devpilot.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.testsupport.TestEcKeys;
import com.devpilot.testsupport.UnitTest;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** docs/09 §6.4 끝: prod profile + devtoken 기동 조건. */
@UnitTest
@ExtendWith(OutputCaptureExtension.class)
class DevTokenConfigTest {

    private static final String PRIVATE_NETWORK_WARNING =
            "expose this instance only on a private network";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    // application.yml 기본값 (devpilot.* 전체가 필수라서). 아래 값이 그 위에 덮인다
                    .withInitializer(new ConfigDataApplicationContextInitializer())
                    .withUserConfiguration(PropertiesConfig.class, DevTokenConfig.class)
                    .withPropertyValues(
                            "devpilot.security.auth-mode=devtoken",
                            "devpilot.security.devtoken.issuer=http://localhost/dev",
                            "devpilot.security.devtoken.token-ttl=720h",
                            "devpilot.security.max-request-body-bytes=65536",
                            "devpilot.web.app-base-url=http://localhost:5173");

    @Test
    void shouldFailStartupWhenProdHasNoSigningKey() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .rootCause()
                                        .hasMessageContaining("DEVPILOT_DEV_JWT_KEY is required"));
    }

    @Test
    void shouldStartAndWarnWhenProdHasSigningKey(CapturedOutput output) {
        String pem = TestEcKeys.pkcs8Pem(TestEcKeys.generate(), true);

        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("devpilot.security.devtoken.private-key-pem=" + pem)
                .run(context -> assertThat(context).hasSingleBean(DevTokenSigningKey.class));

        assertThat(output).contains(PRIVATE_NETWORK_WARNING);
    }

    @Test
    void shouldGenerateKeyWithoutPrivateNetworkWarningWhenNotProd(CapturedOutput output) {
        runner.run(context -> assertThat(context).hasSingleBean(DevTokenSigningKey.class));

        assertThat(output).contains("generated a new devtoken signing key");
        assertThat(output).doesNotContain(PRIVATE_NETWORK_WARNING);
    }

    @Test
    void shouldNotRegisterBeansWhenSupabaseMode() {
        runner.withPropertyValues("devpilot.security.auth-mode=supabase")
                .run(context -> assertThat(context).doesNotHaveBean(DevTokenSigningKey.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DevPilotProperties.class)
    static class PropertiesConfig {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
