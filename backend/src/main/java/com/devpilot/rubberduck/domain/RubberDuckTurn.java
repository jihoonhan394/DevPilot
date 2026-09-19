package com.devpilot.rubberduck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * 러버덕 턴 (docs/04 §2 {@code rubber_duck_turn}, append-only). {@code user_text}는 {@code
 * SecretMasker}를 통과한 값만 저장한다(RD-6) — 마스킹 전 원문은 어디에도 남기지 않는다. {@code learner_stuck}은 서버가 판정한 값이고 AI
 * 출력이 아니다 (docs/06 §9.5 RD-3). 소유자 검증은 부모 세션으로 한다(I-15).
 */
@Entity
@Table(name = "rubber_duck_turn")
public class RubberDuckTurn implements Persistable<UUID> {

    @Id private UUID id;

    @Transient private boolean newEntity = true;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "turn_no", nullable = false, updatable = false)
    private short turnNo;

    @Column(name = "user_text", nullable = false, updatable = false)
    private String userText;

    @Column(name = "ai_question", updatable = false)
    private @Nullable String aiQuestion;

    @Column(name = "learner_stuck", nullable = false, updatable = false)
    private boolean learnerStuck;

    @Column(name = "ai_call_id", updatable = false)
    private @Nullable UUID aiCallId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RubberDuckTurn() {
        // JPA 전용
    }

    public static RubberDuckTurn record(Values values, Instant now) {
        RubberDuckTurn turn = new RubberDuckTurn();
        turn.id = UUID.randomUUID();
        turn.sessionId = Objects.requireNonNull(values.sessionId(), "sessionId");
        turn.turnNo = (short) values.turnNo();
        turn.userText = Objects.requireNonNull(values.userText(), "userText");
        turn.aiQuestion = values.aiQuestion();
        turn.learnerStuck = values.learnerStuck();
        turn.aiCallId = values.aiCallId();
        turn.createdAt = Objects.requireNonNull(now, "now");
        return turn;
    }

    @Override
    public @NonNull UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.newEntity = false;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getTurnNo() {
        return turnNo;
    }

    public String getUserText() {
        return userText;
    }

    public @Nullable String getAiQuestion() {
        return aiQuestion;
    }

    public boolean isLearnerStuck() {
        return learnerStuck;
    }

    public @Nullable UUID getAiCallId() {
        return aiCallId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RubberDuckTurn turn && id.equals(turn.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** 턴 값 (docs/05 §9.7 8단계). {@code userText}는 마스킹본이다. */
    public record Values(
            UUID sessionId,
            int turnNo,
            String userText,
            @Nullable String aiQuestion,
            boolean learnerStuck,
            @Nullable UUID aiCallId) {}
}
