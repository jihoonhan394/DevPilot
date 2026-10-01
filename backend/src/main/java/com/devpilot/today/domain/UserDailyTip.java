package com.devpilot.today.domain;

import com.devpilot.learning.domain.TipFeedback;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * 그 사용자에게 보여 준 팁 (docs/04 §4.11 {@code user_daily_tip}, I-26).
 *
 * <p>팁 본문은 콘텐츠라 여기 남는 것은 <b>언제 보여 줬고 무엇을 골랐는가</b>뿐이다(ADR-041). 사용자·팁당 한 행이고 ({@code
 * user_daily_tip_unique}) 그래서 같은 팁을 두 번 제안하지 않는다(docs/06 §5.12 TIP-2).
 */
@Entity
@Table(name = "user_daily_tip")
public class UserDailyTip implements Persistable<UUID> {

    @Id private UUID id;

    @Transient private boolean newEntity = true;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "tip_key", nullable = false, updatable = false, length = 120)
    private String tipKey;

    @Column(name = "shown_on", nullable = false, updatable = false)
    private LocalDate shownOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback", length = 20)
    private @Nullable TipFeedback feedback;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserDailyTip() {}

    /** 오늘 고른 팁을 표시 기록으로 남긴다 (docs/05 §20.2 4단계). {@code feedback}은 아직 없다. */
    public static UserDailyTip shown(UUID userId, String tipKey, LocalDate shownOn, Instant now) {
        UserDailyTip tip = new UserDailyTip();
        tip.id = UUID.randomUUID();
        tip.userId = userId;
        tip.tipKey = tipKey;
        tip.shownOn = shownOn;
        tip.createdAt = now;
        return tip;
    }

    /**
     * 읽은 뒤 고른 값을 한 번만 기록한다 (docs/05 §20.3 3단계).
     *
     * @return 이번에 기록했으면 true, 이미 값이 있어 그대로 둔 것이면 false
     */
    public boolean chooseFeedback(TipFeedback chosen) {
        if (feedback != null) {
            return false;
        }
        feedback = chosen;
        return true;
    }

    @Override
    public @NonNull UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @jakarta.persistence.PostLoad
    @jakarta.persistence.PostPersist
    void markNotNew() {
        this.newEntity = false;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTipKey() {
        return tipKey;
    }

    public LocalDate getShownOn() {
        return shownOn;
    }

    public @Nullable TipFeedback getFeedback() {
        return feedback;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UserDailyTip tip && Objects.equals(id, tip.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
