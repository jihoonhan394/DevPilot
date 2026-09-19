import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'skill_history_models.freezed.dart';
part 'skill_history_models.g.dart';

/// `SkillStateChangeView` of `GET /skills/{skillId}/history` (docs/05 §6.3). One row per axis
/// level change, newest first.
@freezed
abstract class SkillStateChangeView with _$SkillStateChangeView {
  const factory SkillStateChangeView({
    required String id,
    @JsonKey(unknownEnumValue: SkillAxis.unknown) required SkillAxis axis,
    required int fromLevel,
    required int toLevel,

    /// `rule_code` of docs/06 §7.2~§7.4; the screen shows `skill.rule.<rule_code>`.
    required String ruleCode,

    /// The events the rule looked at, newest first, at most 10.
    required List<EvidenceEventView> evidenceEvents,
    required DateTime changedAt,
  }) = _SkillStateChangeView;

  factory SkillStateChangeView.fromJson(Map<String, Object?> json) =>
      _$SkillStateChangeViewFromJson(json);
}

/// One `learning_event` behind a level change (docs/05 §6.3).
@freezed
abstract class EvidenceEventView with _$EvidenceEventView {
  const factory EvidenceEventView({
    required String id,
    @JsonKey(unknownEnumValue: LearningEventType.unknown) required LearningEventType eventType,

    /// `yyyy-MM-dd` plan date, parsed with `LocalDate` where it is shown.
    required String planDate,
    required DateTime occurredAt,

    /// True once the event was invalidated; it no longer counts towards any rule.
    required bool invalidated,
  }) = _EvidenceEventView;

  factory EvidenceEventView.fromJson(Map<String, Object?> json) =>
      _$EvidenceEventViewFromJson(json);
}
