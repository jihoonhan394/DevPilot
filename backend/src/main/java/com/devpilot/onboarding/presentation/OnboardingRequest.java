package com.devpilot.onboarding.presentation;

import com.devpilot.onboarding.application.OnboardingCommand;
import com.devpilot.user.domain.ExperienceProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@code POST /onboarding} 요청 (docs/05 §4.1). 3단계는 진단({@code runDiagnostic = true}, {@code
 * selfAssessments = []}) 또는 자기평가({@code false}, 1개 이상), 4단계 {@code sideProject = null}은 건너뛰기다.
 */
public record OnboardingRequest(
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(max = 50) String timezone,
        @NotNull @Min(0) @Max(6) Integer dayStartHour,
        @NotNull @Min(0) @Max(720) Integer weekdayStudyMinutes,
        @NotNull @Min(0) @Max(720) Integer weekendStudyMinutes,
        @NotNull ExperienceProfile experienceProfile,
        @Nullable LocalDate experienceStartDate,
        @NotNull @Valid LearningGoalInput learningGoal,
        @NotNull Boolean runDiagnostic,
        @NotNull @Size(max = 13) List<@NotNull @Valid SelfAssessmentInput> selfAssessments,
        @Nullable @Valid SideProjectInput sideProject,
        @NotNull Boolean useTemplate) {

    OnboardingCommand toCommand() {
        return new OnboardingCommand(
                displayName,
                timezone,
                dayStartHour,
                weekdayStudyMinutes,
                weekendStudyMinutes,
                experienceProfile,
                experienceStartDate,
                learningGoal.toCommand(),
                runDiagnostic,
                selfAssessments.stream()
                        .map(
                                input ->
                                        new OnboardingCommand.SelfAssessment(
                                                input.category(), input.level()))
                        .toList(),
                sideProject == null ? null : sideProject.toCommand(),
                useTemplate);
    }
}
