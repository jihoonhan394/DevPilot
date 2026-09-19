package com.devpilot.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.testsupport.TestProperties;
import com.devpilot.testsupport.UnitTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** docs/07 §6.2: {@code userRef = hex(HMAC-SHA256(key, userId))[0..12]}. 기대값은 외부 도구로 계산한 고정값이다. */
@UnitTest
class UserRefCalculatorTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void shouldMatchKnownHmacPrefixWhenKeyIsConfigured() {
        UserRefCalculator calculator =
                new UserRefCalculator(TestProperties.testProfile(), new MockEnvironment());

        assertThat(calculator.userRef(USER_ID)).isEqualTo("e3ef633734c3");
        assertThat(calculator.subjectRef("159725c9-9f1f-5187-844e-f474bd7e29b1"))
                .isEqualTo("54f892d71829");
    }

    @Test
    void shouldFailStartupWhenProdHasNoKey() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(
                        () ->
                                new UserRefCalculator(
                                        TestProperties.testProfile(
                                                Map.of("devpilot.security.log-hash-key", "")),
                                        prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEVPILOT_LOG_HASH_KEY is required");
    }

    @Test
    void shouldGenerateTemporaryKeyWhenNotProdAndKeyIsEmpty() {
        UserRefCalculator calculator =
                new UserRefCalculator(
                        TestProperties.testProfile(Map.of("devpilot.security.log-hash-key", "")),
                        new MockEnvironment());

        assertThat(calculator.userRef(USER_ID)).matches("^[0-9a-f]{12}$");
    }

    @Test
    void shouldFailWhenKeyIsNotSixtyFourHexCharacters() {
        assertThatThrownBy(() -> UserRefCalculator.resolveKey("abc", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64 hex");
    }

    @Test
    void shouldNotContainRawIdentifierWhenRefIsComputed() {
        UserRefCalculator calculator =
                new UserRefCalculator(TestProperties.testProfile(), new MockEnvironment());

        assertThat(calculator.userRef(USER_ID)).doesNotContain("0000");
    }
}
