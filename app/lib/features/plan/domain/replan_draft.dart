import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/features/plan/domain/milestone_ordering.dart';
import 'package:flutter/foundation.dart';

/// One milestone being edited on SCR-REPLAN.
@immutable
final class MilestoneDraft {
  const MilestoneDraft({
    required this.localKey,
    required this.id,
    required this.title,
    required this.description,
    required this.startDate,
    required this.endDate,
    required this.priority,
    required this.status,
    required this.skillCodes,
  });

  factory MilestoneDraft.fromView(MilestoneView view) => MilestoneDraft(
    localKey: view.id,
    id: view.id,
    title: view.title,
    description: view.description ?? '',
    startDate: LocalDate.parse(view.startDate),
    endDate: LocalDate.parse(view.endDate),
    priority: view.priority,
    status: view.status,
    skillCodes: view.skillCodes,
  );

  /// Stable identity for the UI; the server id for existing milestones.
  final String localKey;

  /// Server id; null for a milestone added in this edit.
  final String? id;
  final String title;
  final String description;
  final LocalDate startDate;
  final LocalDate endDate;
  final Priority priority;
  final MilestoneStatus status;
  final List<String> skillCodes;

  MilestoneDraft copyWith({
    String? title,
    String? description,
    LocalDate? startDate,
    LocalDate? endDate,
    Priority? priority,
    List<String>? skillCodes,
  }) => MilestoneDraft(
    localKey: localKey,
    id: id,
    title: title ?? this.title,
    description: description ?? this.description,
    startDate: startDate ?? this.startDate,
    endDate: endDate ?? this.endDate,
    priority: priority ?? this.priority,
    status: status,
    skillCodes: skillCodes ?? this.skillCodes,
  );

  /// `MilestoneInput` at position [sortOrder]. An empty memo is sent as null.
  MilestoneInput toInput(int sortOrder) => MilestoneInput(
    id: id,
    title: title,
    description: description.isEmpty ? null : description,
    startDate: startDate.toIso(),
    endDate: endDate.toIso(),
    priority: priority,
    status: status,
    sortOrder: sortOrder,
    skillCodes: skillCodes,
  );

  @override
  bool operator ==(Object other) =>
      other is MilestoneDraft &&
      other.localKey == localKey &&
      other.id == id &&
      other.title == title &&
      other.description == description &&
      other.startDate == startDate &&
      other.endDate == endDate &&
      other.priority == priority &&
      other.status == status &&
      listEquals(other.skillCodes, skillCodes);

  @override
  int get hashCode => Object.hash(
    localKey,
    id,
    title,
    description,
    startDate,
    endDate,
    priority,
    status,
    Object.hashAll(skillCodes),
  );
}

/// Field problems of one milestone (docs/02 §3.2 milestone rows).
enum MilestoneFieldViolation {
  titleRequired,
  titleTooLong,
  descriptionTooLong,
  dateOutOfRange,
  dateOrder,
  tooManySkills,
}

/// The whole SCR-REPLAN edit: reason plus the ordered milestones.
@immutable
final class ReplanDraft {
  const ReplanDraft({required this.basePlan, required this.reason, required this.milestones});

  factory ReplanDraft.of(PlanView plan) => ReplanDraft(
    basePlan: plan,
    reason: '',
    milestones: [
      for (final milestone in MilestoneOrdering.sorted(plan.milestones))
        MilestoneDraft.fromView(milestone),
    ],
  );

  /// The active plan the edit started from (its `version` goes into the request).
  final PlanView basePlan;
  final String reason;
  final List<MilestoneDraft> milestones;

  bool get isDirty =>
      reason.isNotEmpty || !listEquals(milestones, ReplanDraft.of(basePlan).milestones);

  ReplanDraft copyWith({String? reason, List<MilestoneDraft>? milestones}) => ReplanDraft(
    basePlan: basePlan,
    reason: reason ?? this.reason,
    milestones: milestones ?? this.milestones,
  );

  /// `POST /plans/{planId}/replan` body. S1 sends the four suggestion lists empty (docs/05 §7.8).
  ReplanRequest toRequest() => ReplanRequest(
    reason: reason,
    version: basePlan.version,
    milestones: [
      for (var index = 0; index < milestones.length; index++) milestones[index].toInput(index),
    ],
  );
}

/// Client checks of SCR-REPLAN (docs/02 §3.2). The server repeats them.
abstract final class ReplanRules {
  static bool isReasonValid(String reason) =>
      InputRules.isRequiredText(reason, InputRules.replanReasonMaxLength);

  static bool isCountValid(int count) => count >= 1 && count <= InputRules.milestoneMaxCount;

  /// Milestone dates must be within today − 1 year .. today + 3 years.
  static LocalDate earliestDate(LocalDate today) => today.addYears(-1);

  static LocalDate latestDate(LocalDate today) => today.addYears(3);

  static Set<MilestoneFieldViolation> violations(MilestoneDraft milestone, LocalDate today) => {
    if (milestone.title.trim().isEmpty) MilestoneFieldViolation.titleRequired,
    if (milestone.title.length > InputRules.milestoneTitleMaxLength)
      MilestoneFieldViolation.titleTooLong,
    if (milestone.description.length > InputRules.milestoneDescriptionMaxLength)
      MilestoneFieldViolation.descriptionTooLong,
    if (milestone.startDate.isBefore(earliestDate(today)) ||
        milestone.endDate.isAfter(latestDate(today)))
      MilestoneFieldViolation.dateOutOfRange,
    if (milestone.startDate.isAfter(milestone.endDate)) MilestoneFieldViolation.dateOrder,
    if (milestone.skillCodes.length > InputRules.milestoneMaxSkills)
      MilestoneFieldViolation.tooManySkills,
  };

  static bool canSave(ReplanDraft draft, LocalDate today) =>
      isReasonValid(draft.reason) &&
      isCountValid(draft.milestones.length) &&
      draft.milestones.every((milestone) => violations(milestone, today).isEmpty);

  /// A new milestone: starts today, one month long, `SHOULD`, `PLANNED`.
  static MilestoneDraft newMilestone({required String localKey, required LocalDate today}) =>
      MilestoneDraft(
        localKey: localKey,
        id: null,
        title: '',
        description: '',
        startDate: today,
        endDate: today.addMonths(1),
        priority: Priority.should,
        status: MilestoneStatus.planned,
        skillCodes: const [],
      );

  /// Index of the milestone a server field path such as `milestones[2].endDate` points to.
  static int? milestoneIndexOf(String field) {
    final match = RegExp(r'^milestones\[(\d+)\]').firstMatch(field);
    return match == null ? null : int.parse(match.group(1)!);
  }
}
