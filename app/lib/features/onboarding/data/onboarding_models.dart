import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/features/plan/data/learning_goal_models.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/features/project/data/side_project_models.dart';
import 'package:devpilot_app/features/settings/data/me_response.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'onboarding_models.freezed.dart';
part 'onboarding_models.g.dart';

/// `OnboardingRequest` of `POST /onboarding` (docs/05 §4.1).
@freezed
abstract class OnboardingRequest with _$OnboardingRequest {
  const factory OnboardingRequest({
    required String displayName,
    required String timezone,
    required int dayStartHour,
    required int weekdayStudyMinutes,
    required int weekendStudyMinutes,
    required LearningGoalInput learningGoal,

    /// true = short diagnostic (then [selfAssessments] is empty), false = self-assessment.
    required bool runDiagnostic,
    required List<SelfAssessmentInput> selfAssessments,

    /// null = skipped (SP-1).
    required SideProjectInput? sideProject,
    required bool useTemplate,
  }) = _OnboardingRequest;

  factory OnboardingRequest.fromJson(Map<String, Object?> json) =>
      _$OnboardingRequestFromJson(json);
}

/// The learning track and its target date (목표일).
@freezed
abstract class LearningGoalInput with _$LearningGoalInput {
  const factory LearningGoalInput({
    required TargetRole targetRole,
    required String targetCompletionDate,
    required List<String> focusSkillCodes,
  }) = _LearningGoalInput;

  factory LearningGoalInput.fromJson(Map<String, Object?> json) =>
      _$LearningGoalInputFromJson(json);
}

@freezed
abstract class SelfAssessmentInput with _$SelfAssessmentInput {
  const factory SelfAssessmentInput({required SkillCategory category, required int level}) =
      _SelfAssessmentInput;

  factory SelfAssessmentInput.fromJson(Map<String, Object?> json) =>
      _$SelfAssessmentInputFromJson(json);
}

/// Onboarding sends only [name] and [description]; `repoUrl` and `stack` stay null (docs/02
/// SCR-ONBOARDING step 4).
@freezed
abstract class SideProjectInput with _$SideProjectInput {
  const factory SideProjectInput({
    required String name,
    required String? description,
    required String? repoUrl,
    required String? stack,
  }) = _SideProjectInput;

  factory SideProjectInput.fromJson(Map<String, Object?> json) => _$SideProjectInputFromJson(json);
}

/// `OnboardingResponse` (201).
@freezed
abstract class OnboardingResponse with _$OnboardingResponse {
  const factory OnboardingResponse({
    required MeResponse user,
    required LearningGoalView learningGoal,
    required PlanSummaryView activePlan,
    SideProjectView? sideProject,
    required int assignedSeedCardCount,
    required List<DiagnosticSuggestionView> suggestedDiagnostics,
  }) = _OnboardingResponse;

  factory OnboardingResponse.fromJson(Map<String, Object?> json) =>
      _$OnboardingResponseFromJson(json);
}

/// `DiagnosticSuggestionView` (docs/05 §4.2). Always empty before S3.
@freezed
abstract class DiagnosticSuggestionView with _$DiagnosticSuggestionView {
  const factory DiagnosticSuggestionView({
    @JsonKey(unknownEnumValue: SkillCategory.unknown) required SkillCategory category,
    int? selfAssessedLevel,
    required SkillRef skill,
    required String challengeId,
    required String title,
    required int difficulty,
    int? estimatedMinutes,
  }) = _DiagnosticSuggestionView;

  factory DiagnosticSuggestionView.fromJson(Map<String, Object?> json) =>
      _$DiagnosticSuggestionViewFromJson(json);
}
