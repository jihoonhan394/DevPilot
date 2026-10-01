package com.devpilot.training.application;

import com.devpilot.integration.ai.api.AiBudgetDecision;
import com.devpilot.integration.ai.api.AiConcurrencyReservation;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.budget.AiBudgetGuard;
import com.devpilot.integration.ai.masking.SecretMasker;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 제출을 저장하기 전에 통과해야 하는 검사 (docs/07: 마스킹 → 예산 → 저장). 순서가 규칙이라 한 곳에 모아 둔다. {@link SubmissionService}의
 * 저장 트랜잭션 밖에서 부른다.
 */
@Component
class SubmissionIntakeGuard {

    private static final String MASKING_SOURCE = "CHALLENGE_SUBMISSION";

    private final SecretMasker secretMasker;
    private final AiBudgetGuard aiBudgetGuard;

    SubmissionIntakeGuard(SecretMasker secretMasker, AiBudgetGuard aiBudgetGuard) {
        this.secretMasker = secretMasker;
        this.aiBudgetGuard = aiBudgetGuard;
    }

    /** 자유 텍스트 마스킹 (docs/05 §1.11). private key가 있으면 422이고 아무것도 저장하기 전이다. */
    @Nullable String masked(UUID userId, @Nullable String text) {
        return secretMasker.maskOrRejectNullable(userId, MASKING_SOURCE, text);
    }

    /**
     * docs/05 §1.8 "시작 전 검사 순서": 저장 전에 AI 가능 여부·예산·동시 실행을 본다. 예약은 바로 닫는다 — 저장된 {@code PENDING} 제출이
     * 그 뒤로 동시 실행 수에 잡힌다({@code AiPendingJobCounter}).
     */
    void requireBudget(UUID userId) {
        AiBudgetDecision decision = aiBudgetGuard.check(userId, AiOperation.CHALLENGE_EVALUATE);
        AiConcurrencyReservation reservation = decision.reservation();
        try {
            decision.requireAllowed();
        } finally {
            reservation.close();
        }
    }
}
