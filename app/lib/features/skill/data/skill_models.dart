import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'skill_models.freezed.dart';
part 'skill_models.g.dart';

/// `SkillTreeResponse` of `GET /skills/tree` (docs/05 §6.1). [skills] is flat; `parentCode` builds
/// the tree. Server order: category declaration → sortOrder → code.
@freezed
abstract class SkillTreeResponse with _$SkillTreeResponse {
  const factory SkillTreeResponse({
    @JsonKey(unknownEnumValue: TargetRole.unknown) required TargetRole role,
    required int catalogVersion,
    required List<SkillNodeView> skills,
  }) = _SkillTreeResponse;

  factory SkillTreeResponse.fromJson(Map<String, Object?> json) =>
      _$SkillTreeResponseFromJson(json);
}

@freezed
abstract class SkillNodeView with _$SkillNodeView {
  const factory SkillNodeView({
    required String id,
    required String code,
    required String name,
    @JsonKey(unknownEnumValue: SkillCategory.unknown) required SkillCategory category,
    String? parentCode,
    String? description,
    required int minutesPerLevelStep,
    required int sortOrder,
    required List<String> prerequisiteCodes,

    /// Null when the role has no target for this skill.
    RoleTargetView? roleTarget,
  }) = _SkillNodeView;

  factory SkillNodeView.fromJson(Map<String, Object?> json) => _$SkillNodeViewFromJson(json);
}

@freezed
abstract class RoleTargetView with _$RoleTargetView {
  const factory RoleTargetView({
    @JsonKey(unknownEnumValue: Priority.unknown) required Priority priority,
    required int practicalImportanceBp,
    required AxisLevels targets,
  }) = _RoleTargetView;

  factory RoleTargetView.fromJson(Map<String, Object?> json) => _$RoleTargetViewFromJson(json);
}

/// `UserSkillStatesResponse` of `GET /skills/me` (docs/05 §6.2).
@freezed
abstract class UserSkillStatesResponse with _$UserSkillStatesResponse {
  const factory UserSkillStatesResponse({required List<UserSkillStateView> items}) =
      _UserSkillStatesResponse;

  factory UserSkillStatesResponse.fromJson(Map<String, Object?> json) =>
      _$UserSkillStatesResponseFromJson(json);
}

@freezed
abstract class UserSkillStateView with _$UserSkillStateView {
  const factory UserSkillStateView({
    required SkillRef skill,
    required AxisLevels evidenceLevels,
    required AxisLevels planningLevels,

    /// The active plan's target; null when the plan has none for this skill.
    SkillTargetView? target,
    int? selfAssessedLevel,
    required bool selfAssessmentActive,
    required int evidenceCount,
    DateTime? lastPracticedAt,
    DateTime? updatedAt,
  }) = _UserSkillStateView;

  factory UserSkillStateView.fromJson(Map<String, Object?> json) =>
      _$UserSkillStateViewFromJson(json);
}

@freezed
abstract class SkillTargetView with _$SkillTargetView {
  const factory SkillTargetView({
    @JsonKey(unknownEnumValue: Priority.unknown) required Priority priority,
    required int practicalImportanceBp,
    required AxisLevels targets,
    required bool deferred,
    @JsonKey(unknownEnumValue: TargetAdjustment.unknown) required TargetAdjustment adjustment,
  }) = _SkillTargetView;

  factory SkillTargetView.fromJson(Map<String, Object?> json) => _$SkillTargetViewFromJson(json);
}

/// `SkillDetailView` of `GET /skills/{skillId}` (docs/05 §6.4).
///
/// [learningStages]는 **항상 6칸**이고 순서가 곧 화면 순서다 — 클라이언트가 정렬하거나 채워 넣지 않는다.
@freezed
abstract class SkillDetailView with _$SkillDetailView {
  const factory SkillDetailView({
    required SkillRef skill,
    String? parentCode,
    String? description,

    /// 이 기술을 왜 하는지 한 줄. 노트가 없으면 null이라 줄 자체를 숨긴다.
    String? whyItMatters,
    required int minutesPerLevelStep,
    @Default(<String>[]) List<String> prerequisiteCodes,
    required AxisLevels evidenceLevels,
    required AxisLevels planningLevels,
    SkillTargetView? target,
    @Default(<LearningStageView>[]) List<LearningStageView> learningStages,
  }) = _SkillDetailView;

  factory SkillDetailView.fromJson(Map<String, Object?> json) => _$SkillDetailViewFromJson(json);
}

/// 학습 단계 한 칸 (docs/06 §5.11). 저장하지 않는 파생 값이라 화면에서 체크하거나 되돌릴 수 없다.
@freezed
abstract class LearningStageView with _$LearningStageView {
  const factory LearningStageView({
    @JsonKey(unknownEnumValue: LearningStage.unknown) required LearningStage stage,
    required bool completed,

    /// 그 칸을 채운 가장 이른 기록의 시각. 미완료면 null이다.
    DateTime? completedAt,
  }) = _LearningStageView;

  factory LearningStageView.fromJson(Map<String, Object?> json) =>
      _$LearningStageViewFromJson(json);
}
