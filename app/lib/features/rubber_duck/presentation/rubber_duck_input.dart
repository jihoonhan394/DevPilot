import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/core/widgets/ai_status_widgets.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// `ExplanationField` with its counter, masking note and buttons (docs/02 SCR-RUBBER-DUCK ①②).
/// 1~2000 characters, warned from 1900; a private key block is refused before sending.
/// `Ctrl/Cmd+Enter` sends unless an IME composition is open (A-7, A-13).
class RubberDuckInput extends StatelessWidget {
  const RubberDuckInput({
    super.key,
    required this.controller,
    required this.first,
    required this.aiStatus,
    required this.busy,
    required this.onSend,
    this.remainingTurns,
    this.onFinish,
    this.inputError,
    this.focusNode,
  });

  final TextEditingController controller;

  /// Focused by "계속 설명" of the stuck callout.
  final FocusNode? focusNode;

  /// ① before a session ("설명", "설명 보내기") or ② during the conversation ("내 답", "보내기").
  final bool first;
  final AiStatus aiStatus;
  final bool busy;
  final VoidCallback onSend;
  final int? remainingTurns;

  /// "정리하고 끝내기" (② only). It stays enabled while the AI is off (docs/05 §9.8).
  final VoidCallback? onFinish;
  final Object? inputError;

  static bool canSend(String text) =>
      text.trim().isNotEmpty &&
      text.length <= InputRules.rubberDuckExplanationMaxLength &&
      !InputRules.containsPrivateKey(text);

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return ValueListenableBuilder<TextEditingValue>(
      valueListenable: controller,
      builder: (context, value, _) {
        final sendEnabled = !busy && aiStatus.allowsAi && canSend(value.text);
        void send() {
          if (sendEnabled && !value.composing.isValid) {
            onSend();
          }
        }

        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _Field(
              controller: controller,
              focusNode: focusNode,
              first: first,
              enabled: !busy,
              onSubmit: send,
            ),
            _FieldNotes(text: value.text, remainingTurns: remainingTurns),
            if (InputRules.containsPrivateKey(value.text))
              InlineError(message: l10n.rubberDuckValidationPrivateKey),
            if (inputError != null) InlineError(message: _errorText(inputError!, l10n)),
            const SizedBox(height: AppSpacing.md),
            _Buttons(
              first: first,
              sendEnabled: sendEnabled,
              onSend: send,
              onFinish: busy ? null : onFinish,
            ),
            AiActionNote(status: aiStatus, extraReason: first ? l10n.rubberDuckAiOff : null),
          ],
        );
      },
    );
  }

  static String _errorText(Object error, AppLocalizations l10n) {
    if (error is ApiException) {
      switch (error.code) {
        case ApiErrorCode.contentTooLarge:
          return l10n.validationMaxLength(InputRules.rubberDuckExplanationMaxLength);
        case ApiErrorCode.secretDetectedBlocked:
          return l10n.rubberDuckValidationPrivateKey;
      }
    }
    return messageFor(error, l10n);
  }
}

class _Field extends StatelessWidget {
  const _Field({
    required this.controller,
    required this.focusNode,
    required this.first,
    required this.enabled,
    required this.onSubmit,
  });

  final TextEditingController controller;
  final FocusNode? focusNode;
  final bool first;
  final bool enabled;
  final VoidCallback onSubmit;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return CallbackShortcuts(
      bindings: {
        const SingleActivator(LogicalKeyboardKey.enter, control: true): onSubmit,
        const SingleActivator(LogicalKeyboardKey.enter, meta: true): onSubmit,
      },
      child: TextField(
        key: const Key('rubberDuck.input'),
        controller: controller,
        focusNode: focusNode,
        enabled: enabled,
        minLines: 3,
        maxLines: 8,
        decoration: InputDecoration(
          labelText: first ? l10n.rubberDuckInputFirst : l10n.rubberDuckInputAnswer,
          hintText: first ? l10n.rubberDuckInputFirstHint : l10n.rubberDuckInputAnswerHint,
          alignLabelWithHint: true,
        ),
      ),
    );
  }
}

/// Masking note, remaining turns and the character counter (warning color from 1900).
class _FieldNotes extends StatelessWidget {
  const _FieldNotes({required this.text, required this.remainingTurns});

  final String text;
  final int? remainingTurns;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final small = Theme.of(context).textTheme.bodySmall;
    final near = text.length >= InputRules.rubberDuckNearLimit;
    final over = text.length > InputRules.rubberDuckExplanationMaxLength;
    final remaining = remainingTurns;
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.xs),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: Text(l10n.rubberDuckMaskingNote, style: small)),
              Text(
                l10n.commonCharacterCount(text.length, InputRules.rubberDuckExplanationMaxLength),
                key: const Key('rubberDuck.counter'),
                style: near ? small?.copyWith(color: DevPilotColors.of(context).warning) : small,
              ),
            ],
          ),
          if (over)
            InlineError(
              message: l10n.validationMaxLength(InputRules.rubberDuckExplanationMaxLength),
            )
          else if (near)
            Text(l10n.rubberDuckNearLimit, style: small),
          if (remaining != null) Text(l10n.rubberDuckRemaining(remaining), style: small),
        ],
      ),
    );
  }
}

class _Buttons extends StatelessWidget {
  const _Buttons({
    required this.first,
    required this.sendEnabled,
    required this.onSend,
    required this.onFinish,
  });

  final bool first;
  final bool sendEnabled;
  final VoidCallback onSend;
  final VoidCallback? onFinish;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final sendButton = FilledButton(
      key: const Key('rubberDuck.sendButton'),
      onPressed: sendEnabled ? onSend : null,
      child: Text(first ? l10n.rubberDuckSendFirst : l10n.rubberDuckSend),
    );
    if (first) {
      return sendButton;
    }
    return Row(
      children: [
        TextButton(
          key: const Key('rubberDuck.finishButton'),
          onPressed: onFinish,
          child: Text(l10n.rubberDuckFinish),
        ),
        const Spacer(),
        sendButton,
      ],
    );
  }
}
