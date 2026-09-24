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

/// SCR-DASHBOARD (docs/02 §3.11): 오늘 상태, due 복습, 이번 주 학습 시간에 더해
/// **지금 단계**(프로젝트를 만드는 순서 중 어디인가)와 **얼마나 왔나**(category별 평균)를 보여 준다.
/// 연속 일수·쉬는 날·부족률은 두지 않는다 (U-3).
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
        if (view.milestoneTimeline case final timeline?) ...[
          const SizedBox(height: AppSpacing.xl),
          SectionTitle(l10n.dashboardStep),
          const SizedBox(height: AppSpacing.sm),
          _StepCard(timeline: timeline),
        ],
        if (view.skillCategories.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.xl),
          SectionTitle(l10n.dashboardSkills),
          const SizedBox(height: AppSpacing.sm),
          for (final category in view.skillCategories) _CategoryRow(category: category),
        ],
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

/// 지금 단계 (docs/05 §13.1). 서버가 **진행으로** 정해 준다(ADR-044) — 화면은 날짜로 다시 계산하지 않는다.
class _StepCard extends StatelessWidget {
  const _StepCard({required this.timeline});

  final MilestoneTimelineView timeline;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final current = timeline.current;
    if (current == null) {
      return Text(
        l10n.dashboardStepAllDone,
        key: const Key('dashboard.stepAllDone'),
        style: theme.textTheme.titleMedium,
      );
    }
    final ordinal = timeline.currentOrdinal!;
    final next = ordinal < timeline.milestones.length ? timeline.milestones[ordinal] : null;
    return Card(
      key: const Key('dashboard.stepCard'),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                StatusBadge(
                  label: l10n.dashboardStepOf(ordinal, timeline.milestones.length),
                  tone: AppTone.primary,
                ),
                const SizedBox(width: AppSpacing.sm),
                Expanded(
                  child: Text(
                    current.title,
                    key: const Key('dashboard.stepTitle'),
                    style: theme.textTheme.titleMedium,
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            LinearProgressIndicator(
              value: (ordinal - 1) / timeline.milestones.length,
              // 몇 단계인지는 위 배지가 글로 읽어 준다.
              semanticsLabel: '',
            ),
            if (next != null) ...[
              const SizedBox(height: AppSpacing.sm),
              Text(
                l10n.dashboardStepNext(next.title),
                key: const Key('dashboard.stepNext'),
                style: theme.textTheme.bodySmall,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// category 한 줄: 지금 평균과 목표. **"얼마나 남았는지"가 보이는 것이 이 줄의 목적이다.**
class _CategoryRow extends StatelessWidget {
  const _CategoryRow({required this.category});

  final SkillCategorySummaryView category;

  /// milli 정수를 소수 한 자리로 (예: 1500 → 1.5).
  static String _level(int milli) => (milli / 1000).toStringAsFixed(1);

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Padding(
      key: Key('dashboard.category.${category.category.name}'),
      padding: const EdgeInsets.only(bottom: AppSpacing.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text(category.category.label(l10n))),
              Text(
                l10n.dashboardSkillCount(category.skillCount),
                style: theme.textTheme.bodySmall,
              ),
              const SizedBox(width: AppSpacing.sm),
              Text(
                l10n.dashboardSkillLevels(
                  _level(category.avgPlanningLevelMilli),
                  _level(category.avgTargetLevelMilli),
                ),
                style: theme.textTheme.labelLarge,
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.xs),
          // 진행은 옆의 "지금 / 목표" 숫자가 읽어 주므로 막대는 장식이다.
          LinearProgressIndicator(value: category.progress, semanticsLabel: ''),
        ],
      ),
    );
  }
}
