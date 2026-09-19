import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/widgets/badges.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/plan/data/plan_budget_models.dart';
import 'package:devpilot_app/features/plan/data/plan_repository.dart';
import 'package:devpilot_app/features/plan/domain/budget_display.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// `GET /plans/active/budget`, read next to `GET /plans/active` (docs/02 SCR-PLAN "데이터").
final activeBudgetProvider = FutureProvider.autoDispose<BudgetView>(
  (ref) => ref.watch(planRepositoryProvider).fetchActiveBudget(),
);

/// `BudgetRiskCard` (docs/02 SCR-PLAN, S2): risk, the budget up to the horizon, what the MUST
/// targets need, their ratio and the completion rate used. Only this card shows an error when the
/// budget call fails; the rest of the plan stays.
class BudgetRiskCard extends ConsumerWidget {
  const BudgetRiskCard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final budget = ref.watch(activeBudgetProvider);
    return Card(
      key: const Key('plan.budgetCard'),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: budget.when(
          loading: () => const LoadingSkeleton(child: SkeletonBox(height: 72)),
          error: (error, _) => Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              InlineError(message: messageFor(error, l10n)),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(
                  key: const Key('plan.budgetRetryButton'),
                  onPressed: () => ref.invalidate(activeBudgetProvider),
                  child: Text(l10n.commonErrorRetry),
                ),
              ),
            ],
          ),
          data: (view) => _BudgetContent(view: view),
        ),
      ),
    );
  }
}

class _BudgetContent extends StatelessWidget {
  const _BudgetContent({required this.view});

  final BudgetView view;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final ratio = view.ratioBp;
    final horizon = LocalDate.parse(view.horizonDate);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            ExcludeSemantics(
              child: Text(l10n.planBudgetRisk, style: Theme.of(context).textTheme.titleSmall),
            ),
            const SizedBox(width: AppSpacing.sm),
            RiskBadge(risk: view.riskLevel),
          ],
        ),
        const SizedBox(height: AppSpacing.sm),
        Text(
          l10n.planBudgetAvailable(
            l10n.commonMonthDay(horizon.toDateTime()),
            BudgetDisplay.hours(view.effectiveBudgetMinutes),
          ),
          key: const Key('plan.budgetAvailable'),
        ),
        Text(l10n.planBudgetRequired(BudgetDisplay.hours(view.requiredMustMinutes))),
        if (ratio == null)
          Text(l10n.planBudgetNoTime, key: const Key('plan.budgetNoTime'))
        else
          Text(
            l10n.planBudgetRatio(BudgetDisplay.percent(ratio)),
            key: const Key('plan.budgetRatio'),
          ),
        Text(l10n.planBudgetCompletionRate(BudgetDisplay.percent(view.completionRateBp))),
        const SizedBox(height: AppSpacing.sm),
        _BudgetNextStep(risk: view.riskLevel),
      ],
    );
  }
}

/// The hint under the card: tight → shrink, roomy → deepen, otherwise just "계획 조정". Whether
/// suggestions exist is decided by the replan preview, not here (docs/06 §4.4).
class _BudgetNextStep extends StatelessWidget {
  const _BudgetNextStep({required this.risk});

  final RiskLevel risk;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tight = risk == RiskLevel.high || risk == RiskLevel.critical;
    final note = switch (risk) {
      RiskLevel.high || RiskLevel.critical => l10n.planBudgetTight,
      RiskLevel.low => l10n.planBudgetRoomy,
      RiskLevel.medium || RiskLevel.unknown => null,
    };
    void openReplan() => context.go(AppRoutes.replan);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (note != null) Text(note, key: const Key('plan.budgetNote')),
        Align(
          alignment: Alignment.centerRight,
          child: tight
              ? OutlinedButton(
                  key: const Key('plan.budgetReplanButton'),
                  onPressed: openReplan,
                  child: Text(l10n.planReplanButton),
                )
              : TextButton(
                  key: const Key('plan.budgetReplanButton'),
                  onPressed: openReplan,
                  child: Text(l10n.planReplanButton),
                ),
        ),
      ],
    );
  }
}
