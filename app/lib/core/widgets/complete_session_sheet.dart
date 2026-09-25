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
/// [redoWithoutAi] is null unless the sheet asked (see [CompleteSessionSheet.askRedoAnswer]).
/// [explainedToPerson] and [explainedNote] are null unless the learner ticked the box — not
/// ticking it means "I did not say this to anyone", which is a fine answer, not a missing one.
typedef CompleteSessionSubmit = Future<Object?> Function(
  int actualMinutes,
  String reflection,
  ReadingFeedback? readingFeedback, {
  bool? understood,
  bool? redoWithoutAi,
  bool? explainedToPerson,
  String? explainedNote,
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
  bool askRedoAnswer = false,
  bool askExplained = false,
}) => showFormModal<void>(
  context,
  builder: (_) => CompleteSessionSheet(
    title: title,
    initialMinutes: initialMinutes,
    maxMinutes: maxMinutes,
    onSubmit: onSubmit,
    askReadingFeedback: askReadingFeedback,
    askUnderstanding: askUnderstanding,
    askRedoAnswer: askRedoAnswer,
    askExplained: askExplained,
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
    this.askRedoAnswer = false,
    this.askExplained = false,
  });

  static const step = 5;

  /// Completing a READ_CODE task: the optional reading feedback chips (not for "여기까지 기록").
  final bool askReadingFeedback;

  /// "완료"를 누른 경우에만 묻는다: 지금 설명할 수 있나 (docs/02 SCR-TODAY 완료 시트).
  ///
  /// 시간을 썼다는 것과 알게 되었다는 것은 다르다. 답을 모른 채 "완료"가 되면 그 개념은 다시 나오지 않는다.
  final bool askUnderstanding;

  /// 재현 과제를 끝낼 때만 묻는다: AI 도움 없이 끝냈나 (docs/06 §5.10 RE-6).
  ///
  /// 이 과제의 결과 그 자체라 고르기 전에는 보낼 수 없다. "혼자 했다"만 독립 구현 증거가 되고(RE-8), "도움을 받았다"는
  /// 어디서 막혔는지 묻는 복습 카드가 된다(RE-7) — 둘 중 어느 쪽도 벌이 아니다.
  final bool askRedoAnswer;

  /// EXPLAIN·READ_CODE 과제를 끝낼 때만 묻는다: 사람에게 설명했나 (docs/02 SCR-TODAY 완료 시트).
  ///
  /// **선택 사항이다.** 켜면 그 기술의 설명하기 학습 단계가 채워지고(docs/06 §5.11), 켜지 않아도 과제는 그대로 완료된다 —
  /// 말한 적이 없으면 켜지 않는 것이 맞다.
  final bool askExplained;

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

  /// 〃 (재현 과제의 답, RE-6).
  bool? _redoWithoutAi;

  /// 사람에게 설명했다고 켰나. 켜지 않으면 메모와 함께 아예 보내지 않는다.
  var _explained = false;
  final _explainedNote = TextEditingController();

  var _submitting = false;
  Object? _error;

  @override
  void dispose() {
    _reflection.dispose();
    _explainedNote.dispose();
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
      redoWithoutAi: _redoWithoutAi,
      // 켜지 않았으면 두 값을 모두 빼고 보낸다 — 켜지 않고 메모만 보내면 서버가 400이다 (docs/05 §8.4)
      explainedToPerson: widget.askExplained && _explained ? true : null,
      explainedNote: widget.askExplained && _explained && _explainedNote.text.isNotEmpty
          ? _explainedNote.text
          : null,
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
          if (widget.askRedoAnswer) ...[
            const SizedBox(height: AppSpacing.lg),
            _RedoAnswerChoice(
              withoutAi: _redoWithoutAi,
              enabled: !_submitting,
              onChanged: (answer) => setState(() => _redoWithoutAi = answer),
            ),
          ],
          if (widget.askExplained) ...[
            const SizedBox(height: AppSpacing.lg),
            _ExplainedField(
              explained: _explained,
              note: _explainedNote,
              enabled: !_submitting,
              secretBlocked: apiFailure?.code == ApiErrorCode.secretDetectedBlocked,
              onChanged: (value) => setState(() => _explained = value),
            ),
          ],
          if (generalError != null) InlineError(message: generalError),
          const SizedBox(height: AppSpacing.xl),
          _SubmitButton(
            reflection: _reflection,
            submitting: _submitting,
            understood: _understood,
            answered:
                (!widget.askUnderstanding || _understood != null) &&
                (!widget.askRedoAnswer || _redoWithoutAi != null),
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

/// "AI 도움 없이 끝냈나요?" — 재현 과제의 결과다 (docs/06 §5.10 RE-6).
///
/// 어느 쪽을 골라도 과제는 완료된다. 다른 점은 그 뒤다 — "네"는 독립 구현 증거가 되고(RE-8), "아니요"는 어디서 막혔는지
/// 적어 두는 복습 카드가 된다(RE-7).
class _RedoAnswerChoice extends StatelessWidget {
  const _RedoAnswerChoice({
    required this.withoutAi,
    required this.enabled,
    required this.onChanged,
  });

  final bool? withoutAi;
  final bool enabled;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      key: const Key('completeSheet.redoAnswer'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l10n.todayCompleteSheetRedoTitle, style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: AppSpacing.xs),
        Wrap(
          spacing: AppSpacing.sm,
          runSpacing: AppSpacing.xs,
          children: [
            ChoiceChip(
              key: const Key('completeSheet.redoAlone'),
              label: Text(l10n.todayCompleteSheetRedoAlone),
              selected: withoutAi == true,
              onSelected: enabled ? (_) => onChanged(true) : null,
            ),
            ChoiceChip(
              key: const Key('completeSheet.redoHelped'),
              label: Text(l10n.todayCompleteSheetRedoHelped),
              selected: withoutAi == false,
              onSelected: enabled ? (_) => onChanged(false) : null,
            ),
          ],
        ),
        if (withoutAi == false) ...[
          const SizedBox(height: AppSpacing.xs),
          Text(
            l10n.todayCompleteSheetRedoHelpedNote,
            key: const Key('completeSheet.redoHelpedNote'),
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

/// "다른 사람에게 설명했어요" 체크와, 켰을 때만 펼쳐지는 한 줄 메모 (docs/02 SCR-TODAY 완료 시트).
///
/// 체크를 켜야 메모가 나오는 이유: 서버는 `explainedToPerson` 없이 메모만 오면 400을 준다(docs/05 §8.4). 화면에서 먼저 막는다.
class _ExplainedField extends StatelessWidget {
  const _ExplainedField({
    required this.explained,
    required this.note,
    required this.enabled,
    required this.secretBlocked,
    required this.onChanged,
  });

  final bool explained;
  final TextEditingController note;
  final bool enabled;
  final bool secretBlocked;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        CheckboxListTile(
          key: const Key('completeSheet.explainedCheck'),
          value: explained,
          onChanged: enabled ? (value) => onChanged(value ?? false) : null,
          contentPadding: EdgeInsets.zero,
          controlAffinity: ListTileControlAffinity.leading,
          title: Text(l10n.todayExplainedToPerson),
          subtitle: Text(l10n.todayExplainedToPersonHelp, style: textTheme.bodySmall),
        ),
        if (explained) ...[
          TextField(
            key: const Key('completeSheet.explainedNoteField'),
            controller: note,
            enabled: enabled,
            minLines: 1,
            maxLines: 2,
            // 500자에서 입력이 멈추고 남은 글자 수가 보인다 (docs/02 SCR-TODAY 완료 시트 그림)
            maxLength: InputRules.explainedNoteMaxLength,
            decoration: InputDecoration(
              labelText: l10n.todayExplainedNote,
              hintText: l10n.todayExplainedNoteHint,
              helperText: l10n.todayExplainedNoteMaskingNote,
            ),
          ),
          if (secretBlocked) InlineError(message: l10n.projectsSecretBlocked),
        ],
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
