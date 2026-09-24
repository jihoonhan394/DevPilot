import 'dart:async';

import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/features/today/presentation/today_actions.dart';
import 'package:devpilot_app/features/today/presentation/today_controller.dart';
import 'package:devpilot_app/features/today/presentation/today_diagnostic_card.dart';
import 'package:devpilot_app/features/today/presentation/today_input_controller.dart';
import 'package:devpilot_app/features/today/presentation/today_input_fields.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// SCR-TODAY before generation (`404 TODAY_NOT_GENERATED`): minutes, energy and
/// "오늘 계획 만들기" (docs/02 SCR-TODAY "생성 전").
class TodayGeneratePanel extends ConsumerWidget {
  const TodayGeneratePanel({super.key, required this.busy, this.footer});

  final bool busy;

  /// Shown at the bottom (the install card).
  final Widget? footer;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final input = ref.watch(todayInputProvider);
    final inputController = ref.read(todayInputProvider.notifier);
    final footerWidget = footer;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // 처음 여는 사람에게는 시간·컨디션을 왜 묻는지가 보이지 않는다.
        Padding(
          padding: const EdgeInsets.only(bottom: AppSpacing.lg),
          child: Text(
            l10n.todayGenerateLead,
            key: const Key('today.generateLead'),
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ),
        TodayInputFields(
          minutes: input.minutes,
          energy: input.energy,
          enabled: !busy,
          onMinutesChanged: inputController.setMinutes,
          onEnergyChanged: inputController.setEnergy,
        ),
        const SizedBox(height: AppSpacing.xl),
        FilledButton(
          key: const Key('today.generateButton'),
          onPressed: busy ? null : () => unawaited(generateToday(context, ref, force: false)),
          child: busy
              ? SizedBox.square(
                  dimension: 20,
                  child: SelectionContainer.disabled(
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      semanticsLabel: l10n.commonSubmitting,
                    ),
                  ),
                )
              : Text(l10n.todayGenerateButton),
        ),
        const SizedBox(height: AppSpacing.xl),
        const TodayDiagnosticCard(),
        if (footerWidget != null) ...[const SizedBox(height: AppSpacing.xl), footerWidget],
      ],
    );
  }
}

/// Generation answered `404 PLAN_NOT_FOUND`: "계획 만들기" creates a plan and generates again
/// (docs/02 SCR-TODAY "상태").
class TodayNoPlanState extends ConsumerWidget {
  const TodayNoPlanState({super.key, required this.busy});

  final bool busy;

  Future<void> _createPlan(BuildContext context, WidgetRef ref) async {
    final input = ref.read(todayInputProvider);
    final outcome = await ref
        .read(todayControllerProvider.notifier)
        .createPlanAndGenerate(availableMinutes: input.minutes, energyLevel: input.energy);
    if (context.mounted) {
      await presentTodayOutcome(context, ref, outcome);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    return EmptyState(
      icon: Icons.timeline,
      message: l10n.todayNoPlan,
      actionLabel: l10n.todayNoPlanButton,
      actionKey: const Key('today.noPlanButton'),
      onAction: busy ? null : () => unawaited(_createPlan(context, ref)),
    );
  }
}
