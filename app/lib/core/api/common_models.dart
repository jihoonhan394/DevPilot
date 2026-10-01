import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'common_models.freezed.dart';
part 'common_models.g.dart';

/// `SkillRef` (docs/05 §2.2).
@freezed
abstract class SkillRef with _$SkillRef {
  const factory SkillRef({
    required String id,
    required String code,
    required String name,
    @JsonKey(unknownEnumValue: SkillCategory.unknown) required SkillCategory category,
  }) = _SkillRef;

  factory SkillRef.fromJson(Map<String, Object?> json) => _$SkillRefFromJson(json);
}

/// `AxisLevels`: one 0~5 level per `SkillAxis` (docs/05 §2.2).
@freezed
abstract class AxisLevels with _$AxisLevels {
  const factory AxisLevels({
    required int knowledge,
    required int implementation,
    required int explanation,
    required int debugging,
  }) = _AxisLevels;

  const AxisLevels._();

  factory AxisLevels.fromJson(Map<String, Object?> json) => _$AxisLevelsFromJson(json);

  static const zero = AxisLevels(knowledge: 0, implementation: 0, explanation: 0, debugging: 0);

  /// Level of [axis]; 0 for [SkillAxis.unknown].
  int of(SkillAxis axis) => switch (axis) {
    SkillAxis.knowledge => knowledge,
    SkillAxis.implementation => implementation,
    SkillAxis.explanation => explanation,
    SkillAxis.debugging => debugging,
    SkillAxis.unknown => 0,
  };
}

/// `AiMeta` (docs/05 §2.4): which model and prompt made an AI result. Null when none was used.
@freezed
abstract class AiMeta with _$AiMeta {
  const factory AiMeta({
    required String model,
    required String promptVersion,
    @Default(<GuardActionView>[]) List<GuardActionView> guardActions,
  }) = _AiMeta;

  factory AiMeta.fromJson(Map<String, Object?> json) => _$AiMetaFromJson(json);
}

@freezed
abstract class GuardActionView with _$GuardActionView {
  const factory GuardActionView({required String guard, required String action, String? detail}) =
      _GuardActionView;

  factory GuardActionView.fromJson(Map<String, Object?> json) => _$GuardActionViewFromJson(json);
}

/// `AsyncStatusView`: the 202 body of every asynchronous start or retry (docs/05 §2.3). A replay
/// is the first answer's snapshot, so the screen always reads the resource again afterwards.
@freezed
abstract class AsyncStatusView with _$AsyncStatusView {
  const factory AsyncStatusView({
    required String id,
    int? submissionNo,
    @JsonKey(unknownEnumValue: AsyncJobStatus.unknown) required AsyncJobStatus status,
    @JsonKey(unknownEnumValue: AsyncFailureCode.unknown) AsyncFailureCode? failureCode,
    required DateTime statusUpdatedAt,
    required String pollPath,
    int? maskedSecretCount,
  }) = _AsyncStatusView;

  factory AsyncStatusView.fromJson(Map<String, Object?> json) => _$AsyncStatusViewFromJson(json);
}
