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
