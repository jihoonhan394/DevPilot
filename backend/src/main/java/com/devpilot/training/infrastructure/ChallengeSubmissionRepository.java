package com.devpilot.training.infrastructure;

import com.devpilot.training.domain.ChallengeSubmission;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** challenge submission (docs/04 §2). 소유자 검증은 부모 attempt 또는 {@code user_id}로 한다(I-15). */
public interface ChallengeSubmissionRepository extends JpaRepository<ChallengeSubmission, UUID> {

    /** attempt의 제출 목록 ({@code submissionNo} ASC). */
    List<ChallengeSubmission> findByAttemptIdOrderBySubmissionNoAsc(UUID attemptId);

    Optional<ChallengeSubmission> findByAttemptIdAndSubmissionNo(UUID attemptId, int submissionNo);

    /** 사용자의 {@code PENDING}/{@code RUNNING} 평가 수 ({@code AiPendingJobCounter}, docs/17 §8.1). */
    @Query(
            """
            select count(s) from ChallengeSubmission s
             where s.userId = :userId
               and s.evaluationStatus in (com.devpilot.common.async.AsyncJobStatus.PENDING,
                                          com.devpilot.common.async.AsyncJobStatus.RUNNING)
            """)
    int countPendingOrRunning(@Param("userId") UUID userId);

    /** 멈춘 평가 ({@code OrphanAsyncTaskJob}, docs/03 §5.3, {@code idx_challenge_submission_status}). */
    @Query(
            """
            select s from ChallengeSubmission s
             where s.evaluationStatus in (com.devpilot.common.async.AsyncJobStatus.PENDING,
                                          com.devpilot.common.async.AsyncJobStatus.RUNNING)
               and s.statusUpdatedAt < :staleBefore
            """)
    List<ChallengeSubmission> findStale(@Param("staleBefore") Instant staleBefore);
}
