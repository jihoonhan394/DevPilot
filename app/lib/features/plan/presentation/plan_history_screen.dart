import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/time/time_zone_support.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/load_more_footer.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/core/widgets/status_badge.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/features/plan/presentation/plan_history_controller.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-PLAN-HISTORY: when and why the plan changed (docs/02 §3.9). Never empty after onboarding.
class PlanHistoryScreen extends ConsumerWidget {
  const PlanHistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final history = ref.watch(planHistoryControllerProvider);
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.planHistoryTitle))),
      body: history.when(
        loading: () => const ScreenBody(child: SkeletonList(count: 3, lines: 3)),
        error: (error, _) => ScreenBody(
          child: ErrorView(
            error: error,
            onRetry: () => ref.invalidate(planHistoryControllerProvider),
          ),
        ),
        data: (list) {
          final width = ScreenBody.contentWidthFor(MediaQuery.sizeOf(context).width);
          return ListView.builder(
            padding: const EdgeInsets.all(AppSpacing.screen),
            itemCount: list.items.length + 1,
            itemBuilder: (context, index) => Center(
              child: ConstrainedBox(
                constraints: BoxConstraints(maxWidth: width),
                child: index < list.items.length
                    ? _VersionRow(plan: list.items[index])
                    : LoadMoreFooter(
                        hasMore: list.hasMore,
                        isLoading: list.isLoadingMore,
                        error: list.loadMoreError,
                        onLoadMore: () async {
                          final restarted = await ref
                              .read(planHistoryControllerProvider.notifier)
                              .loadMore();
                          if (restarted && context.mounted) {
                            showToast(context, l10n.errorInvalidCursor);
                          }
                        },
                      ),
              ),
            ),
          );
        },
      ),
    );
  }
}

class _VersionRow extends ConsumerWidget {
  const _VersionRow({required this.plan});

  final PlanSummaryView plan;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final created = formatInstantMonthDay(
      plan.createdAt,
      ref.watch(userTimeZoneProvider),
      ref.watch(timeZoneSupportProvider),
      l10n,
    );
    final reason = plan.changeReason;
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: Card(
        child: ListTile(
          key: Key('planHistory.row.${plan.planVersion}'),
          minTileHeight: AppSizes.listTile,
          title: Wrap(
            spacing: AppSpacing.sm,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Text(l10n.planHistoryVersion(plan.planVersion)),
              StatusBadge(
                label: plan.status.label(l10n),
                tone: plan.status == PlanStatus.active ? AppTone.primary : AppTone.neutral,
              ),
              Text(created, style: Theme.of(context).textTheme.bodySmall),
            ],
          ),
          subtitle: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(reason == null || reason.isEmpty ? l10n.planHistoryNoReason : reason),
              Text(l10n.planHistoryMilestones(plan.milestoneCount)),
            ],
          ),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => context.go(AppRoutes.planVersion(plan.id)),
        ),
      ),
    );
  }
}
