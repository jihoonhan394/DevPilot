import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/features/plan/presentation/replan_controller.dart';
import 'package:devpilot_app/features/plan/presentation/replan_preview_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

// SCR-REPLAN flows: edit → preview → check suggestions → save (docs/02 §4.8).

/// "변경 미리보기".
Future<void> previewReplan(BuildContext context, WidgetRef ref) async {
  final outcome = await ref.read(replanPreviewControllerProvider.notifier).preview();
  if (context.mounted) {
    await _presentPreviewOutcome(context, ref, outcome);
  }
}

/// "선택 반영해 다시 계산".
Future<void> recalculateReplan(BuildContext context, WidgetRef ref) async {
  final outcome = await ref.read(replanPreviewControllerProvider.notifier).recalculate();
  if (context.mounted) {
    await _presentPreviewOutcome(context, ref, outcome);
  }
}

Future<void> _presentPreviewOutcome(
  BuildContext context,
  WidgetRef ref,
  ReplanPreviewOutcome outcome,
) async {
  switch (outcome) {
    case ReplanPreviewShown() || ReplanPreviewRejected():
      return;
    case ReplanPreviewConflict():
      await showReplanConflict(context, ref);
    case ReplanPreviewFailed(:final error):
      await presentActionError(context, ref, error);
  }
}

/// "새 버전(v{n})으로 저장" with the checked suggestions.
Future<void> saveReplan(BuildContext context, WidgetRef ref) async {
  final l10n = AppLocalizations.of(context);
  final preview = ref.read(replanPreviewControllerProvider.notifier);
  final outcome = await ref
      .read(replanControllerProvider.notifier)
      .save(selection: ref.read(replanPreviewControllerProvider).selection);
  if (!context.mounted) {
    return;
  }
  switch (outcome) {
    case ReplanSaved(:final planVersion):
      showToast(context, l10n.replanSaved(planVersion));
      context.go(AppRoutes.plan);
    case ReplanConflict():
      await showReplanConflict(context, ref);
    case ReplanSaveFailed(:final error):
      if (error is ApiException &&
          error.code == ApiErrorCode.validationFailed &&
          error.fieldErrors.isNotEmpty) {
        // Suggestion rows show their error in place; edit fields need the edit step.
        if (!await preview.showSaveErrors(error)) {
          preview.backToEdit();
        }
        return;
      }
      await presentActionError(context, ref, error);
  }
}

/// `CONCURRENT_MODIFICATION` / `PLAN_NOT_ACTIVE`: the edit is dropped and the latest plan is read
/// (docs/02 SCR-REPLAN, AC-24).
Future<void> showReplanConflict(BuildContext context, WidgetRef ref) async {
  final l10n = AppLocalizations.of(context);
  await showDialog<void>(
    context: context,
    barrierDismissible: false,
    builder: (dialogContext) => AlertDialog(
      title: Text(l10n.replanConflictTitle),
      content: Text(l10n.replanConflictBody),
      actions: [
        TextButton(
          key: const Key('replan.conflictReloadButton'),
          onPressed: () => Navigator.of(dialogContext).pop(),
          child: Text(l10n.replanConflictReload),
        ),
      ],
    ),
  );
  ref.read(replanControllerProvider.notifier).reloadLatest();
}
