import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/platform/page_reloader.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/report_info.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Shows a failed save or send the way docs/02 §5.1 prescribes for its `code`.
///
/// Screens handle their own flow codes (field errors, `CONCURRENT_MODIFICATION`, …) first and
/// call this for the rest. Input is never cleared here (docs/02 §3.3 "Error (행동)").
Future<void> presentActionError(BuildContext context, WidgetRef ref, Object error) async {
  final l10n = AppLocalizations.of(context);
  if (error is! ApiException) {
    showToast(context, l10n.errorGeneric);
    return;
  }
  switch (error.code) {
    case ApiErrorCode.unknownEnumValue ||
        ApiErrorCode.malformedRequest ||
        ApiErrorCode.idempotencyKeyRequired:
      await _showReloadDialog(context, ref, error);
    case ApiErrorCode.internalError ||
        ApiErrorCode.forbidden ||
        ApiErrorCode.aiDailyLimitExceeded ||
        ApiErrorCode.aiMonthlyBudgetExceeded:
      // The AI limits are a dialog: the user has to know the feature is out for a while
      // (docs/02 §5.1). The profile is re-read by the API layer.
      await _showErrorDialog(context, error);
    case ApiErrorCode.authenticationRequired || ApiErrorCode.userNotAllowed:
      // The router already leaves the screen (docs/02 §2.4); nothing to show here.
      return;
    default:
      showToast(context, messageFor(error, l10n));
  }
}

Future<void> _showErrorDialog(BuildContext context, ApiException error) {
  final l10n = AppLocalizations.of(context);
  return showDialog<void>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      key: const Key('common.errorDialog'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(messageFor(error, l10n)),
            ReportInfo(failure: error),
          ],
        ),
      ),
      actions: [
        TextButton(
          key: const Key('common.errorDialogCloseButton'),
          onPressed: () => Navigator.of(dialogContext).pop(),
          child: Text(l10n.commonClose),
        ),
      ],
    ),
  );
}

Future<void> _showReloadDialog(BuildContext context, WidgetRef ref, ApiException error) {
  final l10n = AppLocalizations.of(context);
  final reload = ref.read(pageReloaderProvider);
  return showDialog<void>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      key: const Key('common.reloadDialog'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(messageFor(error, l10n)),
            ReportInfo(failure: error),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(dialogContext).pop(),
          child: Text(l10n.commonClose),
        ),
        TextButton(
          key: const Key('common.reloadButton'),
          onPressed: reload,
          child: Text(l10n.commonReload),
        ),
      ],
    ),
  );
}
