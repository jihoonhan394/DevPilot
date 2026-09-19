import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/widgets/badges.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/core/widgets/status_badge.dart';
import 'package:devpilot_app/features/skill/data/skill_models.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:devpilot_app/features/skill/domain/skill_overview.dart';
import 'package:devpilot_app/features/skill/presentation/skill_level_bar.dart';
import 'package:devpilot_app/features/skill/presentation/skill_tree_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-SKILL-TREE: 4-axis levels and targets by category (docs/02 §3.10).
class SkillTreeScreen extends ConsumerWidget {
  const SkillTreeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final data = ref.watch(skillOverviewDataProvider);
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.skillTreeTitle))),
      body: ScreenBody(
        child: data.when(
          loading: () => const SkeletonList(count: 4, lines: 1),
          error: (error, _) => ErrorView(
            error: error,
            onRetry: () {
              ref.invalidate(skillTreeProvider);
              ref.invalidate(mySkillStatesProvider);
            },
          ),
          data: (data) => _SkillGroups(tree: data.$1, states: data.$2),
        ),
      ),
    );
  }
}

class _SkillGroups extends ConsumerWidget {
  const _SkillGroups({required this.tree, required this.states});

  final SkillTreeResponse tree;
  final UserSkillStatesResponse states;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final filter = ref.watch(skillTreeFilterProvider);
    final expanded = ref.watch(expandedCategoriesProvider);
    final groups = SkillOverview.group(tree, states, filter);
    final filterController = ref.read(skillTreeFilterProvider.notifier);
    final priorityLabel = [
      for (final priority in const [Priority.must, Priority.should, Priority.later])
        if (filter.priorities.contains(priority)) priority.label(l10n),
    ].join(' · ');
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Wrap(
          spacing: AppSpacing.sm,
          runSpacing: AppSpacing.sm,
          children: [
            for (final priority in const [Priority.must, Priority.should, Priority.later])
              FilterChip(
                key: Key('skills.filter.${priority.name}'),
                label: Text(priority.label(l10n)),
                selected: filter.priorities.contains(priority),
                onSelected: (_) => filterController.togglePriority(priority),
              ),
            FilterChip(
              key: const Key('skills.filter.onlyGap'),
              label: Text(l10n.skillTreeOnlyGap),
              selected: filter.onlyBelowTarget,
              onSelected: (_) => filterController.toggleOnlyBelowTarget(),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.lg),
        if (groups.isEmpty)
          EmptyState(icon: Icons.filter_alt_off_outlined, message: l10n.skillTreeEmptyFilter)
        else
          for (final group in groups)
            Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.sm),
              child: Card(
                child: ExpansionTile(
                  key: Key('skills.category.${group.category.name}'),
                  initiallyExpanded: expanded.contains(group.category),
                  onExpansionChanged: (_) =>
                      ref.read(expandedCategoriesProvider.notifier).toggle(group.category),
                  title: Text(group.category.label(l10n)),
                  subtitle: Text(
                    l10n.skillTreeCategorySummary(
                      priorityLabel,
                      group.rows.length,
                      group.reachedCount,
                    ),
                  ),
                  children: [for (final row in group.rows) _SkillRowTile(row: row)],
                ),
              ),
            ),
      ],
    );
  }
}

class _SkillRowTile extends StatelessWidget {
  const _SkillRowTile({required this.row});

  final SkillRow row;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final priority = row.priority;
    final targets = row.targets;
    return InkWell(
      key: Key('skills.row.${row.node.code}'),
      onTap: () => context.go(AppRoutes.skillDetail(row.node.id)),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.lg,
          AppSpacing.sm,
          AppSpacing.lg,
          AppSpacing.md,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Wrap(
              spacing: AppSpacing.sm,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Text(row.node.name, style: Theme.of(context).textTheme.titleSmall),
                if (priority != null) PriorityBadge(priority: priority),
                if (row.deferred) StatusBadge(label: l10n.skillTreeDeferred, tone: AppTone.neutral),
              ],
            ),
            const SizedBox(height: AppSpacing.xs),
            for (final axis in SkillAxis.known)
              SkillLevelBar(
                axisLabel: axis.label(l10n),
                planning: row.planning.of(axis),
                target: targets?.of(axis) ?? 0,
                selfAssessed: row.isSelfAssessed(axis),
              ),
          ],
        ),
      ),
    );
  }
}
