package com.devpilot.training.application;

import java.time.ZoneId;
import java.util.UUID;

/**
 * 제출 평가를 시작하라 (docs/03 §5.3 AFTER_COMMIT 이벤트). {@code SubmissionEvaluationTask}가 받는다.
 *
 * @param zone 이벤트 {@code plan_date} 계산용 사용자 timezone (요청 밖에서 실행되므로 미리 담는다)
 */
public record SubmissionEvaluationRequested(
        UUID userId, UUID attemptId, UUID submissionId, ZoneId zone, int dayStartHour) {}
