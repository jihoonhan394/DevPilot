package com.devpilot.integration.ai.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.logging.AuditLogger;
import com.devpilot.integration.ai.api.AiBalance;
import com.devpilot.integration.ai.api.AiCallStatus;
import com.devpilot.integration.ai.api.AiProvider;
import com.devpilot.integration.ai.api.AiProviderCall;
import com.devpilot.integration.ai.api.AiProviderException;
import com.devpilot.integration.ai.api.AiProviderResponse;
import com.devpilot.integration.ai.fake.FakeAiProvider;
import com.devpilot.testsupport.AuditLogCapture;
import com.devpilot.testsupport.MutableClock;
import com.devpilot.testsupport.TestClockConfig;
import com.devpilot.testsupport.TestProperties;
import com.devpilot.testsupport.UnitTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * docs/17 §8.7 M1~M7 ({@code min-balance-usd = 1.00}). {@code FakeAiProvider.setBalance} hook으로 값을
 * 넣는다.
 */
@UnitTest
class AiBalanceMonitorTest {

    private final FakeAiProvider provider = new FakeAiProvider(TestProperties.testProfile());
    private final AiBalanceMonitor monitor =
            new AiBalanceMonitor(
                    new AuditLogger(),
                    new MutableClock(TestClockConfig.DEFAULT_INSTANT),
                    TestProperties.testProfile());
    private final AiBalanceCheckJob job =
            new AiBalanceCheckJob(provider, monitor, new AuditLogger());

    @Test
    void shouldStayEnabledWhenBalanceEqualsMinimum() {
        try (AuditLogCapture capture = AuditLogCapture.start()) {
            provider.setBalance(true, "1.00");

            job.runOnce();

            assertThat(monitor.exhausted()).isFalse();
            assertThat(capture.events()).isEmpty();
        }
    }

    @Test
    void shouldExhaustAndAuditOnceWhenBalanceIsBelowMinimum() {
        try (AuditLogCapture capture = AuditLogCapture.start()) {
            provider.setBalance(true, "0.99");

            job.runOnce();

            assertThat(monitor.exhausted()).isTrue();
            assertThat(monitor.reason()).isEqualTo("BALANCE_BELOW_MIN");
            assertThat(capture.events()).containsExactly("AI_BALANCE_LOW");
            assertThat(capture.fields(0))
                    .filteredOn(pair -> pair.key.equals("balanceUsd"))
                    .extracting(pair -> pair.value)
                    .containsExactly("0.99");

            provider.setBalance(true, "0.50");
            job.runOnce();

            assertThat(monitor.exhausted()).isTrue();
            assertThat(capture.events()).containsExactly("AI_BALANCE_LOW");
        }
    }

    @Test
    void shouldRestoreWithoutAuditWhenBalanceRecovers() {
        provider.setBalance(true, "0.99");
        job.runOnce();
        try (AuditLogCapture capture = AuditLogCapture.start()) {
            provider.setBalance(true, "5.00");

            job.runOnce();

            assertThat(monitor.exhausted()).isFalse();
            assertThat(capture.events()).isEmpty();
            assertThat(monitor.lastBalanceUsd()).isEqualByComparingTo(new BigDecimal("5.00"));
        }
    }

    @Test
    void shouldExhaustImmediatelyWhenInsufficientBalanceResponseArrives() {
        try (AuditLogCapture capture = AuditLogCapture.start()) {
            monitor.onInsufficientBalance();

            assertThat(monitor.exhausted()).isTrue();
            assertThat(capture.events()).containsExactly("AI_BALANCE_LOW");
            assertThat(capture.fields(0))
                    .filteredOn(pair -> pair.key.equals("reason"))
                    .extracting(pair -> pair.value)
                    .containsExactly("INSUFFICIENT_BALANCE_RESPONSE");
        }
    }

    @Test
    void shouldExhaustWhenAccountIsUnavailable() {
        provider.setBalance(false, "3.00");

        job.runOnce();

        assertThat(monitor.exhausted()).isTrue();
        assertThat(monitor.reason()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void shouldKeepStateAndAuditJobFailureWhenLookupFails() {
        AiProvider failing =
                new AiProvider() {
                    @Override
                    public String name() {
                        return "deepseek";
                    }

                    @Override
                    public AiProviderResponse send(AiProviderCall call) {
                        throw new UnsupportedOperationException("not used");
                    }

                    @Override
                    public AiBalance checkBalance() {
                        throw new AiProviderException(
                                AiCallStatus.PROVIDER_ERROR, "io", true, null, null);
                    }
                };
        AiBalanceCheckJob failingJob = new AiBalanceCheckJob(failing, monitor, new AuditLogger());
        try (AuditLogCapture capture = AuditLogCapture.start()) {
            assertThat(failingJob.runOnce()).isFalse();

            assertThat(monitor.exhausted()).isFalse();
            assertThat(capture.events()).containsExactly("JOB_FAILED");
        }
    }

    @Test
    void shouldDoNothingWhenProviderDoesNotSupportBalance() {
        assertThat(job.runOnce()).isFalse();
        assertThat(monitor.lastCheckedAt()).isNull();
    }
}
