import 'package:devpilot_app/app/not_found_screen.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/badges.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:devpilot_app/features/skill/domain/skill_overview.dart';
import 'package:devpilot_app/features/skill/presentation/skill_tree_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// SCR-SKILL-DETAIL, S1 part: header, axis table and self-assessment line (docs/02 §3.10).
/// The level history (S3) and the review/practice/explain buttons (S2/S3) come later.
class SkillDetailScreen extends ConsumerWidget {
  const SkillDetailScreen({super.key, required this.skillId});

  final String skillId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final data = ref.watch(skillOverviewDataProvider);
    final row = data.value == null
        ? null
        : SkillOverview.find(data.requireValue.$1, data.requireValue.$2, skillId);
    if (data.hasValue && row == null) {
      return const NotFoundScreen();
    }
    return Scaffold(
      appBar: AppBar(
        title: Semantics(header: true, child: Text(row?.node.name ?? l10n.skillTreeTitle)),
      ),
      body: ScreenBody(
        child: data.when(
          loading: () => const SkeletonList(count: 2, lines: 4),
          error: (error, _) => ErrorView(
            error: error,
            onRetry: () {
              ref.invalidate(skillTreeProvider);
              ref.invalidate(mySkillStatesProvider);
            },
          ),
          data: (_) => _SkillDetail(row: row!),
        ),
      ),
    );
  }
}

class _SkillDetail extends StatelessWidget {
  const _SkillDetail({required this.row});

  final SkillRow row;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final priority = row.priority;
    final description = row.node.description;
    final state = row.state;
    final selfLevel = state?.selfAssessedLevel;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Wrap(
          spacing: AppSpacing.sm,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Text(row.node.category.label(l10n), style: textTheme.bodyMedium),
            if (priority != null) PriorityBadge(priority: priority),
          ],
        ),
        if (description != null && description.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.sm),
          Text(description),
        ],
        const SizedBox(height: AppSpacing.lg),
        _AxisTable(row: row),
        const SizedBox(height: AppSpacing.lg),
        if (selfLevel != null)
          Text(
            l10n.skillDetailSelf(skillLevelLabel(selfLevel, l10n)),
            key: const Key('skillDetail.selfLine'),
          ),
        if (state != null && !state.selfAssessmentActive)
          Text(l10n.skillDetailSelfInactive, style: textTheme.bodySmall),
      ],
    );
  }
}

/// `축 | 증거 레벨 | 계획용 레벨 | 목표`, each level as a number and its `SkillLevel` label.
class _AxisTable extends StatelessWidget {
  const _AxisTable({required this.row});

  final SkillRow row;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final headerStyle = Theme.of(context).textTheme.labelLarge;
    String level(int value) => l10n.skillDetailLevel(value, skillLevelLabel(value, l10n));
    final targets = row.targets;
    return Table(
      key: const Key('skillDetail.axisTable'),
      columnWidths: const {0: IntrinsicColumnWidth()},
      defaultVerticalAlignment: TableCellVerticalAlignment.middle,
      children: [
        TableRow(
          children: [
            for (final title in [
              l10n.skillDetailAxis,
              l10n.skillDetailEvidence,
              l10n.skillDetailPlanning,
              l10n.skillDetailTarget,
            ])
              Padding(
                padding: const EdgeInsets.all(AppSpacing.xs),
                child: Text(title, style: headerStyle),
              ),
          ],
        ),
        for (final axis in SkillAxis.known)
          TableRow(
            children: [
              for (final cell in [
                axis.label(l10n),
                level(row.evidence.of(axis)),
                level(row.planning.of(axis)),
                if (targets == null) '—' else level(targets.of(axis)),
              ])
                Padding(padding: const EdgeInsets.all(AppSpacing.xs), child: Text(cell)),
            ],
          ),
      ],
    );
  }
}
