import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'dashboard_models.freezed.dart';
part 'dashboard_models.g.dart';

/// `DashboardView` fields of the minimal dashboard (docs/05 §13.1, S2). The S5 sections (`risk`,
/// `milestoneTimeline`, `skillCategories`, `weakThinkingAxes`) are null or empty until then and
/// are not read.
@freezed
abstract class DashboardView with _$DashboardView {
  const factory DashboardView({
    required String today,
    required TodaySummaryView todaySummary,
    required int dueReviewCount,

    /// Monday of today's ISO week.
    required String weekStartDate,
    required int weekStudyMinutes,
    required int weekCompletedSessions,
    @JsonKey(unknownEnumValue: AiStatus.unknown) required AiStatus aiStatus,
    required bool replanRecommended,
  }) = _DashboardView;

  factory DashboardView.fromJson(Map<String, Object?> json) => _$DashboardViewFromJson(json);
}

@freezed
abstract class TodaySummaryView with _$TodaySummaryView {
  const factory TodaySummaryView({
    /// Today's daily plan exists.
    required bool generated,
    String? mainTaskId,
    String? mainTaskTitle,
    @JsonKey(unknownEnumValue: TaskType.unknown) TaskType? mainTaskType,
    @JsonKey(unknownEnumValue: TaskStatus.unknown) TaskStatus? mainTaskStatus,
    int? mainTaskEstimatedMinutes,
    @JsonKey(unknownEnumValue: TaskStatus.unknown) TaskStatus? reviewTaskStatus,
  }) = _TodaySummaryView;

  factory TodaySummaryView.fromJson(Map<String, Object?> json) => _$TodaySummaryViewFromJson(json);
}
