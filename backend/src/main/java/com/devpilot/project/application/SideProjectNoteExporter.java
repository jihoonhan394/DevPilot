package com.devpilot.project.application;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.logging.AuditEvent;
import com.devpilot.common.logging.AuditLogger;
import com.devpilot.common.logging.UserRefCalculator;
import com.devpilot.common.security.CurrentUser;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.project.domain.SideProject;
import com.devpilot.project.domain.SideProjectNoteType;
import com.devpilot.project.infrastructure.SideProjectRepository;
import com.devpilot.skill.application.SkillRef;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기록 → Markdown (docs/05 §19.13, BL-PRJ-04). AI를 호출하지 않는다.
 *
 * <p>본문은 저장된 <b>마스킹본</b> 그대로다 — 내보내기에서 추가 가공을 하지 않는다. 여기서 다시 손대면 화면에서 본 것과 파일이 달라진다.
 */
@Service
public class SideProjectNoteExporter {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SideProjectRepository sideProjectRepository;
    private final SideProjectNoteQueryService noteQueryService;
    private final AuditLogger auditLogger;
    private final UserRefCalculator userRefCalculator;
    private final Clock clock;

    public SideProjectNoteExporter(
            SideProjectRepository sideProjectRepository,
            SideProjectNoteQueryService noteQueryService,
            AuditLogger auditLogger,
            UserRefCalculator userRefCalculator,
            Clock clock) {
        this.sideProjectRepository = sideProjectRepository;
        this.noteQueryService = noteQueryService;
        this.auditLogger = auditLogger;
        this.userRefCalculator = userRefCalculator;
        this.clock = clock;
    }

    /** 기록이 없어도 제목만 있는 문서를 돌려준다 — 빈 응답이 아니라 "아직 없다"는 문서다. */
    @Transactional(readOnly = true)
    public Export export(CurrentUser user, UUID sideProjectId) {
        UUID userId = user.userId();
        SideProject project =
                sideProjectRepository
                        .findByIdAndUserId(sideProjectId, userId)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                ErrorCode.RESOURCE_NOT_FOUND,
                                                "side project not found"));
        LocalDate today =
                PlanDayCalculator.planDate(clock.instant(), user.zoneId(), user.dayStartHour());
        List<SideProjectNoteView> notes = noteQueryService.allForExport(userId, sideProjectId);
        String markdown = render(project.getName(), today, notes);
        auditLogger.log(
                AuditEvent.DATA_EXPORTED,
                Map.of(
                        "userRef",
                        userRefCalculator.userRef(userId),
                        "format",
                        "markdown",
                        "bytes",
                        markdown.getBytes(StandardCharsets.UTF_8).length));
        return new Export(
                "notes-" + sideProjectId + "-" + today.format(FILE_DATE) + ".md", markdown);
    }

    private static String render(
            String projectName, LocalDate today, List<SideProjectNoteView> notes) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(projectName).append(" — 결정·장애 기록\n\n");
        out.append("내보낸 날짜: ").append(today).append(" · 기록 ").append(notes.size()).append("건\n");
        for (SideProjectNoteView note : notes) {
            out.append("\n## ")
                    .append(note.occurredOn())
                    .append(' ')
                    .append(note.title())
                    .append("\n\n");
            out.append("- 유형: ")
                    .append(note.noteType() == SideProjectNoteType.DECISION ? "결정 기록" : "장애 기록")
                    .append('\n');
            if (note.noteType() == SideProjectNoteType.DECISION) {
                line(out, "고른 것", note.decisionChoice());
                line(out, "선택지", note.decisionOptions());
                line(out, "이유", note.decisionRationale());
            } else {
                line(out, "증상", note.incidentSymptom());
                line(out, "발견", note.incidentDetection());
                line(out, "조치", note.incidentFix());
                line(out, "재발 방지", note.incidentPrevention());
            }
            SkillRef skill = note.skill();
            if (skill != null) {
                line(out, "기술", skill.code());
            }
        }
        return out.toString();
    }

    private static void line(StringBuilder out, String label, @Nullable String value) {
        if (value != null && !value.isBlank()) {
            out.append("- ").append(label).append(": ").append(value).append('\n');
        }
    }

    /**
     * 내려받을 파일 하나.
     *
     * @param fileName {@code Content-Disposition}에 그대로 들어간다 (docs/05 §19.13)
     */
    public record Export(String fileName, String markdown) {}
}
