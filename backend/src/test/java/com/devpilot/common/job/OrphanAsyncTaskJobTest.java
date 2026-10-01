package com.devpilot.common.job;

import static com.devpilot.testsupport.TestJobMetrics.jobMetrics;
import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.logging.AuditLogger;
import com.devpilot.testsupport.AuditLogCapture;
import com.devpilot.testsupport.MutableClock;
import com.devpilot.testsupport.TestClockConfig;
import com.devpilot.testsupport.TestProperties;
import com.devpilot.testsupport.UnitTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * docs/03 §5.3·§6 (BL-FND-23): {@code OrphanAsyncTaskJob}은 주입받은 sweeper 전부를 {@code now −
 * orphan-timeout}(10분)으로 부르고, 한 sweeper의 실패가 다음 sweeper를 막지 않는다.
 */
@UnitTest
class OrphanAsyncTaskJobTest {

    private final MutableClock clock = new MutableClock(TestClockConfig.DEFAULT_INSTANT);

    @Test
    void shouldCallEverySweeperWithTenMinuteCutoff() {
        List<Instant> cutoffs = new ArrayList<>();
        OrphanAsyncTaskSweeper first = sweeper("first", cutoffs, 2);
        OrphanAsyncTaskSweeper second = sweeper("second", cutoffs, 1);
        OrphanAsyncTaskJob job =
                new OrphanAsyncTaskJob(
                        List.of(first, second),
                        new AuditLogger(),
                        jobMetrics(),
                        clock,
                        TestProperties.testProfile());

        int interrupted = job.runOnce();

        assertThat(interrupted).isEqualTo(3);
        assertThat(cutoffs)
                .containsExactly(
                        TestClockConfig.DEFAULT_INSTANT.minus(Duration.ofMinutes(10)),
                        TestClockConfig.DEFAULT_INSTANT.minus(Duration.ofMinutes(10)));
    }

    @Test
    void shouldContinueWithNextSweeperWhenOneFails() {
        List<Instant> cutoffs = new ArrayList<>();
        OrphanAsyncTaskSweeper failing =
                new OrphanAsyncTaskSweeper() {
                    @Override
                    public String name() {
                        return "failing";
                    }

                    @Override
                    public int markInterrupted(Instant staleBefore) {
                        throw new IllegalStateException("db down");
                    }
                };
        OrphanAsyncTaskJob job =
                new OrphanAsyncTaskJob(
                        List.of(failing, sweeper("ok", cutoffs, 1)),
                        new AuditLogger(),
                        jobMetrics(),
                        clock,
                        TestProperties.testProfile());
        try (AuditLogCapture capture = AuditLogCapture.start()) {
            assertThat(job.runOnce()).isEqualTo(1);
            assertThat(capture.events()).containsExactly("JOB_FAILED");
        }
        assertThat(cutoffs).hasSize(1);
    }

    @Test
    void shouldDoNothingWhenNoModuleOwnsAsyncTasks() {
        OrphanAsyncTaskJob job =
                new OrphanAsyncTaskJob(
                        List.of(),
                        new AuditLogger(),
                        jobMetrics(),
                        clock,
                        TestProperties.testProfile());

        assertThat(job.runOnce()).isZero();
    }

    private static OrphanAsyncTaskSweeper sweeper(String name, List<Instant> cutoffs, int count) {
        return new OrphanAsyncTaskSweeper() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int markInterrupted(Instant staleBefore) {
                cutoffs.add(staleBefore);
                return count;
            }
        };
    }
}
