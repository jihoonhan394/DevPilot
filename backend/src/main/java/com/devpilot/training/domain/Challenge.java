package com.devpilot.training.domain;

import com.devpilot.common.async.AsyncFailureCode;
import com.devpilot.common.async.AsyncJobStatus;
import com.devpilot.common.domain.ContentOrigin;
import com.devpilot.learning.domain.HintLevel;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * 연습·진단 문제 (docs/04 §2 {@code challenge} + {@code challenge_skill}). {@code owner_user_id}가 null이면
 * 공용 seed다. seed는 {@code ContentSeeder}가 갱신하고(docs/04 §9), 그 외는 생성 후 본문이 바뀌지 않는다 — 상태만 바뀐다.
 *
 * <p>{@code hints_json}은 1~3단계 사전 hint다(HL-6). 어떤 view에도 넣지 않고 hint endpoint로만 공개한다(docs/05
 * §10.1).
 */
@Entity
@Table(name = "challenge")
public class Challenge implements Persistable<UUID> {

    @Id private UUID id;

    @Transient private boolean newEntity;

    @Column(name = "owner_user_id", updatable = false)
    private @Nullable UUID ownerUserId;

    @Column(name = "seed_key", updatable = false)
    private @Nullable String seedKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ContentOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChallengeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_status", nullable = false)
    private AsyncJobStatus generationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code")
    private @Nullable AsyncFailureCode failureCode;

    @Column(name = "status_updated_at", nullable = false)
    private Instant statusUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChallengePurpose purpose;

    @Column(name = "is_transfer", nullable = false)
    private boolean transferChallenge;

    @Column private @Nullable String title;

    @Column(nullable = false)
    private short difficulty;

    @Column(name = "estimated_minutes")
    private @Nullable Integer estimatedMinutes;

    @Column private @Nullable String scenario;

