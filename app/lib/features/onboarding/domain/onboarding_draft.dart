import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'onboarding_draft.freezed.dart';
part 'onboarding_draft.g.dart';

/// Inputs of onboarding steps 1~4, kept until `POST /onboarding` succeeds (docs/02 SCR-ONBOARDING
/// "입력 보관"). Serialized to `localStorage` so a reload does not lose them.
///
/// Null means "not chosen yet"; the screens show the documented defaults for those fields.
@freezed
abstract class OnboardingDraft with _$OnboardingDraft {
  const factory OnboardingDraft({
    /// Null until edited; the field starts with `GET /me.displayName`.
    String? displayName,

    /// The target date (목표일); null until chosen.
    String? targetCompletionDate,
    @Default(OnboardingDefaults.weekdayStudyMinutes) int weekdayStudyMinutes,
    @Default(OnboardingDefaults.weekendStudyMinutes) int weekendStudyMinutes,
    @Default(OnboardingDefaults.dayStartHour) int dayStartHour,

    /// Null until chosen; the screen starts with the browser zone.
    String? timezone,

    /// Step 3 default is the short diagnostic (docs/02 step 3).
    @Default(true) bool runDiagnostic,

    /// Self-assessment level (0~4) per category; missing categories count as 0.
    @Default(<SkillCategory, int>{}) Map<SkillCategory, int> selfAssessmentLevels,
    @Default(<String>[]) List<String> focusSkillCodes,

    /// Null until edited; the field starts with "주문 시스템" filled in by the client.
    String? projectName,
  }) = _OnboardingDraft;

  factory OnboardingDraft.fromJson(Map<String, Object?> json) => _$OnboardingDraftFromJson(json);
}

/// Documented defaults and limits of the onboarding inputs (docs/02 SCR-ONBOARDING, §3.2).
abstract final class OnboardingDefaults {
  static const weekdayStudyMinutes = 45;
  static const weekendStudyMinutes = 240;
  static const dayStartHour = 4;

  static const weekdayMinuteChoices = [0, 15, 30, 45, 60, 90, 120];
  static const weekendMinuteChoices = [0, 30, 60, 120, 180, 240];
  static const dayStartHours = [0, 1, 2, 3, 4, 5, 6];

  /// Self-assessment chips go up to 4 (`PRACTICAL`) (docs/02 §3.2).
  static const maxSelfAssessmentLevel = 4;
  static const maxFocusSkills = 10;
}
