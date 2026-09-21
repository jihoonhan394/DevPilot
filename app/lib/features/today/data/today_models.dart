import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'today_models.freezed.dart';
part 'today_models.g.dart';

// Today module view records and requests (docs/05 §8). Dates are plan dates (`yyyy-MM-dd`).

@freezed
abstract class TodayView with _$TodayView {
  const factory TodayView({
    required String dailyPlanId,
    required String planDate,
    required int availableMinutes,
    @JsonKey(unknownEnumValue: EnergyLevel.unknown) required EnergyLevel energyLevel,

    /// Null hides the risk badge.
    @JsonKey(unknownEnumValue: RiskLevel.unknown) RiskLevel? deadlineRisk,
    required bool comebackMode,
    required int generationCount,
    required DateTime generatedAt,

    /// Null when no candidate is left (docs/06 §5.2).
    MainTaskView? mainTask,

    /// Null when there is no REVIEW task.
    ReviewTaskView? reviewTask,

    /// Other main tasks of the same day, sortOrder ASC.
    @Default(<MainTaskView>[]) List<MainTaskView> earlierMainTasks,
  }) = _TodayView;

  factory TodayView.fromJson(Map<String, Object?> json) => _$TodayViewFromJson(json);
}

@freezed
abstract class MainTaskView with _$MainTaskView {
  const factory MainTaskView({
    required String id,
    @JsonKey(unknownEnumValue: TaskType.unknown) required TaskType taskType,

    /// 그 개념의 노트를 찾는 열쇠 (docs/05 §21.3). skill이 없는 과제면 null이다.
    String? skillId,
    String? skillCode,
    String? skillName,
    String? milestoneId,
    String? challengeId,
    String? sideProjectId,
    String? readingKey,
    required String title,
    String? description,
    required int estimatedMinutes,
    @JsonKey(unknownEnumValue: TaskStatus.unknown) required TaskStatus status,

    /// 1~3 reasons with server-made text (docs/06 §5.8).
    required List<ReasonView> reasons,
    DateTime? completedAt,
    required int version,
  }) = _MainTaskView;

  factory MainTaskView.fromJson(Map<String, Object?> json) => _$MainTaskViewFromJson(json);
}

@freezed
abstract class ReasonView with _$ReasonView {
  const factory ReasonView({
    @JsonKey(unknownEnumValue: ReasonCode.unknown) required ReasonCode code,
    required String text,
  }) = _ReasonView;

  factory ReasonView.fromJson(Map<String, Object?> json) => _$ReasonViewFromJson(json);
}

@freezed
abstract class ReviewTaskView with _$ReviewTaskView {
  const factory ReviewTaskView({
    required String id,
    required int estimatedMinutes,
    required int dueReviewCount,
    @JsonKey(unknownEnumValue: TaskStatus.unknown) required TaskStatus status,
    required int version,
  }) = _ReviewTaskView;

  factory ReviewTaskView.fromJson(Map<String, Object?> json) => _$ReviewTaskViewFromJson(json);
}

/// `PATCH /today/tasks/{taskId}` response.
@freezed
abstract class TaskStatusView with _$TaskStatusView {
  const factory TaskStatusView({
    required String id,
    required String dailyPlanId,
    required String planDate,
    required bool main,
    @JsonKey(unknownEnumValue: TaskType.unknown) required TaskType taskType,
    @JsonKey(unknownEnumValue: TaskStatus.unknown) required TaskStatus status,
    DateTime? completedAt,
    required int version,
  }) = _TaskStatusView;

  factory TaskStatusView.fromJson(Map<String, Object?> json) => _$TaskStatusViewFromJson(json);
}

/// `TodayGenerateRequest`: minutes 5~720 (docs/05 §8.2).
@freezed
abstract class TodayGenerateRequest with _$TodayGenerateRequest {
  const factory TodayGenerateRequest({
    required int availableMinutes,
    required EnergyLevel energyLevel,
    required bool force,
  }) = _TodayGenerateRequest;

  factory TodayGenerateRequest.fromJson(Map<String, Object?> json) =>
      _$TodayGenerateRequestFromJson(json);
}

/// `TaskStatusPatchRequest` (docs/05 §8.4). [readingFeedback] is sent only when chosen, only when
/// a READ_CODE task is completed; otherwise the field is left out of the body.
@freezed
abstract class TaskStatusPatchRequest with _$TaskStatusPatchRequest {
  const factory TaskStatusPatchRequest({
    required TaskStatus status,
    @JsonKey(includeIfNull: false) ReadingFeedback? readingFeedback,
    required int version,
  }) = _TaskStatusPatchRequest;

  factory TaskStatusPatchRequest.fromJson(Map<String, Object?> json) =>
      _$TaskStatusPatchRequestFromJson(json);
}
