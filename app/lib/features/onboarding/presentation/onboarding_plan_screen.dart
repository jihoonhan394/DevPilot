import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/widgets/badges.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_rules.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_step_scaffold.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_submit_controller.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/features/plan/data/plan_repository.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// Milestones of the new plan: the onboarding response only has a summary (docs/02 step 5).
final onboardingActivePlanProvider = FutureProvider.autoDispose<PlanView>(
  (ref) => ref.watch(planRepositoryProvider).fetchActivePlan(),
);

/// SCR-ONBOARDING step 5 — plan created (`/onboarding/plan`, docs/02 §3.4). No "이전": the
/// onboarding is already saved.
class OnboardingPlanScreen extends ConsumerWidget {
  const OnboardingPlanScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final result = ref.watch(onboardingResultProvider);
    final plan = ref.watch(onboardingActivePlanProvider);
    final suggestions = result?.response.suggestedDiagnostics ?? const [];
    final offersDiagnostic = (result?.runDiagnostic ?? false) && suggestions.isNotEmpty;
    return OnboardingStepScaffold(
      step: OnboardingStep.plan,
      title: l10n.onboardingPlanTitle,
      secondaryButton: offersDiagnostic
          ? TextButton(
              key: const Key('onboarding.plan.laterButton'),
              onPressed: () => context.go(AppRoutes.start),
              child: Text(l10n.onboardingPlanStartWithoutDiagnostic),
            )
          : null,
      primaryButton: offersDiagnostic
          ? FilledButton(
              key: const Key('onboarding.plan.diagnosticStartButton'),
              onPressed: () => context.go(AppRoutes.diagnostics),
              child: Text(l10n.onboardingPlanDiagnosticStart),
            )
          : FilledButton(
              key: const Key('onboarding.plan.startButton'),
              onPressed: () => context.go(AppRoutes.start),
              child: Text(l10n.onboardingPlanStart),
            ),
      children: [
        plan.when(
          skipLoadingOnRefresh: false,
          loading: () => const SkeletonList(count: 3, lines: 2),
          error: (error, _) => ErrorView(
            error: error,
            onRetry: () => ref.invalidate(onboardingActivePlanProvider),
          ),
          data: (plan) => _PlanSummary(
            plan: plan,
            riskLevel:
                result?.response.activePlan.latestRiskLevel ?? plan.latestSnapshot?.riskLevel,
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        if (result != null) _ResultNotes(result: result),
        const SizedBox(height: AppSpacing.md),
        Text(l10n.onboardingPlanEditLater, style: Theme.of(context).textTheme.bodySmall),
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton(
            key: const Key('onboarding.plan.detailLink'),
            onPressed: () => context.go(AppRoutes.plan),
            child: Text(l10n.onboardingPlanDetail),
          ),
        ),
      ],
    );
  }
}

class _PlanSummary extends StatelessWidget {
  const _PlanSummary({required this.plan, required this.riskLevel});

  final PlanView plan;
  final RiskLevel? riskLevel;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final risk = riskLevel;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Wrap(
          spacing: AppSpacing.sm,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Text(
              l10n.planHeader(plan.title, plan.planVersion),
              key: const Key('onboarding.plan.header'),
              style: Theme.of(context).textTheme.titleMedium,
            ),
            if (risk != null) RiskBadge(risk: risk),
          ],
        ),
        const SizedBox(height: AppSpacing.md),
        for (final milestone in plan.milestones) _MilestoneRow(milestone: milestone),
      ],
    );
  }
}

class _MilestoneRow extends StatelessWidget {
  const _MilestoneRow({required this.milestone});

  final MilestoneView milestone;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final start = LocalDate.parse(milestone.startDate);
    final end = LocalDate.parse(milestone.endDate);
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.only(top: AppSpacing.xs),
            child: Icon(Icons.circle_outlined, size: 12),
          ),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  l10n.commonDateRange(formatPlanDate(start, l10n), formatPlanDate(end, l10n)),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                Text(milestone.title),
              ],
            ),
          ),
          PriorityBadge(priority: milestone.priority),
        ],
      ),
    );
  }
}

/// Side project, seed cards and diagnostic lines (docs/02 step 5 "데이터").
class _ResultNotes extends StatelessWidget {
  const _ResultNotes({required this.result});

  final OnboardingResult result;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final response = result.response;
    final project = response.sideProject;
    final suggestions = response.suggestedDiagnostics;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          project != null ? l10n.onboardingPlanProject(project.name) : l10n.onboardingPlanNoProject,
          key: const Key('onboarding.plan.projectLine'),
        ),
        if (response.assignedSeedCardCount > 0)
          Text(l10n.onboardingPlanCards(response.assignedSeedCardCount)),
        if (result.runDiagnostic) ...[
          const SizedBox(height: AppSpacing.md),
          Card(
            key: const Key('onboarding.plan.diagnosticCard'),
            child: Padding(
              padding: const EdgeInsets.all(AppSpacing.lg),
              child: Text(
                suggestions.isEmpty
                    ? l10n.onboardingPlanDiagnosticNone
                    : l10n.onboardingPlanDiagnostic(suggestions.length),
              ),
            ),
          ),
        ],
      ],
    );
  }
}
