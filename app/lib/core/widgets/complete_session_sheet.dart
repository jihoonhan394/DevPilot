import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/core/widgets/form_modal.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/reading_feedback_chips.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// Sends the sheet values. Returns null when the sheet may close, otherwise the failure to show
/// inside it (the input stays). [readingFeedback] is null unless the reading chips were shown and
/// one was chosen. [understood] is null unless the sheet asked (see [CompleteSessionSheet.askUnderstanding]).
typedef CompleteSessionSubmit = Future<Object?> Function(
  int actualMinutes,
  String reflection,
  ReadingFeedback? readingFeedback, {
  bool? understood,
});

/// `CompleteSessionSheet` of SCR-TODAY and SCR-REVIEW-SESSION (docs/02 SCR-TODAY 완료 시트):
/// actual minutes with a 5-minute stepper (0..[maxMinutes]) and an optional one-line reflection.
Future<void> showCompleteSessionSheet(
  BuildContext context, {
  required String title,
  required int initialMinutes,
  required int maxMinutes,
  required CompleteSessionSubmit onSubmit,
  bool askReadingFeedback = false,
  bool askUnderstanding = false,
}) => showFormModal<void>(
  context,
  builder: (_) => CompleteSessionSheet(
    title: title,
    initialMinutes: initialMinutes,
    maxMinutes: maxMinutes,
    onSubmit: onSubmit,
    askReadingFeedback: askReadingFeedback,
    askUnderstanding: askUnderstanding,
  ),
);

class CompleteSessionSheet extends StatefulWidget {
  const CompleteSessionSheet({
    super.key,
    required this.title,
    required this.initialMinutes,
    required this.maxMinutes,
    required this.onSubmit,
    this.askReadingFeedback = false,
    this.askUnderstanding = false,
  });

  static const step = 5;

  /// Completing a READ_CODE task: the optional reading feedback chips (not for "여기까지 기록").
  final bool askReadingFeedback;

  /// "완료"를 누른 경우에만 묻는다: 지금 설명할 수 있나 (docs/02 SCR-TODAY 완료 시트).
  ///
  /// 시간을 썼다는 것과 알게 되었다는 것은 다르다. 답을 모른 채 "완료"가 되면 그 개념은 다시 나오지 않는다.
  final bool askUnderstanding;

  final String title;
  final int initialMinutes;
  final int maxMinutes;
  final CompleteSessionSubmit onSubmit;

  @override
  State<CompleteSessionSheet> createState() => _CompleteSessionSheetState();
}

class _CompleteSessionSheetState extends State<CompleteSessionSheet> {
  late int _minutes = widget.initialMinutes.clamp(0, widget.maxMinutes);
  final _reflection = TextEditingController();
  ReadingFeedback? _feedback;

  /// null이면 아직 고르지 않았다. 고르기 전에는 보낼 수 없다.
  bool? _understood;

  var _submitting = false;
  Object? _error;

  @override
  void dispose() {
    _reflection.dispose();
    super.dispose();
  }

  void _step(int direction) => setState(() {
    _minutes = (_minutes + direction * CompleteSessionSheet.step).clamp(0, widget.maxMinutes);
  });

  Future<void> _submit() async {
    setState(() {
      _submitting = true;
      _error = null;
    });
    final error = await widget.onSubmit(
      _minutes,
      _reflection.text,
      _feedback,
      understood: _understood,
    );
    if (!mounted) {
      return;
    }
    if (error == null) {
      Navigator.of(context).pop();
      return;
    }
    setState(() {
      _submitting = false;
      _error = error;
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final failure = _error;
    final apiFailure = failure is ApiException ? failure : null;
    final minutesError = apiFailure?.fieldMessage(
      'actualMinutes',
      fallback: l10n.errorValidationFailed,
    );
    final reflectionError = apiFailure?.fieldMessage(
      'selfReflection',
      fallback: l10n.errorValidationFailed,
    );
    final generalError = failure != null && minutesError == null && reflectionError == null
        ? messageFor(failure, l10n)
        : null;
    return SingleChildScrollView(
      padding: const EdgeInsets.all(AppSpacing.xl),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Semantics(header: true, child: Text(widget.title, style: textTheme.titleLarge)),
          const SizedBox(height: AppSpacing.lg),
          Text(l10n.todayCompleteSheetMinutes, style: textTheme.titleSmall),
          _MinutesStepper(
            minutes: _minutes,
            maxMinutes: widget.maxMinutes,
            enabled: !_submitting,
            onStep: _step,
          ),
          if (minutesError != null) InlineError(message: minutesError),
          const SizedBox(height: AppSpacing.lg),
          _ReflectionField(
            controller: _reflection,
            enabled: !_submitting,
            serverError: reflectionError,
          ),
          if (widget.askReadingFeedback) ...[
            const SizedBox(height: AppSpacing.lg),
            ReadingFeedbackChips(
              selected: _feedback,
              enabled: !_submitting,
              onChanged: (feedback) => setState(() => _feedback = feedback),
            ),
          ],
          if (widget.askUnderstanding) ...[
            const SizedBox(height: AppSpacing.lg),
            _UnderstandingChoice(
              understood: _understood,
              enabled: !_submitting,
              onChanged: (answer) => setState(() => _understood = answer),
            ),
          ],
          if (generalError != null) InlineError(message: generalError),
          const SizedBox(height: AppSpacing.xl),
          _SubmitButton(
            reflection: _reflection,
            submitting: _submitting,
            understood: _understood,
            answered: !widget.askUnderstanding || _understood != null,
            onSubmit: _submit,
          ),
        ],
      ),
    );
  }
}

