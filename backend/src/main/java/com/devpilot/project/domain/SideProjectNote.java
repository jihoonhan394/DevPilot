package com.devpilot.project.domain;

import com.devpilot.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 프로젝트 기록 (docs/04 §2 {@code side_project_note}, docs/05 §19.8).
 *
 * <p>무엇을 <b>왜</b> 골랐는지(결정)와 무엇이 어떻게 깨졌고 어떻게 고쳤는지(장애)를 그때그때 남긴다. 나중에 돌아보면 "그때 왜 그렇게 했더라"가 남아 있는 자리다
 * — 코드는 결과만 보여 주고 이유는 지워진다.
 *
 * <p>모든 텍스트는 <b>마스킹본</b>이다(PN-4, docs/05 §1.11). 학습 이벤트를 만들지 않고 skill 레벨을 바꾸지 않는다(PN-3) — 기록은 증거가
 * 아니라 기억이다.
 *
 * <p>{@code noteType}은 생성 후 바뀌지 않는다(PN-2). 유형을 바꾸려면 지우고 다시 만든다 — 결정 기록의 세 항목과 장애 기록의 네 항목은 서로 옮겨 담을
 * 수 있는 것이 아니다.
 */
@Entity
@Table(name = "side_project_note")
public class SideProjectNote extends BaseTimeEntity {

