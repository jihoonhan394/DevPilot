import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'learning_goal_models.freezed.dart';
part 'learning_goal_models.g.dart';

/// `LearningGoalView` (docs/05 §5.1): the learning track and one target date (목표일).
@freezed
abstract class LearningGoalView with _$LearningGoalView {
  const factory LearningGoalView({
    required String id,
    @JsonKey(unknownEnumValue: TargetRole.unknown) required TargetRole targetRole,
    required String targetCompletionDate,

    /// code ASC.
    required List<SkillRef> focusSkills,
    required bool replanRecommended,
    required DateTime createdAt,
    required DateTime updatedAt,
    required int version,
  }) = _LearningGoalView;

  factory LearningGoalView.fromJson(Map<String, Object?> json) => _$LearningGoalViewFromJson(json);
}

/// `LearningGoalUpdateRequest` of `PUT /learning-goal` (docs/05 §5.2): a full replacement.
@freezed
abstract class LearningGoalUpdateRequest with _$LearningGoalUpdateRequest {
  const factory LearningGoalUpdateRequest({
    required TargetRole targetRole,
    required String targetCompletionDate,
    required List<String> focusSkillCodes,
    required int version,
  }) = _LearningGoalUpdateRequest;

  factory LearningGoalUpdateRequest.fromJson(Map<String, Object?> json) =>
      _$LearningGoalUpdateRequestFromJson(json);
}
