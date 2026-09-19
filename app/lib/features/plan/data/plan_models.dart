import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'plan_models.freezed.dart';
part 'plan_models.g.dart';

// Plan module view records (docs/05 §7.1). Dates are plan dates (`yyyy-MM-dd`).

@freezed
abstract class PlanView with _$PlanView {
  const factory PlanView({
    required String id,
    required int planVersion,
    @JsonKey(unknownEnumValue: PlanStatus.unknown) required PlanStatus status,
    required String title,
    String? supersedesPlanId,
    String? changeReason,
    required bool replanRecommended,

    /// Server order: sortOrder → startDate → id.
    required List<MilestoneView> milestones,
    required List<PlanSkillTargetView> skillTargets,
    SnapshotView? latestSnapshot,
    required DateTime createdAt,
    DateTime? supersededAt,
    required int version,
  }) = _PlanView;

  factory PlanView.fromJson(Map<String, Object?> json) => _$PlanViewFromJson(json);
}

@freezed
abstract class MilestoneView with _$MilestoneView {
  const factory MilestoneView({
    required String id,
    required String title,
    String? description,
    required String startDate,
    required String endDate,
    @JsonKey(unknownEnumValue: Priority.unknown) required Priority priority,
    @JsonKey(unknownEnumValue: MilestoneStatus.unknown) required MilestoneStatus status,
    required int sortOrder,
    required List<String> skillCodes,
    required DateTime updatedAt,
    required int version,
  }) = _MilestoneView;

  factory MilestoneView.fromJson(Map<String, Object?> json) => _$MilestoneViewFromJson(json);
}

@freezed
abstract class PlanSkillTargetView with _$PlanSkillTargetView {
  const factory PlanSkillTargetView({
    required SkillRef skill,
    @JsonKey(unknownEnumValue: Priority.unknown) required Priority priority,
    required int practicalImportanceBp,
    required AxisLevels targets,
    required bool deferred,
    @JsonKey(unknownEnumValue: TargetAdjustment.unknown) required TargetAdjustment adjustment,
  }) = _PlanSkillTargetView;

  factory PlanSkillTargetView.fromJson(Map<String, Object?> json) =>
      _$PlanSkillTargetViewFromJson(json);
}

@freezed
abstract class SnapshotView with _$SnapshotView {
  const factory SnapshotView({
    required String snapshotDate,
    required String horizonDate,
    required int nominalBudgetMinutes,
    required int completionRateBp,
    required int effectiveBudgetMinutes,
    required int requiredMustMinutes,
    required int requiredShouldMinutes,
    int? ratioBp,
    @JsonKey(unknownEnumValue: RiskLevel.unknown) required RiskLevel riskLevel,
    required DateTime generatedAt,
  }) = _SnapshotView;

  factory SnapshotView.fromJson(Map<String, Object?> json) => _$SnapshotViewFromJson(json);
}

@freezed
abstract class PlanSummaryView with _$PlanSummaryView {
  const factory PlanSummaryView({
    required String id,
    required int planVersion,
    @JsonKey(unknownEnumValue: PlanStatus.unknown) required PlanStatus status,
    required String title,
    String? changeReason,
    required int milestoneCount,
    @JsonKey(unknownEnumValue: RiskLevel.unknown) RiskLevel? latestRiskLevel,
    int? latestRatioBp,
    required DateTime createdAt,
    DateTime? supersededAt,
  }) = _PlanSummaryView;

  factory PlanSummaryView.fromJson(Map<String, Object?> json) => _$PlanSummaryViewFromJson(json);
}

/// `MilestonePatchRequest` (docs/05 §7.6). Null fields are left out (unchanged).
/// An empty [description] clears the memo.
@freezed
abstract class MilestonePatchRequest with _$MilestonePatchRequest {
  const factory MilestonePatchRequest({
    @JsonKey(includeIfNull: false) MilestoneStatus? status,
    @JsonKey(includeIfNull: false) String? description,
    @JsonKey(includeIfNull: false) int? sortOrder,
    required int version,
  }) = _MilestonePatchRequest;

  factory MilestonePatchRequest.fromJson(Map<String, Object?> json) =>
      _$MilestonePatchRequestFromJson(json);
}

/// `ReplanRequest` (docs/05 §7.8). S1 sends the four suggestion lists empty.
@freezed
abstract class ReplanRequest with _$ReplanRequest {
  const factory ReplanRequest({
    required String reason,
    required int version,
    required List<MilestoneInput> milestones,
    @Default(<String>[]) List<String> acceptedDeferrals,
    @Default(<TargetChangeInput>[]) List<TargetChangeInput> acceptedTargetReductions,
    @Default(<String>[]) List<String> restoredDeferrals,
    @Default(<TargetChangeInput>[]) List<TargetChangeInput> acceptedTargetRaises,
  }) = _ReplanRequest;

  factory ReplanRequest.fromJson(Map<String, Object?> json) => _$ReplanRequestFromJson(json);
}

/// `MilestoneInput`: [id] is null for a new milestone.
@freezed
abstract class MilestoneInput with _$MilestoneInput {
  const factory MilestoneInput({
    required String? id,
    required String title,
    required String? description,
    required String startDate,
    required String endDate,
    required Priority priority,
    required MilestoneStatus status,
    required int sortOrder,
    required List<String> skillCodes,
  }) = _MilestoneInput;

  factory MilestoneInput.fromJson(Map<String, Object?> json) => _$MilestoneInputFromJson(json);
}

/// `TargetReductionInput` / `TargetRaiseInput` share this shape.
@freezed
abstract class TargetChangeInput with _$TargetChangeInput {
  const factory TargetChangeInput({
    required String skillCode,
    required SkillAxis axis,
    required int newTarget,
  }) = _TargetChangeInput;

  factory TargetChangeInput.fromJson(Map<String, Object?> json) =>
      _$TargetChangeInputFromJson(json);
}

@freezed
abstract class ReplanCommitResponse with _$ReplanCommitResponse {
  const factory ReplanCommitResponse({
    required PlanView plan,

    /// Only milestones that had an id in the request, in request order.
    required List<MilestoneIdMappingView> milestoneIdMapping,
  }) = _ReplanCommitResponse;

  factory ReplanCommitResponse.fromJson(Map<String, Object?> json) =>
      _$ReplanCommitResponseFromJson(json);
}

@freezed
abstract class MilestoneIdMappingView with _$MilestoneIdMappingView {
  const factory MilestoneIdMappingView({required String previousId, required String newId}) =
      _MilestoneIdMappingView;

  factory MilestoneIdMappingView.fromJson(Map<String, Object?> json) =>
      _$MilestoneIdMappingViewFromJson(json);
}
