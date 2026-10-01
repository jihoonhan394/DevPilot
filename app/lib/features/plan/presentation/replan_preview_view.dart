import 'dart:async';

import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/badges.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/features/plan/data/plan_budget_models.dart';
import 'package:devpilot_app/features/plan/domain/budget_display.dart';
import 'package:devpilot_app/features/plan/domain/replan_draft.dart';
import 'package:devpilot_app/features/plan/presentation/budget_risk_card.dart';
import 'package:devpilot_app/features/plan/presentation/replan_actions.dart';
import 'package:devpilot_app/features/plan/presentation/replan_controller.dart';
import 'package:devpilot_app/features/plan/presentation/replan_preview_controller.dart';
import 'package:devpilot_app/features/plan/presentation/replan_suggestion_list.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// SCR-REPLAN ②·③: the risk if saved as edited, the suggestions to check, the recalculation and
/// the save (docs/02 SCR-REPLAN). The client shows the server's risk; it never computes one.
class ReplanPreviewView extends ConsumerWidget {
  const ReplanPreviewView({super.key, required this.replan});

  final ReplanState replan;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final preview = ref.watch(replanPreviewControllerProvider);
    final response = preview.preview;
    if (response == null) {
      return const SizedBox.shrink();
    }
    final recalculated = preview.recalculated;
    final busy = preview.busy || replan.isSaving;
    final reasonValid = ReplanRules.isReasonValid(replan.draft.reason);
    return ScreenBody(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _PreviewSummary(response: response),
          const Divider(height: AppSpacing.xxl),
          ReplanSuggestionList(response: response, preview: preview, plan: replan.draft.basePlan),
          const SizedBox(height: AppSpacing.lg),
          OutlinedButton(
            key: const Key('replan.recalcButton'),
            onPressed: busy ? null : () => unawaited(recalculateReplan(context, ref)),
            child: Text(l10n.replanPreviewRecalc),
          ),
          if (recalculated != null)
            Padding(
              padding: const EdgeInsets.only(top: AppSpacing.sm),
              child: Text(
                l10n.replanPreviewAfterSelected(recalculated.riskLevel.label(l10n)),
                key: const Key('replan.afterSelected'),
              ),
            ),
          const SizedBox(height: AppSpacing.xl),
          if (!reasonValid) InlineError(message: l10n.replanPreviewReasonNeeded),
          FilledButton(
            key: const Key('replan.saveButton'),
            onPressed: busy || !reasonValid ? null : () => unawaited(saveReplan(context, ref)),
            child: replan.isSaving
                ? SizedBox.square(
                    dimension: 20,
                    child: SelectionContainer.disabled(
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        semanticsLabel: l10n.commonSubmitting,
                      ),
                    ),
                  )
                : Text(l10n.replanSave(replan.draft.basePlan.planVersion + 1)),
          ),
          TextButton(
            key: const Key('replan.backToEditButton'),
            onPressed: busy ? null : ref.read(replanPreviewControllerProvider.notifier).backToEdit,
            child: Text(l10n.replanBackToEdit),
          ),
        ],
      ),
    );
  }
}

/// "이대로 저장하면 · 마감 위험 [..] (지금 [..]) · 가능 약 X시간 · 필수에 필요 약 Y시간".
class _PreviewSummary extends ConsumerWidget {
  const _PreviewSummary({required this.response});

  final ReplanPreviewResponse response;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final current = ref.watch(activeBudgetProvider).value?.riskLevel;
    final ratio = response.ratioBp;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.replanPreviewIfSaved),
        const SizedBox(height: AppSpacing.sm),
        Wrap(
          spacing: AppSpacing.sm,
          runSpacing: AppSpacing.xs,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            ExcludeSemantics(child: Text(l10n.planBudgetRisk)),
            RiskBadge(key: const Key('replan.previewRisk'), risk: response.riskLevel),
            if (current != null) Text(l10n.replanPreviewCurrentRisk(current.label(l10n))),
          ],
        ),
        const SizedBox(height: AppSpacing.xs),
        Text(
          l10n.replanPreviewBudget(
            BudgetDisplay.hours(response.effectiveBudgetMinutes),
            BudgetDisplay.hours(response.requiredMustMinutes),
          ),
        ),
        if (ratio != null) Text(l10n.planBudgetRatio(BudgetDisplay.percent(ratio))),
      ],
    );
  }
}
