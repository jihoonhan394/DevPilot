package com.devpilot.common.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.logging.AuditLogger;
import com.devpilot.common.logging.UserRefCalculator;
import com.devpilot.testsupport.AuditLogCapture;
import com.devpilot.testsupport.MutableClock;
import com.devpilot.testsupport.TestProperties;
import com.devpilot.testsupport.UnitTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** BL-FND-25 스케줄 job 공통 규약: 사용자 단위 처리, 실패 격리, {@code JOB_FAILED} WARN (docs/03 §6·§7). */
@UnitTest
class PerUserJobTest {

    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FAILING = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SKIPPED = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID LAST = UUID.fromString("00000000-0000-0000-0000-000000000004");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-12-07T19:05:00Z"));
    private final UserRefCalculator userRefCalculator =
            new UserRefCalculator(TestProperties.testProfile(), new MockEnvironment());

    @Test
    void shouldContinueWithNextUserWhenOneUserFails() {
        RecordingJob job = new RecordingJob();

        try (AuditLogCapture capture = AuditLogCapture.start()) {
            PerUserJob.JobResult result = job.runOnce();

            assertThat(result).isEqualTo(new PerUserJob.JobResult(2, 1));
            assertThat(job.visited).containsExactly(FIRST, FAILING, SKIPPED, LAST);
            assertThat(capture.events()).containsExactly("JOB_FAILED");
            assertThat(capture.fields(0))
                    .extracting(pair -> pair.key)
                    .containsExactlyInAnyOrder(
                            "event", "job", "userRef", "errorType", "durationMs");
            assertThat(capture.fields(0))
                    .filteredOn(pair -> "job".equals(pair.key))
                    .extracting(pair -> pair.value)
                    .containsExactly("RecordingJob");
            assertThat(capture.fields(0))
                    .filteredOn(pair -> "userRef".equals(pair.key))
                    .extracting(pair -> pair.value)
                    .containsExactly(userRefCalculator.userRef(FAILING));
            assertThat(capture.fields(0))
                    .filteredOn(pair -> "errorType".equals(pair.key))
                    .extracting(pair -> pair.value)
                    .containsExactly("IllegalStateException");
        }
    }

    @Test
    void shouldPassRunStartInstantToEveryUser() {
        RecordingJob job = new RecordingJob();

        job.runOnce();

        assertThat(job.instants).containsOnly(Instant.parse("2026-12-07T19:05:00Z"));
    }

    /** FAILING은 예외, SKIPPED는 대상 아님. */
    private final class RecordingJob extends PerUserJob {

        private final List<UUID> visited = new ArrayList<>();
        private final Set<Instant> instants = new HashSet<>();

        RecordingJob() {
            super(new AuditLogger(), userRefCalculator, clock);
        }

        @Override
        protected String name() {
            return "RecordingJob";
        }

        @Override
        protected List<UUID> candidates(Instant now) {
            return List.of(FIRST, FAILING, SKIPPED, LAST);
        }

        @Override
        protected boolean process(UUID userId, Instant now) {
            visited.add(userId);
            instants.add(now);
            if (FAILING.equals(userId)) {
                throw new IllegalStateException("boom for " + userId);
            }
            return !SKIPPED.equals(userId);
        }
    }
}
