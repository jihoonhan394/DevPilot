import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// A text button that copies [value] to the clipboard and confirms with "복사했어요".
class CopyTextButton extends StatelessWidget {
  const CopyTextButton({
    super.key,
    required this.value,
    required this.label,
    this.semanticsLabel,
  });

  final String value;
  final String label;

  /// A distinct name when several copy buttons share a screen.
  final String? semanticsLabel;

  @override
  Widget build(BuildContext context) {
    return TextButton.icon(
      onPressed: () async {
        await Clipboard.setData(ClipboardData(text: value));
        if (context.mounted) {
          showToast(context, AppLocalizations.of(context).commonErrorCopied);
        }
      },
      icon: const Icon(Icons.copy, size: 16),
      label: Text(label, semanticsLabel: semanticsLabel),
    );
  }
}
