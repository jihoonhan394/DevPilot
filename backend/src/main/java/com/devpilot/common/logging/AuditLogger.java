package com.devpilot.common.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 감사 로그 (docs/03 §8, docs/07 §6.3). 로거 이름은 {@code com.devpilot.audit}, 필드 {@code event}와 호출자가 준
 * key-value. 이메일·토큰·요청 body 원문을 넣지 않는다 — 이메일은 {@link #emailRef(String)}, 사용자는 {@code userRef}로만
 * 남긴다. 필드 이름은 {@link AuditEvent#fieldNames()}와 정확히 같아야 한다.
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger("com.devpilot.audit");
    private static final int REF_HEX_LENGTH = 12;

    /** 거부·실패 시점에 바로 남기는 이벤트 (docs/07 §6.3 끝의 예외 목록). */
    public void log(AuditEvent event, Map<String, ?> fields) {
        requireFields(event, fields);
        LoggingEventBuilder builder =
                (event.warnLevel() ? log.atWarn() : log.atInfo())
                        .addKeyValue("event", event.name());
        fields.forEach(builder::addKeyValue);
        builder.log("audit {}", event.name());
    }

    /** 커밋 후에 남긴다 (docs/07 §6.3 "감사 이벤트는 커밋 후 기록"). 활성 트랜잭션이 없으면 바로 남긴다. 롤백되면 남기지 않는다. */
    public void logAfterCommit(AuditEvent event, Map<String, ?> fields) {
        requireFields(event, fields);
        Map<String, Object> snapshot = new LinkedHashMap<>(fields);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log(event, snapshot);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        log(event, snapshot);
                    }
                });
    }

    /** 이메일 SHA-256 앞 12 hex (docs/05 §1.4.5). */
    public static String emailRef(String normalizedEmail) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(normalizedEmail.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, REF_HEX_LENGTH);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void requireFields(AuditEvent event, Map<String, ?> fields) {
        if (!event.fieldNames().equals(fields.keySet())) {
            throw new IllegalArgumentException(
                    "audit fields for "
                            + event
                            + " must be "
                            + event.fieldNames()
                            + " but were "
                            + fields.keySet());
        }
    }
}
