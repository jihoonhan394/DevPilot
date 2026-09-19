import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/features/plan/presentation/plan_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Toast or dialog for a finished plan action (docs/02 SCR-PLAN "행동·검증"): a conflict re-reads
/// the plan and says so with `plan.reloaded`; other failures follow docs/02 §5.1.
Future<void> showPlanActionOutcome(
  BuildContext context,
  WidgetRef ref,
  PlanActionOutcome outcome,
) async {
  switch (outcome) {
    case PlanActionSaved():
      return;
    case PlanActionReloaded():
      showToast(context, AppLocalizations.of(context).planReloaded);
    case PlanActionFailed(:final error):
      await presentActionError(context, ref, error);
  }
}
