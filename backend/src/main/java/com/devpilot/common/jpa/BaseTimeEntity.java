package com.devpilot.common.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * {@code created_at}·{@code updated_at}을 JPA Auditing으로 채우는 entity 상위 타입 (docs/03 §3.1, docs/08
 * §5.1). 시각은 주입된 {@code Clock}을 쓰는 {@code DateTimeProvider}(JpaConfig)에서 온다. {@code updated_at}은 값이
 * 실제로 바뀌어 UPDATE가 나갈 때만 갱신된다.
 *
 * <p>id는 애플리케이션이 만든 UUID이므로 {@link Persistable#isNew()}로 새 entity를 알린다(BL-FND-11). 그래야 {@code
 * save()}가 {@code merge}(조회 후 복사본 저장) 대신 {@code persist}를 쓰고, 호출자가 가진 인스턴스에 auditing 값이 채워진다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity implements Persistable<UUID> {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @Transient private boolean newEntity = true;

    protected BaseTimeEntity() {}

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.newEntity = false;
    }

    /** 저장 전에는 null. */
    public @Nullable Instant getCreatedAt() {
        return createdAt;
    }

    /** 저장 전에는 null. */
    public @Nullable Instant getUpdatedAt() {
        return updatedAt;
    }
}