    @Column private @Nullable String prompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "constraints_json")
    private @Nullable List<String> constraints;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expected_concepts_json")
    private @Nullable List<String> expectedConcepts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rubric_json")
    private @Nullable List<ChallengeRubricItem> rubric;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "common_mistakes_json")
    private @Nullable List<String> commonMistakes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transfer_targets_json")
    private @Nullable List<String> transferTargets;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hints_json")
    private @Nullable Map<String, String> hints;

    @Column(name = "ai_call_id")
    private @Nullable UUID aiCallId;

    @Column(name = "prompt_version")
    private @Nullable String promptVersion;

    @Column(name = "rejection_reason")
    private @Nullable String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "challenge_skill",
            joinColumns = @JoinColumn(name = "challenge_id", nullable = false))
    @Column(name = "skill_id", nullable = false)
    private Set<UUID> skillIds = new LinkedHashSet<>();

    protected Challenge() {
        // JPA 전용
    }

    /** seed challenge 1개 (docs/04 §9). 검증은 {@code ChallengeValidationService}가 한다. */
    public static Challenge seed(String seedKey, ChallengeContent content, Instant now) {
        Challenge challenge = new Challenge();
        challenge.id = UUID.randomUUID();
        challenge.newEntity = true;
        challenge.seedKey = Objects.requireNonNull(seedKey, "seedKey");
        challenge.origin = ContentOrigin.SEED;
        challenge.status = ChallengeStatus.DRAFT;
        challenge.generationStatus = AsyncJobStatus.COMPLETED;
        challenge.statusUpdatedAt = Objects.requireNonNull(now, "now");
        challenge.createdAt = now;
        challenge.applyStructure(content);
        challenge.applyText(content);
        return challenge;
    }

    /** seed 갱신: 구조 필드는 그대로 두고 텍스트·보조 필드만 바꾼다(docs/04 §9 2단계). */
    public void updateSeedText(ChallengeContent content) {
        applyText(content);
    }

    /** seed 구조가 바뀌었는지 (docs/04 §9 2단계 — 다르면 기동 실패다). */
    public boolean structureMatches(ChallengeContent content) {
        return skillIds.equals(content.skillIds())
                && difficulty == content.difficulty()
                && purpose == content.purpose()
                && transferChallenge == content.transferChallenge()
                && structureOf(rubric).equals(structureOf(content.rubric()))
                && Objects.equals(expectedConcepts, content.expectedConcepts());
    }

    /** 검증 통과 (I-09·I-10). */
    public void validated(Instant now) {
        this.status = ChallengeStatus.VALIDATED;
        this.rejectionReason = null;
        this.statusUpdatedAt = Objects.requireNonNull(now, "now");
    }

    /** 검증 실패. 사유는 사용자에게 보여 주지 않는다(docs/05 §10.4). */
    public void rejected(String reason, Instant now) {
        this.status = ChallengeStatus.REJECTED;
        this.rejectionReason = Objects.requireNonNull(reason, "reason");
        this.statusUpdatedAt = Objects.requireNonNull(now, "now");
    }

    /** YAML에서 사라진 seed (docs/04 §9 3단계). */
    public void retire(Instant now) {
        this.status = ChallengeStatus.RETIRED;
        this.statusUpdatedAt = Objects.requireNonNull(now, "now");
    }

    /** 사전 hint가 있는 단계 (HL-6). */
    public Set<HintLevel> pregeneratedHintLevels() {
        if (hints == null) {
            return Set.of();
        }
        Set<HintLevel> levels = new LinkedHashSet<>();
        for (HintLevel level : HintLevel.values()) {
            if (hints.containsKey(level.name())) {
                levels.add(level);
            }
        }
        return Set.copyOf(levels);
    }

    /** 그 단계의 사전 hint 내용. 없으면 null. */
    public @Nullable String pregeneratedHint(HintLevel level) {
        return hints == null ? null : hints.get(level.name());
    }

    private void applyStructure(ChallengeContent content) {
        this.purpose = Objects.requireNonNull(content.purpose(), "purpose");
        this.difficulty = (short) content.difficulty();
        this.transferChallenge = content.transferChallenge();
        this.skillIds = new LinkedHashSet<>(content.skillIds());
        this.expectedConcepts = List.copyOf(content.expectedConcepts());
    }

    private void applyText(ChallengeContent content) {
        this.title = content.title();
        this.estimatedMinutes = content.estimatedMinutes();
        this.scenario = content.scenario();
        this.prompt = content.prompt();
        this.constraints = List.copyOf(content.constraints());
        this.rubric = List.copyOf(content.rubric());
        this.commonMistakes = List.copyOf(content.commonMistakes());
        this.transferTargets = List.copyOf(content.transferTargets());
        this.hints = Map.copyOf(new LinkedHashMap<>(content.hints()));
        this.expectedConcepts = List.copyOf(content.expectedConcepts());
    }

    private static List<String> structureOf(@Nullable List<ChallengeRubricItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> item.id() + ":" + item.weightBp() + ":" + item.axis())
                .toList();
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

    public @Nullable UUID getOwnerUserId() {
        return ownerUserId;
    }

    public @Nullable String getSeedKey() {
        return seedKey;
    }

    public ContentOrigin getOrigin() {
        return origin;
    }

    public ChallengeStatus getStatus() {
        return status;
    }

    public AsyncJobStatus getGenerationStatus() {
        return generationStatus;
    }

    public @Nullable AsyncFailureCode getFailureCode() {
        return failureCode;
    }

    public Instant getStatusUpdatedAt() {
        return statusUpdatedAt;
    }

    public ChallengePurpose getPurpose() {
        return purpose;
    }

    public boolean isTransferChallenge() {
        return transferChallenge;
    }

    public @Nullable String getTitle() {
        return title;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public @Nullable Integer getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public @Nullable String getScenario() {
        return scenario;
    }

    public @Nullable String getPrompt() {
        return prompt;
    }

    public List<String> getConstraints() {
        return constraints == null ? List.of() : List.copyOf(constraints);
    }

    public List<String> getExpectedConcepts() {
        return expectedConcepts == null ? List.of() : List.copyOf(expectedConcepts);
    }

    public List<ChallengeRubricItem> getRubric() {
        return rubric == null ? List.of() : List.copyOf(rubric);
    }

    public List<String> getCommonMistakes() {
        return commonMistakes == null ? List.of() : List.copyOf(commonMistakes);
    }

    public List<String> getTransferTargets() {
        return transferTargets == null ? List.of() : List.copyOf(transferTargets);
    }

    public @Nullable UUID getAiCallId() {
        return aiCallId;
    }

    public @Nullable String getPromptVersion() {
        return promptVersion;
    }

    public @Nullable String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<UUID> getSkillIds() {
        return Set.copyOf(skillIds);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Challenge challenge && id != null && id.equals(challenge.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /**
     * 본문 값 (seed YAML 또는 생성 결과).
     *
     * @param hints 1~3단계 사전 hint. 키는 {@code HintLevel} 이름
     */
    public record ChallengeContent(
            ChallengePurpose purpose,
            int difficulty,
            boolean transferChallenge,
            Set<UUID> skillIds,
            @Nullable String title,
            @Nullable Integer estimatedMinutes,
            @Nullable String scenario,
            @Nullable String prompt,
            List<String> constraints,
            List<String> expectedConcepts,
            List<ChallengeRubricItem> rubric,
            List<String> commonMistakes,
            List<String> transferTargets,
            Map<String, String> hints) {

        public ChallengeContent {
            skillIds = Set.copyOf(skillIds);
            constraints = List.copyOf(constraints);
            expectedConcepts = List.copyOf(expectedConcepts);
            rubric = List.copyOf(rubric);
            commonMistakes = List.copyOf(commonMistakes);
            transferTargets = List.copyOf(transferTargets);
            hints = Map.copyOf(hints);
        }
    }
}
