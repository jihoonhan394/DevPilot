import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/confirm_dialog.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/features/training/presentation/attempt_controller.dart';
import 'package:devpilot_app/features/training/presentation/attempt_input_guard.dart';
import 'package:devpilot_app/features/training/presentation/attempt_state.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// ① "먼저 어떻게 풀지 설명해 주세요" (docs/02 SCR-TRAINING-ATTEMPT). Once saved or skipped it is a
/// read-only line; the saved text cannot be edited.
class SelfExplanationSection extends ConsumerWidget {
  const SelfExplanationSection({super.key, required this.data});

  final AttemptScreenData data;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final attempt = data.attempt;
    if (attempt.explanationRecorded) {
      return ExpansionTile(
        key: const Key('attempt.explanationDone'),
        tilePadding: EdgeInsets.zero,
        leading: const Icon(Icons.check_circle_outline),
        title: Text(l10n.trainingSelfExplainDone),
        children: [
          Align(
            alignment: Alignment.centerLeft,
            child: SelectionArea(
              child: Text(attempt.selfExplanation ?? l10n.trainingSelfExplainSkipped),
            ),
          ),
        ],
      );
    }
    if (data.closed) {
      return const SizedBox.shrink();
    }
    return _ExplanationForm(data: data);
  }
}

class _ExplanationForm extends ConsumerStatefulWidget {
  const _ExplanationForm({required this.data});

  final AttemptScreenData data;

  @override
  ConsumerState<_ExplanationForm> createState() => _ExplanationFormState();
}

class _ExplanationFormState extends ConsumerState<_ExplanationForm> {
  final _text = TextEditingController();
  Object? _error;

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  AttemptController get _controller =>
      ref.read(attemptControllerProvider(widget.data.attempt.id).notifier);

  Future<void> _save({required bool skip}) async {
    final l10n = AppLocalizations.of(context);
    if (skip &&
        !await showConfirmDialog(
          context,
          title: l10n.trainingSelfExplainSkip,
          body: l10n.trainingSelfExplainSkipConfirm,
          confirmLabel: l10n.trainingSelfExplainSkip,
          cancelLabel: l10n.commonCancel,
          confirmKey: const Key('attempt.skipConfirmButton'),
        )) {
      return;
    }
    setState(() => _error = null);
    final result = await _controller.recordExplanation(skip ? null : _text.text);
    if (!mounted) {
      return;
    }
    if (result case AttemptActionFailed(:final error)) {
      final inline =
          error is ApiException &&
          (error.code == ApiErrorCode.secretDetectedBlocked ||
              error.code == ApiErrorCode.validationFailed);
      if (inline) {
        setState(() => _error = error);
      } else {
        await presentActionError(context, ref, error);
      }
      return;
    }
    ref.read(attemptInputGuardProvider.notifier).mark(AttemptInput.explanation, dirty: false);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final error = _error;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.trainingSelfExplainTitle),
        const SizedBox(height: AppSpacing.xs),
        Text(l10n.trainingSelfExplainHelp, style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: AppSpacing.sm),
        ValueListenableBuilder<TextEditingValue>(
          valueListenable: _text,
          builder: (context, value, _) => _ExplanationField(
            controller: _text,
            enabled: !widget.data.busy,
            onChanged: (text) => ref
                .read(attemptInputGuardProvider.notifier)
                .mark(AttemptInput.explanation, dirty: text.isNotEmpty),
          ),
        ),
        if (error != null) InlineError(message: messageFor(error, l10n)),
        Text(l10n.trainingSelfExplainFinal, style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: AppSpacing.sm),
        ValueListenableBuilder<TextEditingValue>(
          valueListenable: _text,
          builder: (context, value, _) {
            final valid = InputRules.isRequiredText(
              value.text,
              InputRules.selfExplanationMaxLength,
            );
            final enabled = !widget.data.busy;
            return Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  key: const Key('attempt.explanationSkipButton'),
                  onPressed: enabled ? () => _save(skip: true) : null,
                  child: Text(l10n.trainingSelfExplainSkip),
                ),
                const SizedBox(width: AppSpacing.sm),
                FilledButton(
                  key: const Key('attempt.explanationSaveButton'),
                  onPressed: enabled && valid ? () => _save(skip: false) : null,
                  child: Text(l10n.trainingSelfExplainSave),
                ),
              ],
            );
          },
        ),
      ],
    );
  }
}

class _ExplanationField extends StatelessWidget {
  const _ExplanationField({
    required this.controller,
    required this.enabled,
    required this.onChanged,
  });

  final TextEditingController controller;
  final bool enabled;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tooLong = controller.text.length > InputRules.selfExplanationMaxLength;
    return TextField(
      key: const Key('attempt.explanationField'),
      controller: controller,
      enabled: enabled,
      minLines: 4,
      maxLines: 10,
      onChanged: onChanged,
      decoration: InputDecoration(
        labelText: l10n.trainingSelfExplainLabel,
        alignLabelWithHint: true,
        counterText: l10n.commonCharacterCount(
          controller.text.length,
          InputRules.selfExplanationMaxLength,
        ),
        errorText: tooLong ? l10n.validationMaxLength(InputRules.selfExplanationMaxLength) : null,
      ),
    );
  }
}
