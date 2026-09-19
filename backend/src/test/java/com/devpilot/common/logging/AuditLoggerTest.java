package com.devpilot.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devpilot.testsupport.AuditLogCapture;
import com.devpilot.testsupport.UnitTest;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** docs/07 §6.3: 이벤트 필드 목록 고정, 커밋 후 기록, 롤백 시 미기록. */
@UnitTest
class AuditLoggerTest {

    private final AuditLogger auditLogger = new AuditLogger();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldLogEventWithFieldsWhenFieldNamesMatchCatalog() {
        try (AuditLogCapture capture = AuditLogCapture.start()) {
            auditLogger.log(
                    AuditEvent.AUTH_USER_PROVISIONED,
                    Map.of("userRef", "e3ef633734c3", "matchedBy", "EMAIL"));

            assertThat(capture.events()).containsExactly("AUTH_USER_PROVISIONED");
            assertThat(capture.fields(0))
                    .extracting(pair -> pair.key)
                    .containsExactlyInAnyOrder("event", "userRef", "matchedBy");
        }
    }

    @Test
    void shouldRejectWhenFieldNamesDifferFromCatalog() {
        assertThatThrownBy(
                        () ->
                                auditLogger.log(
                                        AuditEvent.AUTH_USER_PROVISIONED,
                                        Map.of("userRef", "x", "email", "owner@devpilot.test")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldLogImmediatelyWhenNoTransactionIsActive() {
        try (AuditLogCapture capture = AuditLogCapture.start()) {
            auditLogger.logAfterCommit(
                    AuditEvent.AUTH_USER_PROVISIONED, Map.of("userRef", "r", "matchedBy", "EMAIL"));

            assertThat(capture.events()).containsExactly("AUTH_USER_PROVISIONED");
        }
    }

    @Test
    void shouldLogOnlyAfterCommitWhenTransactionIsActive() {
        TransactionSynchronizationManager.initSynchronization();
        try (AuditLogCapture capture = AuditLogCapture.start()) {
            auditLogger.logAfterCommit(
                    AuditEvent.AUTH_USER_PROVISIONED, Map.of("userRef", "r", "matchedBy", "EMAIL"));
            assertThat(capture.events()).isEmpty();

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            assertThat(capture.events()).containsExactly("AUTH_USER_PROVISIONED");
        }
    }

    @Test
    void shouldNotLogWhenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        try (AuditLogCapture capture = AuditLogCapture.start()) {
            auditLogger.logAfterCommit(
                    AuditEvent.AUTH_USER_PROVISIONED, Map.of("userRef", "r", "matchedBy", "EMAIL"));

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(
                            sync ->
                                    sync.afterCompletion(
                                            TransactionSynchronization.STATUS_ROLLED_BACK));

            assertThat(capture.events()).isEmpty();
        }
    }

    @Test
    void shouldHashEmailWhenEmailRefIsComputed() {
        assertThat(AuditLogger.emailRef("owner@devpilot.test")).isEqualTo("2a9fa3d44e9f");
    }
}
