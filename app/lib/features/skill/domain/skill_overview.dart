import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/features/skill/data/skill_models.dart';

/// One catalog skill joined with the user's state (docs/02 SCR-SKILL-TREE "클라이언트에서 결합").
final class SkillRow {
  const SkillRow({required this.node, required this.state});

  final SkillNodeView node;

  /// Null when `GET /skills/me` has no item for the skill.
  final UserSkillStateView? state;

  /// The active plan's target when present, otherwise the role target.
  AxisLevels? get targets => state?.target?.targets ?? node.roleTarget?.targets;

  Priority? get priority => state?.target?.priority ?? node.roleTarget?.priority;

  AxisLevels get planning => state?.planningLevels ?? AxisLevels.zero;

  AxisLevels get evidence => state?.evidenceLevels ?? AxisLevels.zero;

  bool get deferred => state?.target?.deferred ?? false;

  /// Any axis below its target (docs/02: "목표 미달 = 어느 축이든 planning < target").
  bool get belowTarget {
    final target = targets;
    return target != null && SkillAxis.known.any((axis) => planning.of(axis) < target.of(axis));
  }

  bool get reachedTarget => targets != null && !belowTarget;

  /// The planning level on [axis] comes from the self-assessment (it is above the evidence level).
  bool isSelfAssessed(SkillAxis axis) => planning.of(axis) > evidence.of(axis);
}

/// Skills of one category after filtering.
final class SkillCategoryGroup {
  const SkillCategoryGroup({required this.category, required this.rows});

  final SkillCategory category;
  final List<SkillRow> rows;

  int get reachedCount => rows.where((row) => row.reachedTarget).length;
}

/// Filter chips of SCR-SKILL-TREE. Default: MUST only, "목표 미달만" off.
final class SkillTreeFilter {
  const SkillTreeFilter({this.priorities = const {Priority.must}, this.onlyBelowTarget = false});

  final Set<Priority> priorities;
  final bool onlyBelowTarget;

  SkillTreeFilter togglePriority(Priority priority) => SkillTreeFilter(
    priorities: priorities.contains(priority)
        ? ({...priorities}..remove(priority))
        : {...priorities, priority},
    onlyBelowTarget: onlyBelowTarget,
  );

  SkillTreeFilter toggleOnlyBelowTarget() =>
      SkillTreeFilter(priorities: priorities, onlyBelowTarget: !onlyBelowTarget);
}

abstract final class SkillOverview {
  /// Joins the catalog with the states and groups by category in catalog order. Skills without a
  /// priority in [filter] are left out; "목표 미달만" also leaves out deferred skills.
  static List<SkillCategoryGroup> group(
    SkillTreeResponse tree,
    UserSkillStatesResponse states,
    SkillTreeFilter filter,
  ) {
    final stateBySkillId = {for (final state in states.items) state.skill.id: state};
    final groups = <SkillCategory, List<SkillRow>>{};
    for (final node in tree.skills) {
      final row = SkillRow(node: node, state: stateBySkillId[node.id]);
      final priority = row.priority;
      if (priority == null || !filter.priorities.contains(priority)) {
        continue;
      }
      if (filter.onlyBelowTarget && (!row.belowTarget || row.deferred)) {
        continue;
      }
      groups.putIfAbsent(node.category, () => []).add(row);
    }
    return [
      for (final category in SkillCategory.values)
        if (groups[category] case final rows?) SkillCategoryGroup(category: category, rows: rows),
    ];
  }

  /// The row of [skillId], or null when the catalog has no such skill.
  static SkillRow? find(
    SkillTreeResponse tree,
    UserSkillStatesResponse states,
    String skillId,
  ) {
    final node = tree.skills.where((skill) => skill.id == skillId).firstOrNull;
    if (node == null) {
      return null;
    }
    return SkillRow(
      node: node,
      state: states.items.where((state) => state.skill.id == skillId).firstOrNull,
    );
  }
}
