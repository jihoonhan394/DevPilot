import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// "직접 입력" for minutes: a number field limited to [minMinutes]~[maxMinutes] (docs/02 §3.2).
/// Study time uses 0~720 (onboarding step 2), Today's available time 5~720.
/// Returns the minutes, or null when cancelled.
Future<int?> showMinutesInputDialog(
  BuildContext context, {
  required String title,
  required int initialMinutes,
  int minMinutes = 0,
  int maxMinutes = InputRules.studyMinutesMax,
}) => showDialog<int>(
  context: context,
  builder: (_) => _MinutesInputDialog(
    title: title,
    initialMinutes: initialMinutes,
    minMinutes: minMinutes,
    maxMinutes: maxMinutes,
  ),
);

class _MinutesInputDialog extends StatefulWidget {
  const _MinutesInputDialog({
    required this.title,
    required this.initialMinutes,
    required this.minMinutes,
    required this.maxMinutes,
  });

  final String title;
  final int initialMinutes;
  final int minMinutes;
  final int maxMinutes;

  @override
  State<_MinutesInputDialog> createState() => _MinutesInputDialogState();
}

class _MinutesInputDialogState extends State<_MinutesInputDialog> {
  late final _controller = TextEditingController(text: '${widget.initialMinutes}');

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  int? get _minutes {
    final value = int.tryParse(_controller.text);
    return value != null && value >= widget.minMinutes && value <= widget.maxMinutes ? value : null;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return ValueListenableBuilder<TextEditingValue>(
      valueListenable: _controller,
      builder: (context, _, _) {
        final minutes = _minutes;
        return AlertDialog(
          title: Text(widget.title),
          content: TextField(
            key: const Key('common.minutesField'),
            controller: _controller,
            autofocus: true,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            decoration: InputDecoration(
              labelText: l10n.commonMinutesLabel,
              errorText: minutes == null
                  ? l10n.validationRange(widget.minMinutes, widget.maxMinutes)
                  : null,
            ),
            onSubmitted: (_) {
              if (minutes != null) {
                Navigator.of(context).pop(minutes);
              }
            },
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: Text(l10n.commonCancel),
            ),
            TextButton(
              key: const Key('common.minutesConfirmButton'),
              onPressed: minutes == null ? null : () => Navigator.of(context).pop(minutes),
              child: Text(l10n.commonApply),
            ),
          ],
        );
      },
    );
  }
}