    @Id private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "side_project_id", nullable = false, updatable = false)
    private UUID sideProjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, updatable = false)
    private SideProjectNoteType noteType;

    @Column(nullable = false)
    private String title;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    /** 선택. skill이 비활성화돼도 기록은 남는다. */
    @Column(name = "skill_id")
    private @Nullable UUID skillId;

    @Column(name = "decision_choice")
    private @Nullable String decisionChoice;

    @Column(name = "decision_options")
    private @Nullable String decisionOptions;

    @Column(name = "decision_rationale")
    private @Nullable String decisionRationale;

    @Column(name = "incident_symptom")
    private @Nullable String incidentSymptom;

    @Column(name = "incident_detection")
    private @Nullable String incidentDetection;

    @Column(name = "incident_fix")
    private @Nullable String incidentFix;

    @Column(name = "incident_prevention")
    private @Nullable String incidentPrevention;

    @Version private @Nullable Long version;

    protected SideProjectNote() {
        // JPA 전용
    }

    private SideProjectNote(UUID userId, UUID sideProjectId, NoteValues values) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.sideProjectId = Objects.requireNonNull(sideProjectId, "sideProjectId");
        this.noteType = Objects.requireNonNull(values.noteType(), "noteType");
        this.title = Objects.requireNonNull(values.title(), "title");
        this.occurredOn = Objects.requireNonNull(values.occurredOn(), "occurredOn");
        this.skillId = values.skillId();
        this.decisionChoice = values.decisionChoice();
        this.decisionOptions = values.decisionOptions();
        this.decisionRationale = values.decisionRationale();
        this.incidentSymptom = values.incidentSymptom();
        this.incidentDetection = values.incidentDetection();
        this.incidentFix = values.incidentFix();
        this.incidentPrevention = values.incidentPrevention();
    }

    /** 등록 (docs/05 §19.9). 유형별 필수·금지 검사와 마스킹은 호출자가 끝냈다. */
    public static SideProjectNote create(UUID userId, UUID sideProjectId, NoteValues values) {
        return new SideProjectNote(userId, sideProjectId, values);
    }

    /**
     * PATCH (docs/05 §19.11). {@code null}은 변경하지 않는다. 실제로 바뀐 값이 있으면 {@code true} — 없으면 {@code
     * updated_at}·{@code version}을 그대로 둔다.
     *
     * <p>{@code noteType}은 인자에 없다(PN-2).
     */
    public boolean update(NotePatch patch) {
        boolean changed = false;
        String newTitle = patch.title();
        if (newTitle != null && !newTitle.equals(title)) {
            title = newTitle;
            changed = true;
        }
        LocalDate newOccurredOn = patch.occurredOn();
        if (newOccurredOn != null && !newOccurredOn.equals(occurredOn)) {
            occurredOn = newOccurredOn;
            changed = true;
        }
        SkillLink link = patch.skillId();
        if (link.applies() && !Objects.equals(link.value(), skillId)) {
            skillId = link.value();
            changed = true;
        }
        return updateDecision(patch) | updateIncident(patch) | changed;
    }

    /** 유형에 맞는 항목만 바뀐다 — 유형 검사는 호출자가 끝냈다. */
    private boolean updateDecision(NotePatch patch) {
        boolean changed = false;
        String choice = patch.decisionChoice();
        if (choice != null && !choice.equals(decisionChoice)) {
            decisionChoice = choice;
            changed = true;
        }
        String options = patch.decisionOptions();
        if (options != null && !options.equals(decisionOptions)) {
            decisionOptions = options;
            changed = true;
        }
        String rationale = patch.decisionRationale();
        if (rationale != null && !rationale.equals(decisionRationale)) {
            decisionRationale = rationale;
            changed = true;
        }
        return changed;
    }

    private boolean updateIncident(NotePatch patch) {
        boolean changed = false;
        String symptom = patch.incidentSymptom();
        if (symptom != null && !symptom.equals(incidentSymptom)) {
            incidentSymptom = symptom;
            changed = true;
        }
        String detection = patch.incidentDetection();
        if (detection != null && !detection.equals(incidentDetection)) {
            incidentDetection = detection;
            changed = true;
        }
        String fix = patch.incidentFix();
        if (fix != null && !fix.equals(incidentFix)) {
            incidentFix = fix;
            changed = true;
        }
        String prevention = patch.incidentPrevention();
        if (prevention != null && !prevention.equals(incidentPrevention)) {
            incidentPrevention = prevention;
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

    public UUID getSideProjectId() {
        return sideProjectId;
    }

    public SideProjectNoteType getNoteType() {
        return noteType;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public @Nullable UUID getSkillId() {
        return skillId;
    }

    public @Nullable String getDecisionChoice() {
        return decisionChoice;
    }

    public @Nullable String getDecisionOptions() {
        return decisionOptions;
    }

    public @Nullable String getDecisionRationale() {
        return decisionRationale;
    }

    public @Nullable String getIncidentSymptom() {
        return incidentSymptom;
    }

    public @Nullable String getIncidentDetection() {
        return incidentDetection;
    }

    public @Nullable String getIncidentFix() {
        return incidentFix;
    }

    public @Nullable String getIncidentPrevention() {
        return incidentPrevention;
    }

    public long getVersion() {
        return version == null ? 0L : version;
    }

    /** 등록 값 (docs/05 §19.9). 모든 텍스트는 이미 마스킹을 거쳤다. */
    public record NoteValues(
            SideProjectNoteType noteType,
            String title,
            LocalDate occurredOn,
            @Nullable UUID skillId,
            @Nullable String decisionChoice,
            @Nullable String decisionOptions,
            @Nullable String decisionRationale,
            @Nullable String incidentSymptom,
            @Nullable String incidentDetection,
            @Nullable String incidentFix,
            @Nullable String incidentPrevention) {}

    /**
     * PATCH 값 (docs/05 §19.11). {@code null}은 "변경하지 않음"이다.
     *
     * @param skillId 빈 문자열로 온 {@code skillCode}는 {@link SkillLink#clear()}가 된다
     */
    public record NotePatch(
            @Nullable String title,
            @Nullable LocalDate occurredOn,
            SkillLink skillId,
            @Nullable String decisionChoice,
            @Nullable String decisionOptions,
            @Nullable String decisionRationale,
            @Nullable String incidentSymptom,
            @Nullable String incidentDetection,
            @Nullable String incidentFix,
            @Nullable String incidentPrevention) {}

    /**
     * skill 연결의 세 상태 (docs/05 §19.11). {@code null}(그대로 둔다)과 빈 문자열(연결을 끊는다)은 다른 뜻이라 boolean 하나로는
     * 표현되지 않는다.
     */
    public record SkillLink(boolean applies, @Nullable UUID value) {

        public static SkillLink keep() {
            return new SkillLink(false, null);
        }

        public static SkillLink clear() {
            return new SkillLink(true, null);
        }

        public static SkillLink to(UUID value) {
            return new SkillLink(true, Objects.requireNonNull(value, "value"));
        }
    }
}
