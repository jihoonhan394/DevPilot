import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'plan_buildable_models.freezed.dart';
part 'plan_buildable_models.g.dart';

// What the plan's build order says can be built now (docs/05 §7.10, docs/06 §11.4, ADR-060).
// Readiness counts evidence levels only — a self-assessment never opens a step.

/// `GET /plans/active/buildable`.
@freezed
abstract class BuildableView with _$BuildableView {
  const factory BuildableView({
    required String planId,
    required int planVersion,
    required String today,
    required int buildableStepCount,
    required int stepCount,

    /// The step to build now. Null once every step is buildable.
    String? nextStepId,

    /// Axes that can be measured today (docs/06 §7.6).
    required List<SkillAxis> countedAxes,

    /// Axes left out because there is no way to record them yet. Can be empty.
    required List<SkillAxis> uncountedAxes,

    /// Build order (`sortOrder` ASC).
    required List<BuildableStepView> steps,
  }) = _BuildableView;

  factory BuildableView.fromJson(Map<String, Object?> json) => _$BuildableViewFromJson(json);
}

/// One step of the build order = one plan milestone.
@freezed
abstract class BuildableStepView with _$BuildableStepView {
  const factory BuildableStepView({
    required String milestoneId,
    required String title,

    /// What to build in this step, as the plan template wrote it.
    String? description,
    required int sortOrder,
    required String startDate,
    required String endDate,
    @JsonKey(unknownEnumValue: BuildableStatus.unknown) required BuildableStatus status,
    required int metSkillCount,
    required int gateSkillCount,

    /// Widest gap first, at most 5.
    required List<BuildableGapView> gaps,
  }) = _BuildableStepView;

  factory BuildableStepView.fromJson(Map<String, Object?> json) =>
      _$BuildableStepViewFromJson(json);
}

/// A gate skill that has not reached its target yet.
@freezed
abstract class BuildableGapView with _$BuildableGapView {
  const factory BuildableGapView({
    required SkillRef skill,
    required AxisLevels evidenceLevels,
    required AxisLevels targets,
  }) = _BuildableGapView;

  factory BuildableGapView.fromJson(Map<String, Object?> json) => _$BuildableGapViewFromJson(json);
}
