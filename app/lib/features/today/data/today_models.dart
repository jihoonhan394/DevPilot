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

    /// 며칠 전에 끝낸 원본 과제 (docs/06 §5.10 RE-1). `taskType`이 REDO일 때만 있다.
    String? redoSourceTaskId,
    @JsonKey(unknownEnumValue: TaskType.unknown) TaskType? redoSourceTaskType,

    /// "AI 도움 없이 끝냈나요?"의 답 (RE-6). 완료한 REDO에만 있다.
    bool? redoWithoutAi,
    required String title,
    String? description,

    /// 시작 전·끝내기 전 확인 목록 (docs/05 §8.1). 맞는 목록이 없거나 skill이 없는 과제면 null이다.
    ChecklistView? checklist,
    required int estimatedMinutes,
    @JsonKey(unknownEnumValue: TaskStatus.unknown) required TaskStatus status,

    /// 1~3 reasons with server-made text (docs/06 §5.8).
    required List<ReasonView> reasons,
    DateTime? completedAt,
    required int version,
  }) = _MainTaskView;

  factory MainTaskView.fromJson(Map<String, Object?> json) => _$MainTaskViewFromJson(json);
}

/// 과제 체크리스트 (docs/05 §8.1). 저장되지 않고 응답을 만들 때 채워지므로 지난 과제에도 지금 글이 붙는다.
@freezed
abstract class ChecklistView with _$ChecklistView {
  const factory ChecklistView({
    required String key,

    /// 시작 전 확인 3~5개.
    required List<String> before,

    /// 끝내기 전 확인 3~5개.
    required List<String> after,
  }) = _ChecklistView;

  factory ChecklistView.fromJson(Map<String, Object?> json) => _$ChecklistViewFromJson(json);
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

/// `TaskStatusPatchRequest` (docs/05 §8.4). [redoWithoutAi] is required when a REDO task is
/// completed and rejected elsewhere. [readingFeedback] is sent only when chosen, only when
/// a READ_CODE task is completed; otherwise the field is left out of the body.
@freezed
abstract class TaskStatusPatchRequest with _$TaskStatusPatchRequest {
  const factory TaskStatusPatchRequest({
    required TaskStatus status,
    @JsonKey(includeIfNull: false) ReadingFeedback? readingFeedback,
    @JsonKey(includeIfNull: false) bool? redoWithoutAi,
    required int version,
  }) = _TaskStatusPatchRequest;

  factory TaskStatusPatchRequest.fromJson(Map<String, Object?> json) =>
      _$TaskStatusPatchRequestFromJson(json);
}