/// "완료 기록", waiting while the reflection is too long or a request is in flight.
class _SubmitButton extends StatelessWidget {
  const _SubmitButton({
    required this.reflection,
    required this.submitting,
    required this.understood,
    required this.answered,
    required this.onSubmit,
  });

  final TextEditingController reflection;
  final bool submitting;

  /// null이면 묻지 않았거나 아직 고르지 않았다.
  final bool? understood;

  /// 물어본 경우 답을 골랐는가. 고르기 전에는 버튼이 꺼져 있다.
  final bool answered;

  final VoidCallback onSubmit;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return ValueListenableBuilder<TextEditingValue>(
      valueListenable: reflection,
      builder: (context, value, _) => FilledButton(
        key: const Key('completeSheet.submitButton'),
        onPressed: submitting || !answered || value.text.length > InputRules.selfReflectionMaxLength
            ? null
            : onSubmit,
        child: submitting
            ? SizedBox.square(
                dimension: 20,
                child: SelectionContainer.disabled(
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    semanticsLabel: l10n.commonSubmitting,
                  ),
                ),
              )
            : Text(
                understood == false
                    ? l10n.todayCompleteSheetSubmitNotYet
                    : l10n.todayCompleteSheetSubmit,
              ),
      ),
    );
  }
}

/// "지금 이 개념을 설명할 수 있나요?" — 시간을 썼다는 것과 알게 되었다는 것을 갈라 놓는다.
///
/// "아직 모르겠어요"를 고르면 과제가 `COMPLETED`가 아니라 `DEFERRED`가 되고, 다음 날 같은 skill이 이어진다
/// (`06` §5.4 3a `CONTINUATION`). 모른 채로 끝나 다시 안 나오는 일을 막는 장치다.
class _UnderstandingChoice extends StatelessWidget {
  const _UnderstandingChoice({
    required this.understood,
    required this.enabled,
    required this.onChanged,
  });

  final bool? understood;
  final bool enabled;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      key: const Key('completeSheet.understanding'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l10n.todayCompleteSheetUnderstandTitle, style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: AppSpacing.xs),
        Wrap(
          spacing: AppSpacing.sm,
          runSpacing: AppSpacing.xs,
          children: [
            ChoiceChip(
              key: const Key('completeSheet.understood'),
              label: Text(l10n.todayCompleteSheetUnderstandYes),
              selected: understood == true,
              onSelected: enabled ? (_) => onChanged(true) : null,
            ),
            ChoiceChip(
              key: const Key('completeSheet.notYet'),
              label: Text(l10n.todayCompleteSheetUnderstandNo),
              selected: understood == false,
              onSelected: enabled ? (_) => onChanged(false) : null,
            ),
          ],
        ),
        if (understood == false) ...[
          const SizedBox(height: AppSpacing.xs),
          Text(
            l10n.todayCompleteSheetNotYetNote,
            key: const Key('completeSheet.notYetNote'),
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ],
    );
  }
}

/// `[ − ]  35 분  [ + ]`: 5-minute steps within 0..[maxMinutes].
class _MinutesStepper extends StatelessWidget {
  const _MinutesStepper({
    required this.minutes,
    required this.maxMinutes,
    required this.enabled,
    required this.onStep,
  });

  final int minutes;
  final int maxMinutes;
  final bool enabled;
  final ValueChanged<int> onStep;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        IconButton.outlined(
          key: const Key('completeSheet.decreaseButton'),
          tooltip: l10n.todayCompleteSheetDecrease(CompleteSessionSheet.step),
          onPressed: enabled && minutes > 0 ? () => onStep(-1) : null,
          icon: const Icon(Icons.remove),
        ),
        SizedBox(
          width: 120,
          child: Semantics(
            liveRegion: true,
            child: Text(
              l10n.todayCompleteSheetMinutesValue(minutes),
              key: const Key('completeSheet.minutes'),
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.headlineSmall,
            ),
          ),
        ),
        IconButton.outlined(
          key: const Key('completeSheet.increaseButton'),
          tooltip: l10n.todayCompleteSheetIncrease(CompleteSessionSheet.step),
          onPressed: enabled && minutes < maxMinutes ? () => onStep(1) : null,
          icon: const Icon(Icons.add),
        ),
      ],
    );
  }
}

class _ReflectionField extends StatelessWidget {
  const _ReflectionField({
    required this.controller,
    required this.enabled,
    required this.serverError,
  });

  final TextEditingController controller;
  final bool enabled;
  final String? serverError;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return ValueListenableBuilder<TextEditingValue>(
      valueListenable: controller,
      builder: (context, value, _) {
        final tooLong = value.text.length > InputRules.selfReflectionMaxLength;
        return TextField(
          key: const Key('completeSheet.reflectionField'),
          controller: controller,
          enabled: enabled,
          minLines: 1,
          maxLines: 3,
          decoration: InputDecoration(
            labelText: l10n.todayCompleteSheetReflection,
            hintText: l10n.todayCompleteSheetReflectionHint,
            errorText: tooLong
                ? l10n.validationMaxLength(InputRules.selfReflectionMaxLength)
                : serverError,
          ),
        );
      },
    );
  }
}
