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
  }) = _ReplanPreviewResponse;

  factory ReplanPreviewResponse.fromJson(Map<String, Object?> json) =>
      _$ReplanPreviewResponseFromJson(json);
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
