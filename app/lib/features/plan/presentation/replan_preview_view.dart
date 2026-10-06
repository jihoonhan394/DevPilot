import 'dart:async';

import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
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
          // 목표일에 맞춘 날짜 제안 (ADR-067). 경고가 아니라 **동작**이다 — 목표일을 바꾸지 않아도
          // 오늘이 지나면 창이 줄어 제안이 생기므로, 알림으로 두면 매번 뜨는 잔소리가 된다.
          if (response.milestoneSchedule.isNotEmpty)
            _Reschedule(schedule: response.milestoneSchedule),
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
/// "목표일에 맞춰 일정 다시 배치" — 제안 날짜를 편집 양식에 넣는다. 저장은 아래 저장 버튼이 한다.
class _Reschedule extends ConsumerWidget {
  const _Reschedule({required this.schedule});

  final List<MilestoneScheduleView> schedule;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final moving = schedule.where((item) => item.changed).length;
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.lg),
      child: Column(
        key: const Key('replan.reschedule'),
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(l10n.replanPreviewRescheduleHint(moving), style: theme.textTheme.bodySmall),
          Align(
            alignment: Alignment.centerLeft,
            child: OutlinedButton(
              key: const Key('replan.rescheduleButton'),
              onPressed: () {
                ref.read(replanControllerProvider.notifier).applySchedule(schedule);
                showToast(context, l10n.replanPreviewRescheduleDone);
              },
              child: Text(l10n.replanPreviewReschedule),
            ),
          ),
        ],
      ),
    );
  }
}

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
