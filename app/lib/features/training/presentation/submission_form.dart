import 'dart:async';

import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/core/widgets/ai_provider_notice.dart';
import 'package:devpilot_app/core/widgets/ai_status_widgets.dart';
import 'package:devpilot_app/core/widgets/code_text_field.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/labeled_dropdown.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/training/domain/submission_rules.dart';
import 'package:devpilot_app/features/training/presentation/attempt_actions.dart';
import 'package:devpilot_app/features/training/presentation/attempt_controller.dart';
import 'package:devpilot_app/features/training/presentation/attempt_draft.dart';
import 'package:devpilot_app/features/training/presentation/attempt_input_guard.dart';
import 'package:devpilot_app/features/training/presentation/attempt_state.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// `SubmissionForm`: answer text, optional code with its language, and "제출" (docs/02 ③). The
/// input is kept as a draft for the attempt (1 s debounce) until a submission succeeds.
class SubmissionForm extends ConsumerStatefulWidget {
  const SubmissionForm({super.key, required this.data});

  final AttemptScreenData data;

  @override
  ConsumerState<SubmissionForm> createState() => _SubmissionFormState();
}

class _SubmissionFormState extends ConsumerState<SubmissionForm> {
  static const _draftDelay = Duration(seconds: 1);
  final _answer = TextEditingController();
  final _code = TextEditingController();
  CodeLanguage? _language = CodeLanguage.java;
  Timer? _draftTimer;
  Object? _error;

  String get _attemptId => widget.data.attempt.id;

  @override
  void initState() {
    super.initState();
    final draft = AttemptDraft.read(ref.read(keyValueStoreProvider), _attemptId);
    _answer.text = draft.answerText;
    _code.text = draft.code;
    _language = draft.language ?? CodeLanguage.java;
  }

  @override
  void dispose() {
    _draftTimer?.cancel();
    _answer.dispose();
    _code.dispose();
    super.dispose();
  }

  void _changed() {
    setState(() {});
    ref
        .read(attemptInputGuardProvider.notifier)
        .mark(AttemptInput.submission, dirty: _answer.text.isNotEmpty || _code.text.isNotEmpty);
    _draftTimer?.cancel();
    _draftTimer = Timer(_draftDelay, () {
      AttemptDraft(
        answerText: _answer.text,
        code: _code.text,
        language: _language,
      ).write(ref.read(keyValueStoreProvider), _attemptId);
    });
  }

  Future<void> _submit() async {
    if (!await confirmAiProviderNotice(context) || !mounted) {
      return;
    }
    setState(() => _error = null);
    final request = SubmissionRules.requestOf(
      answerText: _answer.text,
      code: _code.text,
      language: _language,
    );
    final result = await ref.read(attemptControllerProvider(_attemptId).notifier).submit(request);
    if (!mounted) {
      return;
    }
    if (result case AttemptActionFailed(:final error)) {
      if (error is ApiException && _inlineCodes.contains(error.code)) {
        setState(() => _error = error);
      } else {
        await presentAttemptResult(context, ref, result);
      }
      return;
    }
    _draftTimer?.cancel();
    AttemptDraft.clear(ref.read(keyValueStoreProvider), _attemptId);
    ref.read(attemptInputGuardProvider.notifier).mark(AttemptInput.submission, dirty: false);
  }

  static const _inlineCodes = {
    ApiErrorCode.validationFailed,
    ApiErrorCode.contentTooLarge,
    ApiErrorCode.requestTooLarge,
    ApiErrorCode.secretDetectedBlocked,
  };

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final aiStatus = ref.watch(aiStatusProvider);
    final problem = SubmissionRules.problemOf(
      answerText: _answer.text,
      code: _code.text,
      language: _language,
    );
    final error = _error;
    final canSend = aiStatus.allowsAi && !widget.data.busy && problem == null;
    return Column(
      key: const Key('attempt.submissionForm'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _AnswerField(
          controller: _answer,
          tooLong: problem == SubmissionProblem.answerTooLong,
          onChanged: _changed,
        ),
        const SizedBox(height: AppSpacing.md),
        LabeledDropdown<CodeLanguage>(
          key: const Key('attempt.languageDropdown'),
          label: l10n.trainingSubmitLanguage,
          value: _language ?? CodeLanguage.java,
          items: CodeLanguage.known,
          itemLabel: (language) => language.wireName,
          onChanged: (language) {
            _language = language;
            _changed();
          },
        ),
        const SizedBox(height: AppSpacing.sm),
        CodeTextField(
          key: const Key('attempt.codeField'),
          controller: _code,
          label: l10n.trainingSubmitCode,
          helperText: l10n.commonCodeFieldTabHint,
          errorText: problem == SubmissionProblem.codeTooLarge ? l10n.errorContentTooLarge : null,
          onChanged: (_) => _changed(),
        ),
        _ProblemLine(problem: problem),
        if (error != null) InlineError(message: messageFor(error, l10n)),
        const SizedBox(height: AppSpacing.md),
        FilledButton(
          key: const Key('attempt.submitButton'),
          onPressed: canSend ? _submit : null,
          child: Text(l10n.trainingSubmitButton),
        ),
        if (!aiStatus.allowsAi)
          Text(l10n.trainingDetailAiSubmitNote, key: const Key('attempt.aiSubmitNote')),
        BudgetWarningNote(status: aiStatus),
      ],
    );
  }
}

class _AnswerField extends StatelessWidget {
  const _AnswerField({required this.controller, required this.tooLong, required this.onChanged});

  final TextEditingController controller;
  final bool tooLong;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return TextField(
      key: const Key('attempt.answerField'),
      controller: controller,
      minLines: 3,
      maxLines: 10,
      onChanged: (_) => onChanged(),
      decoration: InputDecoration(
        labelText: l10n.trainingSubmitAnswer,
        alignLabelWithHint: true,
        errorText: tooLong ? l10n.validationMaxLength(InputRules.submissionAnswerMaxLength) : null,
      ),
    );
  }
}

/// The client check that keeps "제출" disabled, as a sentence (empty input is not an error yet).
class _ProblemLine extends StatelessWidget {
  const _ProblemLine({required this.problem});

  final SubmissionProblem? problem;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final message = switch (problem) {
      SubmissionProblem.languageMissing => l10n.trainingSubmitValidationLanguage,
      SubmissionProblem.privateKey => l10n.errorSecretDetectedBlocked,
      SubmissionProblem.empty => l10n.trainingSubmitValidationEmpty,
      _ => null,
    };
    if (message == null) {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.xs),
      child: Text(message, style: Theme.of(context).textTheme.bodySmall),
    );
  }
}
