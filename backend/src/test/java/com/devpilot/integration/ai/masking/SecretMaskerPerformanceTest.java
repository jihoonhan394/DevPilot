package com.devpilot.integration.ai.masking;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.logging.AuditLogger;
import com.devpilot.common.logging.UserRefCalculator;
import com.devpilot.testsupport.TestProperties;
import com.devpilot.testsupport.UnitTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * docs/17 §7.3 끝, docs/09 §10.2: 30KB(1000줄) 입력 × 11개 패턴이 200ms 안에 끝난다. 벽시계 단언이므로 상한을 넉넉히 두고, 목표는
 * 10ms다. catastrophic backtracking이 있으면 이 상한을 크게 넘는다.
 */
@UnitTest
class SecretMaskerPerformanceTest {

    @Test
    void shouldMaskThirtyKilobytesWithinTwoHundredMillisecondsWhenInputIsCodeLike() {
        SecretMasker masker =
                new SecretMasker(
                        new AuditLogger(),
                        new UserRefCalculator(TestProperties.testProfile(), new MockEnvironment()));
        StringBuilder code = new StringBuilder();
        for (int line = 0; line < 1000; line++) {
            code.append("    private final String password")
                    .append(line % 10)
                    .append(" = props.value(); // ok\n");
        }
        String input = code.substring(0, Math.min(code.length(), 30_000));
        masker.mask(input);

        long started = System.nanoTime();
        MaskingResult result = masker.mask(input);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(result.blocked()).isFalse();
        assertThat(elapsed).isLessThan(Duration.ofMillis(200));
    }
}
