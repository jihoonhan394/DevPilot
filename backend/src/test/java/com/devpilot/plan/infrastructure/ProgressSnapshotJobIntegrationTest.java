package com.devpilot.plan.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.job.PerUserJob;
import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.AuditLogCapture;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * docs/03 §6 (BL-GOL-14, BL-FND-25), AC-03 S6, AC-17 S6. job은 스케줄러 대신 {@link PerUserJob#runOnce()}를
 * 직접 부른다(test profile은 스케줄러가 꺼져 있다). 대상 사용자는 Asia/Seoul, dayStartHour 4다.
 */
@IntegrationTest
class ProgressSnapshotJobIntegrationTest extends ApiTestSupport {

    @Autowired private ProgressSnapshotJob job;

    @Test
    void shouldUpsertSnapshotOnlyAtUsersDayStartHour() throws Exception {
        // AC-03 S6
        clock.setInstant(Instant.parse("2026-12-06T01:00:00Z"));
        TestUser user = TestUser.owner();
        Map<String, Object> request = TestApi.onboardingRequest();
        learningGoal(request).put("targetCompletionDate", "2027-04-01");
        api.onboard(user, request);
        UUID userId = userId(user);

        clock.setInstant(Instant.parse("2026-12-07T18:05:00Z"));
        job.runOnce();
        assertThat(snapshotDates(userId)).doesNotContain("2026-12-08");

        clock.setInstant(Instant.parse("2026-12-07T19:05:00Z"));
        job.runOnce();
        assertThat(snapshotDates(userId)).containsOnlyOnce("2026-12-08");

        job.runOnce();
        assertThat(snapshotDates(userId)).containsOnlyOnce("2026-12-08");
        Map<String, Object> snapshot =
                jdbc.queryForMap(
                        "select horizon_date::text as horizon, risk_level from"
                                + " devpilot.plan_progress_snapshot where user_id = ? and"
                                + " snapshot_date = date '2026-12-08'",
                        userId);
        assertThat(snapshot).containsEntry("horizon", "2027-04-01");
        assertThat(snapshot.get("risk_level")).isNotNull();
    }

    @Test
    void shouldContinueWithOtherUsersWhenOneFails() throws Exception {
        // AC-03 S6 마지막 항목: 한 사용자 실패 → 다음 사용자 처리, JOB_FAILED WARN 1줄
        TestUser broken = onboardedOwner();
        TestUser healthy = onboardedOwner();
        UUID brokenId = userId(broken);
        UUID healthyId = userId(healthy);
        jdbc.update(
                "update devpilot.app_user set timezone = 'Mars/Olympus' where id = ?", brokenId);
        clock.setInstant(Instant.parse("2026-10-05T19:05:00Z"));
        try (AuditLogCapture audit = AuditLogCapture.start()) {
            PerUserJob.JobResult result = job.runOnce();

            assertThat(result.failed()).isGreaterThanOrEqualTo(1);
            assertThat(result.processed()).isGreaterThanOrEqualTo(1);
            assertThat(audit.events()).contains("JOB_FAILED");
        } finally {
            jdbc.update(
                    "update devpilot.app_user set timezone = 'Asia/Seoul' where id = ?", brokenId);
        }
        assertThat(snapshotDates(healthyId)).contains("2026-10-06");
        assertThat(snapshotDates(brokenId)).doesNotContain("2026-10-06");
    }

    private List<String> snapshotDates(UUID userId) {
        return jdbc.queryForList(
                "select snapshot_date::text from devpilot.plan_progress_snapshot where user_id = ?",
                String.class,
                userId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> learningGoal(Map<String, Object> request) {
        return (Map<String, Object>) request.get("learningGoal");
    }
}
