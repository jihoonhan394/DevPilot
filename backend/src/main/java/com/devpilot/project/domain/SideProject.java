package com.devpilot.project.domain;

import com.devpilot.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 사이드 프로젝트 (docs/04 §2 {@code side_project}). 학습이 적용될 대상이다. {@code repo_url}은 저장만 한다 — 서버는 이 URL을
 * 요청하지 않는다(docs/07 §5.5). {@code updated_at}·{@code version}은 값이 실제로 바뀔 때만 오른다(docs/05 §19.5).
 */
@Entity
@Table(name = "side_project")
public class SideProject extends BaseTimeEntity {

    @Id private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column private @Nullable String description;

    @Column(name = "repo_url")
    private @Nullable String repoUrl;

    @Column private @Nullable String stack;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SideProjectStatus status;

    @Version private @Nullable Long version;

    protected SideProject() {
        // JPA 전용
    }

    private SideProject(UUID userId, ProjectValues values) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.name = Objects.requireNonNull(values.name(), "name");
        this.description = values.description();
        this.repoUrl = values.repoUrl();
        this.stack = values.stack();
        this.status = SideProjectStatus.ACTIVE;
    }

    /** 등록 (docs/05 §19.2): {@code status = ACTIVE}. 빈 문자열 정규화·URL 검사는 호출자가 끝냈다. */
    public static SideProject create(UUID userId, ProjectValues values) {
        return new SideProject(userId, values);
    }

    /**
     * PATCH (docs/05 §19.5). {@code null}은 변경하지 않는다. 호출자가 빈 문자열을 "지움"으로 바꿔 {@link Change#clear()}로
     * 넘긴다. 실제로 바뀐 값이 있으면 {@code true}.
     */
    public boolean update(
            @Nullable String newName,
            Change newDescription,
            Change newRepoUrl,
            Change newStack,
            @Nullable SideProjectStatus newStatus) {
        boolean changed = false;
        if (newName != null && !newName.equals(name)) {
            name = newName;
            changed = true;
        }
        if (newDescription.applies() && !Objects.equals(newDescription.value(), description)) {
            description = newDescription.value();
            changed = true;
        }
        if (newRepoUrl.applies() && !Objects.equals(newRepoUrl.value(), repoUrl)) {
            repoUrl = newRepoUrl.value();
            changed = true;
        }
        if (newStack.applies() && !Objects.equals(newStack.value(), stack)) {
            stack = newStack.value();
            changed = true;
        }
        if (newStatus != null && newStatus != status) {
            status = newStatus;
            changed = true;
        }
        return changed;
    }

    @Override
    public @NonNull UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public @Nullable String getRepoUrl() {
        return repoUrl;
    }

    public @Nullable String getStack() {
        return stack;
    }

    public SideProjectStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version == null ? 0L : version;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SideProject project && id != null && id.equals(project.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /** 등록 값. 선택 필드는 null이면 비어 있다. */
    public record ProjectValues(
            String name,
            @Nullable String description,
            @Nullable String repoUrl,
            @Nullable String stack) {}

    /**
     * nullable 필드의 PATCH 의도: 변경 안 함({@link #keep()}), 지움({@link #clear()}), 새 값({@link
     * #to(String)}).
     *
     * @param applies false면 변경하지 않는다
     */
    public record Change(boolean applies, @Nullable String value) {

        public static Change keep() {
            return new Change(false, null);
        }

        public static Change clear() {
            return new Change(true, null);
        }

        public static Change to(String value) {
            return new Change(true, Objects.requireNonNull(value, "value"));
        }
    }
}
