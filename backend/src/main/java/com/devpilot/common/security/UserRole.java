package com.devpilot.common.security;

/** 사용자 역할 (docs/04 §3). MVP에는 ADMIN 전용 endpoint가 없다(docs/07 §4.4). */
public enum UserRole {
    USER,
    ADMIN
}
