package com.devpilot.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.testsupport.TestProperties;
import com.devpilot.testsupport.UnitTest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

@UnitTest
class AllowedUserPolicyTest {

    private final AllowedUserPolicy policy =
            new AllowedUserPolicy(
                    TestProperties.withSecurity(
                            new DevPilotProperties.Security(
                                    DevPilotProperties.AuthMode.DEVTOKEN,
                                    new DevPilotProperties.Devtoken(
                                            "http://localhost/dev", null, Duration.ofHours(720)),
                                    List.of(" Owner@DevPilot.test ", ""),
                                    List.of("00000000-0000-0000-0000-000000000009"),
                                    65536,
                                    Duration.ofMinutes(5),
                                    null)));

    @Test
    void shouldAllowEmailCaseInsensitivelyWhenInAllowlist() {
        assertThat(policy.isAllowed(" OWNER@devpilot.test", null)).isTrue();
    }

    @Test
    void shouldRejectWhenEmailIsNotInAllowlist() {
        assertThat(policy.isAllowed("stranger@devpilot.test", "x")).isFalse();
    }

    @Test
    void shouldAllowSubjectWhenEmailIsMissing() {
        assertThat(policy.isAllowed(null, "00000000-0000-0000-0000-000000000009")).isTrue();
    }

    @Test
    void shouldIgnoreBlankAllowlistEntries() {
        assertThat(policy.isAllowed("", null)).isFalse();
    }
}
