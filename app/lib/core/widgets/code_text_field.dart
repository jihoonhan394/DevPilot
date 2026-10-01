import 'package:devpilot_app/core/theme/app_theme.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// `CodeField` (docs/02 SCR-TRAINING-ATTEMPT, A-9): monospace, no line numbers, `Tab` types four
/// spaces and `Esc` leaves the field so the next `Tab` moves focus. Pasted tabs and spaces stay.
///
/// With [onSubmit], `Ctrl/Cmd+Enter` sends it (A-7: 여러 줄 입력은 Ctrl/Cmd+Enter 제출). Screens with
/// nothing to send — the lesson answer is never posted, the server does not grade it — leave it null
/// rather than binding the keys to something else.
class CodeTextField extends StatefulWidget {
  const CodeTextField({
    super.key,
    required this.controller,
    required this.label,
    this.enabled = true,
    this.errorText,
    this.helperText,
    this.onChanged,
    this.onSubmit,
  });

  final TextEditingController controller;
  final String label;
  final bool enabled;
  final String? errorText;
  final String? helperText;
  final ValueChanged<String>? onChanged;

  /// `Ctrl/Cmd+Enter`. Null means this field has nothing to send.
  final VoidCallback? onSubmit;

  @override
  State<CodeTextField> createState() => _CodeTextFieldState();
}

class _CodeTextFieldState extends State<CodeTextField> {
  static const _indent = '    ';
  final _focusNode = FocusNode();

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  void _insertIndent() {
    final value = widget.controller.value;
    final selection = value.selection;
    if (!selection.isValid || value.composing.isValid) {
      return;
    }
    final text = value.text.replaceRange(selection.start, selection.end, _indent);
    widget.controller.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: selection.start + _indent.length),
    );
    widget.onChanged?.call(text);
  }

  @override
  Widget build(BuildContext context) {
    final onSubmit = widget.onSubmit;
    return CallbackShortcuts(
      bindings: {
        const SingleActivator(LogicalKeyboardKey.tab): _insertIndent,
        const SingleActivator(LogicalKeyboardKey.escape): _focusNode.unfocus,
        if (onSubmit != null) ...{
          const SingleActivator(LogicalKeyboardKey.enter, control: true): onSubmit,
          const SingleActivator(LogicalKeyboardKey.enter, meta: true): onSubmit,
        },
      },
      child: TextField(
        controller: widget.controller,
        focusNode: _focusNode,
        enabled: widget.enabled,
        minLines: 6,
        maxLines: 16,
        keyboardType: TextInputType.multiline,
        style: AppTheme.codeTextStyle,
        onChanged: widget.onChanged,
        decoration: InputDecoration(
          labelText: widget.label,
          alignLabelWithHint: true,
          filled: true,
          fillColor: DevPilotColors.of(context).codeBackground,
          errorText: widget.errorText,
          helperText: widget.helperText,
          helperMaxLines: 2,
        ),
      ),
    );
  }
}
