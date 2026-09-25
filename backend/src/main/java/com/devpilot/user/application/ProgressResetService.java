package com.devpilot.user.application;

import com.devpilot.common.error.ApiFieldError;
import com.devpilot.common.error.BusinessValidationException;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.FieldErrorCodes;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.logging.AuditEvent;
import com.devpilot.common.logging.AuditLogger;
import com.devpilot.common.logging.UserRefCalculator;
import com.devpilot.user.domain.AppUser;
import com.devpilot.user.infrastructure.AppUserRepository;
import com.devpilot.user.infrastructure.ProgressResetRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 학습 진도 초기화 (docs/05 §3.7, ADR-056, BL-SEC-19).
 *
 * <p>{@link AccountDeletionService}와 <b>다른 일</b>이다. 저쪽은 계정 삭제 요청이라 이후 거의 모든 요청이 403이 된다. 이쪽은 계정을
 * 그대로 두고 온보딩 이전 상태로 되돌린다 — 다시 시작하기다.
 *
 * <p>사용자가 직접 쓴 글(사이드 프로젝트와 그 기록)은 기본으로 남긴다. 진도가 아니라 기록이고, "진도 초기화"를 눌렀다가 몇 주치가 사라지면 사고다.
 */
@Service
public class ProgressResetService {

    /** 되돌릴 수 없는 일이라 "예/아니오"로 받지 않는다 (docs/02 §3.2). */
    static final String CONFIRMATION = "초기화합니다";

    private final AppUserRepository appUserRepository;
    private final ProgressResetRepository progressResetRepository;
    private final AuditLogger auditLogger;
    private final UserRefCalculator userRefCalculator;
    private final Clock clock;

    ProgressResetService(
            AppUserRepository appUserRepository,
            ProgressResetRepository progressResetRepository,
            AuditLogger auditLogger,
            UserRefCalculator userRefCalculator,
            Clock clock) {
        this.appUserRepository = appUserRepository;
        this.progressResetRepository = progressResetRepository;
        this.auditLogger = auditLogger;
        this.userRefCalculator = userRefCalculator;
        this.clock = clock;
    }

    /**
     * 진도를 지우고 온보딩 이전으로 되돌린다. 이미 초기화된 사용자가 다시 불러도 {@code deletedRows = 0}으로 성공한다(멱등).
     *
     * @throws BusinessValidationException 확인 문구가 정확하지 않을 때 (docs/05 §3.7 1단계)
     */
    @Transactional
    public ResetResult reset(UUID userId, String confirmation, boolean includeProjects) {
        if (!CONFIRMATION.equals(confirmation.strip())) {
            throw new BusinessValidationException(
                    "confirmation must be the exact phrase",
                    List.of(ApiFieldError.of("confirmation", FieldErrorCodes.VALUE_NOT_ALLOWED)));
        }
        AppUser user =
                appUserRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "user not found"));
        int deleted = progressResetRepository.deleteProgress(userId);
        if (includeProjects) {
            deleted += progressResetRepository.deleteProjects(userId);
        }
        user.resetProgress();
        Instant resetAt = clock.instant();
        auditLogger.log(
                AuditEvent.ACCOUNT_PROGRESS_RESET,
                Map.of(
                        "userRef",
                        userRefCalculator.userRef(userId),
                        "resetAt",
                        resetAt.toString(),
                        "deletedRows",
                        deleted,
                        "projectsDeleted",
                        includeProjects));
        return new ResetResult(resetAt, deleted, includeProjects);
    }

    /**
     * @param deletedRows 지운 행 수 합계. 화면에는 쓰지 않고 로그·테스트에서만 본다
     */
    public record ResetResult(Instant resetAt, int deletedRows, boolean projectsDeleted) {}
}
