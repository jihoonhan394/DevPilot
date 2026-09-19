import 'dart:async';

import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/badges.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';
import 'package:devpilot_app/features/today/presentation/main_task_card.dart';
import 'package:devpilot_app/features/today/presentation/today_actions.dart';
import 'package:devpilot_app/features/today/presentation/today_state.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-TODAY after generation: comeback banner, risk and input line, the main task, the REVIEW
/// row and the earlier main tasks of the day (docs/02 SCR-TODAY "생성 후").
class TodayGeneratedView extends StatelessWidget {
  const TodayGeneratedView({super.key, required this.data, required this.today, this.footer});

  final TodayScreenData data;
  final TodayView today;

  /// Shown at the bottom (the install card).
  final Widget? footer;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final main = today.mainTask;
    final review = today.reviewTask;
    final footerWidget = footer;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (today.comebackMode) ...[const _ComebackBanner(), const SizedBox(height: AppSpacing.md)],
        _SummaryBar(today: today, data: data),
        const SizedBox(height: AppSpacing.lg),
        SectionTitle(l10n.todayMainTitle),
        const SizedBox(height: AppSpacing.sm),
        if (main == null) const _NoCandidate() else MainTaskCard(task: main, data: data),
        if (today.earlierMainTasks.isNotEmpty) _EarlierTasks(tasks: today.earlierMainTasks),
        if (review != null) ...[
          const SizedBox(height: AppSpacing.md),
          _ReviewTaskTile(task: review),
        ],
        if (footerWidget != null) ...[const SizedBox(height: AppSpacing.xl), footerWidget],
      ],
    );
  }
}

/// One line, no close button (docs/02 §4.14).
class _ComebackBanner extends StatelessWidget {
  const _ComebackBanner();

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return Card(
      key: const Key('today.comebackBanner'),
      color: colorScheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Text(
          AppLocalizations.of(context).todayComebackBanner,
          style: TextStyle(color: colorScheme.onPrimaryContainer),
        ),
      ),
    );
  }
}

/// "마감 위험 [보통]   45분 · 보통 [변경]".
class _SummaryBar extends ConsumerWidget {
  const _SummaryBar({required this.today, required this.data});

  final TodayView today;
  final TodayScreenData data;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final risk = today.deadlineRisk;
    return Wrap(
      spacing: AppSpacing.md,
      runSpacing: AppSpacing.sm,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        if (risk != null)
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              ExcludeSemantics(child: Text(l10n.todayRiskLabel)),
              const SizedBox(width: AppSpacing.xs),
              RiskBadge(risk: risk),
            ],
          ),
        Text(
          l10n.todayInputSummary(
            formatMinutes(today.availableMinutes, l10n),
            today.energyLevel.label(l10n),
          ),
          key: const Key('today.inputSummary'),
        ),
        TextButton(
          key: const Key('today.changeButton'),
          onPressed: data.busy
              ? null
              : () => unawaited(regenerateToday(context, ref, mainStatus: data.mainTask?.status)),
          child: Text(l10n.todayChange),
        ),
      ],
    );
  }
}

/// `mainTask = null`: every target is reached; the next step is adjusting the plan.
class _NoCandidate extends StatelessWidget {
  const _NoCandidate();

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.todayNoCandidate, key: const Key('today.noCandidate')),
            const SizedBox(height: AppSpacing.md),
            FilledButton(
              key: const Key('today.noCandidateButton'),
              onPressed: () => context.go(AppRoutes.replan),
              child: Text(l10n.todayNoCandidateButton),
            ),
          ],
        ),
      ),
    );
  }
}

/// "복습 {n}장 · 약 {m}" + "복습" → SCR-REVIEW-SESSION with the task id.
class _ReviewTaskTile extends StatelessWidget {
  const _ReviewTaskTile({required this.task});

  final ReviewTaskView task;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Card(
      key: const Key('today.reviewTile'),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.lg, vertical: AppSpacing.sm),
        child: Row(
          children: [
            Expanded(
              child: Text(
                l10n.todayReviewTile(
                  task.dueReviewCount,
                  formatMinutes(task.estimatedMinutes, l10n),
                ),
              ),
            ),
            OutlinedButton(
              key: const Key('today.reviewButton'),
              onPressed: () => context.go(AppRoutes.reviewSessionFor(task.id)),
              child: Text(l10n.todayReviewButton),
            ),
          ],
        ),
      ),
    );
  }
}

/// "오늘 앞서 한 과제 {n}개", folded.
class _EarlierTasks extends StatelessWidget {
  const _EarlierTasks({required this.tasks});

  final List<MainTaskView> tasks;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return ExpansionTile(
      key: const Key('today.earlierTasks'),
      tilePadding: EdgeInsets.zero,
      title: Text(l10n.todayEarlier(tasks.length)),
      children: [
        for (final task in tasks)
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(l10n.todayEarlierItem(task.title, task.status.label(l10n))),
          ),
      ],
    );
  }
}
