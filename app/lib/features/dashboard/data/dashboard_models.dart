import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'dashboard_models.freezed.dart';
part 'dashboard_models.g.dart';

/// `DashboardView` (docs/05 §13.1). 오늘 상태·이번 주에 더해 **어디쯤 왔나**(`milestoneTimeline`)와
/// **늘고 있나**(`skillCategories`)를 읽는다. `risk` 추세와 `weakThinkingAxes`는 서버가 아직 비워 둔다.
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

    /// 활성 계획이나 학습 목표가 없으면 null이다.
    MilestoneTimelineView? milestoneTimeline,
    @Default(<SkillCategorySummaryView>[]) List<SkillCategorySummaryView> skillCategories,
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

/// milestone 타임라인 (docs/05 §13.1). 프로젝트를 만드는 순서 그대로다.
@freezed
abstract class MilestoneTimelineView with _$MilestoneTimelineView {
  const MilestoneTimelineView._();

  const factory MilestoneTimelineView({
    required String planId,
    required int planVersion,
    required String todayMarker,
    required String horizonDate,
    @Default(<TimelineMilestoneView>[]) List<TimelineMilestoneView> milestones,
  }) = _MilestoneTimelineView;

  factory MilestoneTimelineView.fromJson(Map<String, Object?> json) =>
      _$MilestoneTimelineViewFromJson(json);

  /// 지금 단계. 전부 끝냈으면 null이다.
  TimelineMilestoneView? get current =>
      milestones.where((milestone) => milestone.current).firstOrNull;

  /// 지금 단계가 몇 번째인가 (1부터). 없으면 null.
  int? get currentOrdinal {
    final index = milestones.indexWhere((milestone) => milestone.current);
    return index < 0 ? null : index + 1;
  }
}

/// 타임라인의 milestone 하나.
@freezed
abstract class TimelineMilestoneView with _$TimelineMilestoneView {
  const factory TimelineMilestoneView({
    required String id,
    required String title,
    required String startDate,
    required String endDate,
    @JsonKey(unknownEnumValue: Priority.unknown) required Priority priority,
    @JsonKey(unknownEnumValue: MilestoneStatus.unknown) required MilestoneStatus status,

    /// 지금 단계인가. **날짜가 아니라 진행으로 정해진다**(ADR-044) — Today가 고르는 단계와 같다.
    required bool current,
  }) = _TimelineMilestoneView;

  factory TimelineMilestoneView.fromJson(Map<String, Object?> json) =>
      _$TimelineMilestoneViewFromJson(json);
}

/// category별 평균 레벨 (docs/05 §13.1). 평균은 4축 전체에 대한 milli 정수다.
@freezed
abstract class SkillCategorySummaryView with _$SkillCategorySummaryView {
  const SkillCategorySummaryView._();

  const factory SkillCategorySummaryView({
    @JsonKey(unknownEnumValue: SkillCategory.unknown) required SkillCategory category,
    required int skillCount,
    required int avgPlanningLevelMilli,
    required int avgTargetLevelMilli,
  }) = _SkillCategorySummaryView;

  factory SkillCategorySummaryView.fromJson(Map<String, Object?> json) =>
      _$SkillCategorySummaryViewFromJson(json);

  /// 0.0~1.0. 목표가 0이면 0이다 (나눌 것이 없다).
  double get progress =>
      avgTargetLevelMilli == 0 ? 0 : (avgPlanningLevelMilli / avgTargetLevelMilli).clamp(0.0, 1.0);
}
