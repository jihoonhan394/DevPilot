import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/refreshing_bar.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/core/widgets/status_badge.dart';
import 'package:devpilot_app/features/dashboard/data/dashboard_models.dart';
import 'package:devpilot_app/features/dashboard/data/dashboard_repository.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// `GET /dashboard`, read on every entry.
final dashboardProvider = FutureProvider.autoDispose<DashboardView>(
  (ref) => ref.watch(dashboardRepositoryProvider).fetchDashboard(),
);

/// SCR-DASHBOARD, minimal (S2): today's state, due reviews and this week's study time
/// (docs/02 §3.11). No streaks, rest days or shortfall percentages (U-3).
class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final dashboard = ref.watch(dashboardProvider);
    return Scaffold(
      appBar: AppBar(
        leading: context.canPop()
            ? null
            : BackButton(
                key: const Key('dashboard.backButton'),
                onPressed: () => context.go(AppRoutes.today),
              ),
        title: Semantics(header: true, child: Text(l10n.dashboardTitle)),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(2),
          child: RefreshingBar(visible: dashboard.isRefreshing),
        ),
      ),
      body: dashboard.when(
        loading: () => const ScreenBody(child: SkeletonList(count: 2, lines: 3)),
        error: (error, _) => ScreenBody(
          child: ErrorView(error: error, onRetry: () => ref.invalidate(dashboardProvider)),
        ),
        data: (view) => ScreenBody(child: _DashboardContent(view: view)),
      ),
    );
  }
}

class _DashboardContent extends StatelessWidget {
  const _DashboardContent({required this.view});

  final DashboardView view;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final weekStart = LocalDate.parse(view.weekStartDate);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (view.replanRecommended) ...[
          const _ReplanBanner(),
          const SizedBox(height: AppSpacing.lg),
        ],
        SectionTitle(l10n.dashboardToday),
        const SizedBox(height: AppSpacing.sm),
        _TodayCard(summary: view.todaySummary, dueReviewCount: view.dueReviewCount),
        const SizedBox(height: AppSpacing.xl),
        SectionTitle(l10n.dashboardWeek(l10n.commonMonthDay(weekStart.toDateTime()))),
        const SizedBox(height: AppSpacing.sm),
        Text(
          l10n.dashboardWeekSummary(
            view.weekCompletedSessions,
            formatMinutes(view.weekStudyMinutes, l10n),
          ),
          key: const Key('dashboard.weekSummary'),
          style: Theme.of(context).textTheme.titleLarge,
        ),
      ],
    );
  }
}

/// `replanRecommended = true` → SCR-REPLAN with `from=goal`.
class _ReplanBanner extends StatelessWidget {
  const _ReplanBanner();

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final colorScheme = Theme.of(context).colorScheme;
    return Card(
      key: const Key('dashboard.replanBanner'),
      color: colorScheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Row(
          children: [
            Icon(Icons.info_outline, color: colorScheme.onPrimaryContainer),
            const SizedBox(width: AppSpacing.sm),
            Expanded(child: Text(l10n.dashboardReplanRecommended)),
            OutlinedButton(
              key: const Key('dashboard.replanButton'),
              onPressed: () => context.go(AppRoutes.replanFrom(AppRoutes.replanFromGoal)),
              child: Text(l10n.planReplanButton),
            ),
          ],
        ),
      ),
    );
  }
}

/// Today's state: not generated → "Today로"; no candidate → "계획 조정"; otherwise the main task
/// with its status, estimate and the remaining due reviews.
class _TodayCard extends StatelessWidget {
  const _TodayCard({required this.summary, required this.dueReviewCount});

  final TodaySummaryView summary;
  final int dueReviewCount;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final type = summary.mainTaskType;
    final status = summary.mainTaskStatus;
    final minutes = summary.mainTaskEstimatedMinutes;
    return Card(
      key: const Key('dashboard.todayCard'),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (!summary.generated) ...[
              Text(l10n.dashboardTodayNone, key: const Key('dashboard.todayStatus')),
              const SizedBox(height: AppSpacing.md),
              FilledButton(
                key: const Key('dashboard.todayButton'),
                onPressed: () => context.go(AppRoutes.today),
                child: Text(l10n.dashboardTodayButton),
              ),
            ] else if (summary.mainTaskId == null) ...[
              Text(l10n.todayNoCandidate, key: const Key('dashboard.todayStatus')),
              const SizedBox(height: AppSpacing.md),
              OutlinedButton(
                key: const Key('dashboard.noCandidateButton'),
                onPressed: () => context.go(AppRoutes.replan),
                child: Text(l10n.todayNoCandidateButton),
              ),
            ] else ...[
              if (type != null)
                Align(
                  alignment: Alignment.centerLeft,
                  child: StatusBadge(label: type.label(l10n), tone: AppTone.primary),
                ),
              const SizedBox(height: AppSpacing.xs),
              Text(summary.mainTaskTitle ?? '', style: textTheme.titleMedium),
              if (status != null && minutes != null)
                Text(
                  l10n.dashboardTodayStatus(status.label(l10n), formatMinutes(minutes, l10n)),
                  key: const Key('dashboard.todayStatus'),
                ),
            ],
            if (dueReviewCount > 0) ...[
              const SizedBox(height: AppSpacing.sm),
              Text(
                l10n.dashboardTodayReviewLeft(dueReviewCount),
                key: const Key('dashboard.dueCount'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
