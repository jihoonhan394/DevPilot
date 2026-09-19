import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/refreshing_bar.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/plan/domain/milestone_ordering.dart';
import 'package:devpilot_app/features/plan/presentation/milestone_card.dart';
import 'package:devpilot_app/features/plan/presentation/plan_action_feedback.dart';
import 'package:devpilot_app/features/plan/presentation/plan_controller.dart';
import 'package:devpilot_app/features/plan/presentation/plan_header.dart';
import 'package:devpilot_app/features/plan/presentation/plan_timeline_bar.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-PLAN: milestone timeline of the active plan with in-place status, memo and order edits
/// (docs/02 §3.9). The budget card arrives with S2.
class PlanScreen extends ConsumerWidget {
  const PlanScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final screen = ref.watch(planControllerProvider);
    return Scaffold(
      appBar: AppBar(
        title: Semantics(header: true, child: Text(l10n.planTitle)),
        actions: [
          TextButton(
            key: const Key('plan.versionsButton'),
            onPressed: () => context.go(AppRoutes.planVersions),
            child: Text(l10n.planVersions),
          ),
          const SizedBox(width: AppSpacing.sm),
        ],
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(2),
          child: RefreshingBar(visible: screen.isRefreshing),
        ),
      ),
      body: screen.when(
        loading: () => const ScreenBody(child: SkeletonList(count: 4)),
        error: (error, _) => ScreenBody(
          child: ErrorView(
            error: error,
            onRetry: () => ref.read(planControllerProvider.notifier).reload(),
          ),
        ),
        data: (data) => data.plan == null ? const _EmptyPlan() : _PlanContent(data: data),
      ),
    );
  }
}

class _EmptyPlan extends ConsumerWidget {
  const _EmptyPlan();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    return ScreenBody(
      child: EmptyState(
        icon: Icons.timeline,
        message: l10n.planEmpty,
        actionLabel: l10n.planCreate,
        actionKey: const Key('plan.createButton'),
        onAction: () async {
          final outcome = await ref.read(planControllerProvider.notifier).createPlan();
          if (context.mounted) {
            await showPlanActionOutcome(context, ref, outcome);
          }
        },
      ),
    );
  }
}

class _PlanContent extends ConsumerWidget {
  const _PlanContent({required this.data});

  final PlanScreenData data;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final plan = data.plan!;
    final milestones = data.milestones;
    final today = LocalDate.parse(ref.watch(meProvider).requireValue.today);
    final dividerIndex = MilestoneOrdering.todayDividerIndex(milestones, today);
    final wide = MediaQuery.sizeOf(context).width >= AppBreakpoints.tablet;
    return ScreenBody(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          PlanHeader(plan: plan, goal: data.goal),
          if (plan.replanRecommended) ...[
            const SizedBox(height: AppSpacing.md),
            const ReplanRecommendedBanner(),
          ],
          if (wide && milestones.isNotEmpty) ...[
            const SizedBox(height: AppSpacing.lg),
            PlanTimelineBar(milestones: milestones, today: today, goal: data.goal),
          ],
          const SizedBox(height: AppSpacing.lg),
          for (var index = 0; index <= milestones.length; index++) ...[
            if (index == dividerIndex) TodayDivider(today: today),
            if (index < milestones.length)
              Padding(
                padding: const EdgeInsets.only(bottom: AppSpacing.md),
                child: MilestoneCard(
                  key: ValueKey(milestones[index].id),
                  milestone: milestones[index],
                  isFirst: index == 0,
                  isLast: index == milestones.length - 1,
                  busy: data.busyMilestoneIds.contains(milestones[index].id),
                ),
              ),
          ],
          const SizedBox(height: AppSpacing.md),
          FilledButton(
            key: const Key('plan.restructureButton'),
            onPressed: () => context.go(AppRoutes.replan),
            child: Text(l10n.planRestructure),
          ),
          const SizedBox(height: AppSpacing.sm),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              key: const Key('plan.projectsLink'),
              onPressed: () => context.go(AppRoutes.projects),
              icon: const Icon(Icons.chevron_right),
              label: Text(l10n.planProjectsLink),
            ),
          ),
        ],
      ),
    );
  }
}
