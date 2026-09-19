import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/time/time_zone_support.dart';
import 'package:devpilot_app/core/validation/goal_date_rules.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/features/onboarding/data/onboarding_models.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_draft.dart';

/// Onboarding steps, numbered as in the UI (1~5).
enum OnboardingStep {
  goal(1),
  time(2),
  level(3),
  project(4),
  plan(5);

  const OnboardingStep(this.number);

  final int number;

  static const count = 5;
}

/// Client-side rules and request building for SCR-ONBOARDING (docs/02 §3.4, docs/05 §4.1).
abstract final class OnboardingRules {
  static const focusSkillCodesField = 'learningGoal.focusSkillCodes';

  /// "3개월 후" / "6개월 후" chips.
  static LocalDate completionAfterMonths(LocalDate today, int months) => today.addMonths(months);

  /// "완료일 3개월 전" chip value, or null when it would be before today (chip disabled).
  static LocalDate? checkpointThreeMonthsBefore(LocalDate? completion, LocalDate today) {
    if (completion == null) {
      return null;
    }
    final candidate = completion.addMonths(-3);
    return candidate.isBefore(today) ? null : candidate;
  }

  /// The zone step 2 starts with: the draft's choice, else the browser zone, else Asia/Seoul.
  static String timeZone(OnboardingDraft draft, TimeZoneSupport support) =>
      draft.timezone ?? support.deviceTimeZone() ?? defaultTimeZone;

  /// Weekly total = weekday × 5 + weekend × 2 (docs/02 step 2).
  static int weeklyMinutes(OnboardingDraft draft) =>
      draft.weekdayStudyMinutes * 5 + draft.weekendStudyMinutes * 2;

  /// Step 1 "다음" is enabled: profile chosen, display name and goal dates valid (docs/02 step 1).
  static bool canLeaveGoalStep(OnboardingDraft draft, String displayName, LocalDate today) {
    final completion = LocalDate.tryParse(draft.targetCompletionDate);
    final checkpoint = LocalDate.tryParse(draft.checkpointDate);
    final experienceStart = LocalDate.tryParse(draft.experienceStartDate);
    return draft.experienceProfile != null &&
        InputRules.isValidDisplayName(displayName) &&
        GoalDateRules.completionViolation(completion, today) == null &&
        GoalDateRules.checkpointViolation(checkpoint, completion, today) == null &&
        GoalDateRules.experienceStartViolation(experienceStart, today) == null;
  }

  /// Categories whose chosen level is 3 or more get the diagnostic note (docs/02 step 3).
  static bool hasHighSelfAssessment(OnboardingDraft draft) =>
      draft.selfAssessmentLevels.values.any((level) => level >= 3);

  /// Builds `POST /onboarding`. [withProject] false sends `sideProject: null` (skip, SP-1).
  static OnboardingRequest buildRequest({
    required OnboardingDraft draft,
    required String displayName,
    required String timezone,
    required String projectName,
    required String projectDescription,
    required bool withProject,
  }) {
    final experienceProfile = draft.experienceProfile;
    final completion = draft.targetCompletionDate;
    if (experienceProfile == null || completion == null) {
      throw StateError('Step 1 is incomplete');
    }
    return OnboardingRequest(
      displayName: displayName.trim(),
      timezone: timezone,
      dayStartHour: draft.dayStartHour,
      weekdayStudyMinutes: draft.weekdayStudyMinutes,
      weekendStudyMinutes: draft.weekendStudyMinutes,
      experienceProfile: experienceProfile,
      experienceStartDate: draft.experienceStartDate,
      learningGoal: LearningGoalInput(
        targetRole: TargetRole.javaBackend,
        checkpointDate: draft.checkpointDate,
        targetCompletionDate: completion,
        focusSkillCodes: draft.focusSkillCodes,
      ),
      runDiagnostic: draft.runDiagnostic,
      // Diagnostic mode must send []; self-assessment sends all 13 categories (docs/05 §4.1).
      selfAssessments: draft.runDiagnostic
          ? const []
          : [
              for (final category in SkillCategory.known)
                SelfAssessmentInput(
                  category: category,
                  level: draft.selfAssessmentLevels[category] ?? 0,
                ),
            ],
      sideProject: withProject
          ? SideProjectInput(
              name: projectName,
              description: projectDescription,
              repoUrl: null,
              stack: null,
            )
          : null,
      useTemplate: true,
    );
  }

  /// The step that owns the first field error of a `VALIDATION_FAILED` response (docs/02 step
  /// "상태": `sideProject.*` → 4, `selfAssessments` → 3, `learningGoal.*` → 1). Focus skills are
  /// picked on step 3, so `learningGoal.focusSkillCodes` goes there.
  static OnboardingStep stepForFieldErrors(List<ApiFieldError> errors) {
    final fields = errors.map((error) => error.field).toList();
    bool any(bool Function(String field) test) => fields.any(test);
    bool isFocusSkills(String field) => field.startsWith(focusSkillCodesField);
    if (any(
      (field) =>
          (field.startsWith('learningGoal') && !isFocusSkills(field)) ||
          field == 'displayName' ||
          field == 'experienceProfile' ||
          field == 'experienceStartDate',
    )) {
      return OnboardingStep.goal;
    }
    if (any(
      (field) =>
          field == 'timezone' ||
          field == 'dayStartHour' ||
          field == 'weekdayStudyMinutes' ||
          field == 'weekendStudyMinutes',
    )) {
      return OnboardingStep.time;
    }
    if (any(
      (field) =>
          field.startsWith('selfAssessments') || field == 'runDiagnostic' || isFocusSkills(field),
    )) {
      return OnboardingStep.level;
    }
    return OnboardingStep.project;
  }
}
