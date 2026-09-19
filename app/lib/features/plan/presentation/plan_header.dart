import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/features/plan/data/learning_goal_models.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// `PlanHeader`: title · version, the user's target date and "목표 수정" (docs/02 SCR-PLAN).
class PlanHeader extends StatelessWidget {
  const PlanHeader({super.key, required this.plan, required this.goal});

  final PlanView plan;

  /// Null when `GET /learning-goal` failed: the date line is hidden.
  final LearningGoalView? goal;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final goalView = goal;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // The current risk is on the budget card below.
        Text(
          l10n.planHeader(plan.title, plan.planVersion),
          key: const Key('plan.header'),
          style: textTheme.titleMedium,
        ),
        if (goalView != null) ...[
          const SizedBox(height: AppSpacing.xs),
          Text(
            l10n.planGoalCompletion(
              formatLongDate(LocalDate.parse(goalView.targetCompletionDate), l10n),
            ),
            key: const Key('plan.targetDate'),
          ),
        ],
        Align(
          alignment: Alignment.centerRight,
          child: TextButton(
            key: const Key('plan.goalEditButton'),
            onPressed: () => context.go(AppRoutes.learningGoal),
            child: Text(l10n.planGoalEdit),
          ),
        ),
      ],
    );
  }
}

/// Shown while `replanRecommended = true` (goal dates changed, docs/02 SCR-PLAN).
class ReplanRecommendedBanner extends StatelessWidget {
  const ReplanRecommendedBanner({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final colorScheme = Theme.of(context).colorScheme;
    return Card(
      key: const Key('plan.replanRecommendedBanner'),
      color: colorScheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.info_outline, color: colorScheme.onPrimaryContainer),
                const SizedBox(width: AppSpacing.sm),
                Expanded(
                  child: Text(
                    l10n.planReplanRecommended,
                    style: TextStyle(color: colorScheme.onPrimaryContainer),
                  ),
                ),
              ],
            ),
            Align(
              alignment: Alignment.centerRight,
              child: OutlinedButton(
                key: const Key('plan.replanButton'),
                onPressed: () => context.go(AppRoutes.replan),
                child: Text(l10n.planReplanButton),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// "오늘 {date}" line placed at today's position in the milestone list.
class TodayDivider extends StatelessWidget {
  const TodayDivider({super.key, required this.today});

  final LocalDate today;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final colorScheme = Theme.of(context).colorScheme;
    return Padding(
      key: const Key('plan.todayDivider'),
      padding: const EdgeInsets.only(bottom: AppSpacing.md),
      child: Row(
        children: [
          Text(
            l10n.planToday(formatPlanDate(today, l10n)),
            style: Theme.of(context).textTheme.labelLarge?.copyWith(color: colorScheme.primary),
          ),
          const SizedBox(width: AppSpacing.sm),
          Expanded(child: Divider(color: colorScheme.primary, thickness: 2)),
        ],
      ),
    );
  }
}
