import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'plan_budget_models.freezed.dart';
part 'plan_budget_models.g.dart';

// Deadline budget, risk and replan suggestions (docs/05 §7.1 BudgetView, §7.7). The client never
// computes risk or thresholds; it shows these values (docs/02 SCR-REPLAN "제안 영역 규칙").

/// `GET /plans/active/budget`.
@freezed
abstract class BudgetView with _$BudgetView {
  const factory BudgetView({
    required String planId,
    required String today,

    /// The target date (목표일) the budget counts up to (docs/06 §3.1).
    required String horizonDate,
    required int nominalBudgetMinutes,
    required int completionRateBp,
    required int effectiveBudgetMinutes,
    required int requiredMustMinutes,
    required int requiredShouldMinutes,

    /// Null when the effective budget is 0.
    int? ratioBp,
    @JsonKey(unknownEnumValue: RiskLevel.unknown) required RiskLevel riskLevel,

    /// Minutes charged to axes there is no way to earn yet (ADR-061, ADR-062). Not in the risk.
    /// Defaults to 0 so an older server does not break the whole budget card.
    @Default(0) int requiredMustLaterMinutes,

    /// When the current scope could be finished (docs/06 §3.4). Null when no date carries it —
    /// then the answer is to shrink the scope, not to move the date.
    String? feasibleCompletionDate,

    /// True when the completion rate is the default guess, not measured (docs/06 §3.3). The screen
    /// never paints a warning colour from a guess. Defaults to false for an older server.
    @Default(false) bool completionRateEstimated,
  }) = _BudgetView;

  factory BudgetView.fromJson(Map<String, Object?> json) => _$BudgetViewFromJson(json);
}

/// `POST /plans/{planId}/replan/preview`. Shrink lists and the expansion list are never both
/// non-empty (docs/06 §4.4).
@freezed
abstract class ReplanPreviewResponse with _$ReplanPreviewResponse {
  const factory ReplanPreviewResponse({
    required String planId,
    required String today,
    required String horizonDate,
    required int nominalBudgetMinutes,
    required int completionRateBp,
    required int effectiveBudgetMinutes,
    required int requiredMustMinutes,
    required int requiredShouldMinutes,
    int? ratioBp,
    @JsonKey(unknownEnumValue: RiskLevel.unknown) required RiskLevel riskLevel,
    required List<DeferSuggestionView> deferSuggestions,
    required List<TargetReductionSuggestionView> mustTargetReductionSuggestions,
    required List<ExpansionSuggestionView> expansionSuggestions,
    required RiskEstimateView riskAfterSuggestions,

    /// Dates proposed for the goal's target date (docs/05 §7.7, ADR-067). Empty when nothing
    /// would move, or when no date can carry the remaining steps. Defaults to empty so an older
    /// server does not break the preview.
    @Default(<MilestoneScheduleView>[]) List<MilestoneScheduleView> milestoneSchedule,
  }) = _ReplanPreviewResponse;

  factory ReplanPreviewResponse.fromJson(Map<String, Object?> json) =>
      _$ReplanPreviewResponseFromJson(json);
}

/// One milestone's proposed dates (docs/05 §7.7, ADR-067).
@freezed
abstract class MilestoneScheduleView with _$MilestoneScheduleView {
  const factory MilestoneScheduleView({
    /// Null for a milestone the reader added in the form and has not saved yet.
    String? id,
    required int sortOrder,
    required String title,
    required String startDate,
    required String endDate,

    /// Whether this differs from the date the plan holds now — the screen highlights only these.
    required bool changed,
  }) = _MilestoneScheduleView;

  factory MilestoneScheduleView.fromJson(Map<String, Object?> json) =>
      _$MilestoneScheduleViewFromJson(json);
}

@freezed
abstract class DeferSuggestionView with _$DeferSuggestionView {
  const factory DeferSuggestionView({
    required SkillRef skill,
    @JsonKey(unknownEnumValue: Priority.unknown) required Priority priority,
    required int practicalImportanceBp,

    /// Minutes of `requiredShould` this deferral removes.
    required int requiredMinutes,
  }) = _DeferSuggestionView;

  factory DeferSuggestionView.fromJson(Map<String, Object?> json) =>
      _$DeferSuggestionViewFromJson(json);
}

@freezed
abstract class TargetReductionSuggestionView with _$TargetReductionSuggestionView {
  const factory TargetReductionSuggestionView({
    required SkillRef skill,
    @JsonKey(unknownEnumValue: SkillAxis.unknown) required SkillAxis axis,
    required int currentTarget,
    required int newTarget,
    required int planningLevel,
    required int savedMinutes,
  }) = _TargetReductionSuggestionView;

  factory TargetReductionSuggestionView.fromJson(Map<String, Object?> json) =>
      _$TargetReductionSuggestionViewFromJson(json);
}

@freezed
abstract class ExpansionSuggestionView with _$ExpansionSuggestionView {
  const factory ExpansionSuggestionView({
    @JsonKey(unknownEnumValue: ExpansionKind.unknown) required ExpansionKind kind,
    required SkillRef skill,
    @JsonKey(unknownEnumValue: Priority.unknown) required Priority priority,
    required int practicalImportanceBp,

    /// `RAISE_TARGET` only.
    @JsonKey(unknownEnumValue: SkillAxis.unknown) SkillAxis? axis,
    int? currentTarget,
    int? newTarget,
    required int addedMinutes,
  }) = _ExpansionSuggestionView;

  factory ExpansionSuggestionView.fromJson(Map<String, Object?> json) =>
      _$ExpansionSuggestionViewFromJson(json);
}

@freezed
abstract class RiskEstimateView with _$RiskEstimateView {
  const factory RiskEstimateView({
    required int requiredMustMinutes,
    required int requiredShouldMinutes,
    int? ratioBp,
    @JsonKey(unknownEnumValue: RiskLevel.unknown) required RiskLevel riskLevel,
  }) = _RiskEstimateView;

  factory RiskEstimateView.fromJson(Map<String, Object?> json) => _$RiskEstimateViewFromJson(json);
}
