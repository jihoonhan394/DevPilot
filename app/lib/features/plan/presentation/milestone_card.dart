import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/widgets/badges.dart';
import 'package:devpilot_app/core/widgets/labeled_dropdown.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/features/plan/domain/milestone_ordering.dart';
import 'package:devpilot_app/features/plan/presentation/milestone_memo.dart';
import 'package:devpilot_app/features/plan/presentation/plan_action_feedback.dart';
import 'package:devpilot_app/features/plan/presentation/plan_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Milestone statuses a user can pick (all transitions are allowed, docs/04 §4.6).
const _pickableStatuses = [
  MilestoneStatus.planned,
  MilestoneStatus.inProgress,
  MilestoneStatus.done,
  MilestoneStatus.deferred,
  MilestoneStatus.dropped,
];

/// `MilestoneCard` of SCR-PLAN: period, priority, title, status dropdown, ↑/↓ and the memo.
/// With [readOnly] (SCR-PLAN-VERSION) the edit controls are left out.
class MilestoneCard extends ConsumerWidget {
  const MilestoneCard({
    super.key,
    required this.milestone,
    this.isFirst = false,
    this.isLast = false,
    this.busy = false,
    this.readOnly = false,
  });

  final MilestoneView milestone;
  final bool isFirst;
  final bool isLast;
  final bool busy;
  final bool readOnly;

  Future<void> _run(
    BuildContext context,
    WidgetRef ref,
    Future<PlanActionOutcome> Function(PlanController controller) action,
  ) async {
    final outcome = await action(ref.read(planControllerProvider.notifier));
    if (context.mounted) {
      await showPlanActionOutcome(context, ref, outcome);
    }
  }

  Future<bool> _saveMemo(BuildContext context, WidgetRef ref, String text) async {
    final outcome = await ref
        .read(planControllerProvider.notifier)
        .saveDescription(milestone.id, text);
    if (context.mounted) {
      await showPlanActionOutcome(context, ref, outcome);
    }
    return outcome is! PlanActionFailed;
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _MilestoneSummary(milestone: milestone),
            const SizedBox(height: AppSpacing.md),
            if (readOnly)
              Text(l10n.planMilestoneStatusValue(milestone.status.label(l10n)))
            else
              _StatusAndOrder(
                milestone: milestone,
                isFirst: isFirst,
                isLast: isLast,
                busy: busy,
                onStatus: (status) => _run(
                  context,
                  ref,
                  (controller) => controller.changeStatus(milestone.id, status),
                ),
                onMove: (direction) =>
                    _run(context, ref, (controller) => controller.move(milestone.id, direction)),
              ),
            const SizedBox(height: AppSpacing.sm),
            MilestoneMemo(
              milestone: milestone,
              readOnly: readOnly,
              busy: busy,
              onSave: (text) => _saveMemo(context, ref, text),
            ),
          ],
        ),
      ),
    );
  }
}

/// Period, priority, title and skills of a milestone.
class _MilestoneSummary extends StatelessWidget {
  const _MilestoneSummary({required this.milestone});

  final MilestoneView milestone;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final period = l10n.commonDateRange(
      formatPlanDate(LocalDate.parse(milestone.startDate), l10n),
      formatPlanDate(LocalDate.parse(milestone.endDate), l10n),
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(child: Text(period, style: textTheme.bodySmall)),
            PriorityBadge(priority: milestone.priority),
          ],
        ),
        const SizedBox(height: AppSpacing.xs),
        Text(milestone.title, style: textTheme.titleMedium),
        if (milestone.skillCodes.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.xs),
          Text(milestone.skillCodes.join(' · '), style: textTheme.bodySmall),
        ],
      ],
    );
  }
}

class _StatusAndOrder extends StatelessWidget {
  const _StatusAndOrder({
    required this.milestone,
    required this.isFirst,
    required this.isLast,
    required this.busy,
    required this.onStatus,
    required this.onMove,
  });

  final MilestoneView milestone;
  final bool isFirst;
  final bool isLast;
  final bool busy;
  final ValueChanged<MilestoneStatus> onStatus;
  final ValueChanged<MoveDirection> onMove;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final statuses = [
      ..._pickableStatuses,
      if (!_pickableStatuses.contains(milestone.status)) milestone.status,
    ];
    return Row(
      children: [
        Expanded(
          child: LabeledDropdown<MilestoneStatus>(
            key: Key('plan.milestone.${milestone.id}.status'),
            label: l10n.planMilestoneStatus,
            value: milestone.status,
            items: statuses,
            itemLabel: (status) => status.label(l10n),
            onChanged: busy
                ? null
                : (status) {
                    if (status != milestone.status && status != MilestoneStatus.unknown) {
                      onStatus(status);
                    }
                  },
          ),
        ),
        const SizedBox(width: AppSpacing.sm),
        IconButton(
          key: Key('plan.milestone.${milestone.id}.moveUp'),
          tooltip: l10n.planMilestoneMoveUp(milestone.title),
          onPressed: busy || isFirst ? null : () => onMove(MoveDirection.up),
          icon: const Icon(Icons.arrow_upward),
        ),
        IconButton(
          key: Key('plan.milestone.${milestone.id}.moveDown'),
          tooltip: l10n.planMilestoneMoveDown(milestone.title),
          onPressed: busy || isLast ? null : () => onMove(MoveDirection.down),
          icon: const Icon(Icons.arrow_downward),
        ),
      ],
    );
  }
}
