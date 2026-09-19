package com.devpilot.user.domain;

/** 계정 상태 (docs/04 §3, §4.7). {@code DELETION_REQUESTED} 이후 행 삭제는 AccountDeletionJob(Later). */
public enum UserStatus {
    ACTIVE,
    DELETION_REQUESTED
}
