import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// Two-button dialog: cancel on the left, the named action on the right (docs/02 §6.7).
///
/// Returns true only when the action button was pressed. Destructive actions use the danger color.
Future<bool> showConfirmDialog(
  BuildContext context, {
  required String title,
  required String body,
  required String confirmLabel,
  required String cancelLabel,
  bool destructive = false,
  Key? confirmKey,
}) async {
  final confirmed = await showDialog<bool>(
    context: context,
    builder: (dialogContext) {
      final danger = DevPilotColors.of(dialogContext).danger;
      return AlertDialog(
        title: Text(title),
        content: Text(body),
        actions: [
          TextButton(
            key: const Key('common.dialogCancelButton'),
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text(cancelLabel),
          ),
          TextButton(
            key: confirmKey ?? const Key('common.dialogConfirmButton'),
            style: destructive ? TextButton.styleFrom(foregroundColor: danger) : null,
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text(confirmLabel),
          ),
        ],
      );
    },
  );
  return confirmed ?? false;
}

/// "저장하지 않은 내용이 있어요" (docs/02 §6.8). Returns true when the user chose to leave.
Future<bool> confirmDiscardChanges(BuildContext context) {
  final l10n = AppLocalizations.of(context);
  return showConfirmDialog(
    context,
    title: l10n.commonUnsavedTitle,
    body: l10n.commonUnsavedBody,
    cancelLabel: l10n.commonUnsavedStay,
    confirmLabel: l10n.commonUnsavedLeave,
    confirmKey: const Key('common.leaveButton'),
  );
}
