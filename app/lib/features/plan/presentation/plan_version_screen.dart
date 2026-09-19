import 'package:devpilot_app/app/not_found_screen.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/time_zone_support.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/features/plan/data/plan_repository.dart';
import 'package:devpilot_app/features/plan/domain/milestone_ordering.dart';
import 'package:devpilot_app/features/plan/presentation/milestone_card.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// `GET /plans/{planId}`, including superseded versions.
final planVersionProvider = FutureProvider.autoDispose.family<PlanView, String>(
  (ref, planId) => ref.watch(planRepositoryProvider).fetchPlan(planId),
);

/// SCR-PLAN-VERSION: one plan version, read-only (docs/02 §3.9).
class PlanVersionScreen extends ConsumerWidget {
  const PlanVersionScreen({super.key, required this.planId});

  final String planId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final plan = ref.watch(planVersionProvider(planId));
    final error = plan.error;
    if (error is ApiException && error.code == ApiErrorCode.planNotFound) {
      return const NotFoundScreen();
    }
    return Scaffold(
      appBar: AppBar(
        title: Semantics(
          header: true,
          child: Text(
            plan.value == null
                ? l10n.planHistoryTitle
                : l10n.planHistoryVersion(plan.requireValue.planVersion),
          ),
        ),
      ),
      body: ScreenBody(
        child: plan.when(
          loading: () => const SkeletonList(count: 3),
          error: (error, _) => ErrorView(
            error: error,
            onRetry: () => ref.invalidate(planVersionProvider(planId)),
          ),
          data: (plan) => _VersionContent(plan: plan),
        ),
      ),
    );
  }
}

class _VersionContent extends ConsumerWidget {
  const _VersionContent({required this.plan});

  final PlanView plan;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final timeZone = ref.watch(userTimeZoneProvider);
    final support = ref.watch(timeZoneSupportProvider);
    final supersededAt = plan.supersededAt;
    final reason = plan.changeReason;
    final adjusted = [
      for (final target in plan.skillTargets)
        if (target.adjustment != TargetAdjustment.roleDefault) target,
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          l10n.planHeader(plan.title, plan.planVersion),
          key: const Key('planVersion.header'),
          style: textTheme.titleMedium,
        ),
        Text(
          l10n.planVersionMeta(
            plan.status.label(l10n),
            formatInstantMonthDay(plan.createdAt, timeZone, support, l10n),
          ),
        ),
        if (supersededAt != null)
          Text(
            l10n.planVersionSuperseded(
              formatInstantMonthDay(supersededAt, timeZone, support, l10n),
            ),
          ),
        Text(reason == null || reason.isEmpty ? l10n.planHistoryNoReason : reason),
        const SizedBox(height: AppSpacing.sm),
        Text(l10n.planVersionReadOnly, style: textTheme.bodySmall),
        const SizedBox(height: AppSpacing.lg),
        for (final milestone in MilestoneOrdering.sorted(plan.milestones))
          Padding(
            padding: const EdgeInsets.only(bottom: AppSpacing.md),
            child: MilestoneCard(milestone: milestone, readOnly: true),
          ),
        if (adjusted.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.md),
          SectionTitle(l10n.planVersionAdjusted),
          const SizedBox(height: AppSpacing.sm),
          for (final target in adjusted)
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(target.skill.name),
              subtitle: Text(target.adjustment.label(l10n)),
            ),
        ],
      ],
    );
  }
}
