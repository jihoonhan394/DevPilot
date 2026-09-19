import 'dart:async';

import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/features/review/data/review_item_models.dart';
import 'package:devpilot_app/features/review/presentation/review_item_edit_controller.dart';
import 'package:devpilot_app/features/review/presentation/review_item_edit_fields.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-REVIEW-ITEM-EDIT: a manual card (skill, type, prompt, expected answer, 1~6 points) or the
/// prompt and expected answer of an existing card (docs/02 §3.6, §4.4).
class ReviewItemEditScreen extends ConsumerStatefulWidget {
  const ReviewItemEditScreen({super.key, this.item});

  /// The card to edit, passed as `extra` by SCR-REVIEW-ITEMS; null creates a card.
  final ReviewItemView? item;

  @override
  ConsumerState<ReviewItemEditScreen> createState() => _ReviewItemEditScreenState();
}

class _ReviewItemEditScreenState extends ConsumerState<ReviewItemEditScreen> {
  late final _form = ref.read(reviewItemEditControllerProvider(widget.item)).form;
  late final _prompt = TextEditingController(text: _form.prompt);
  late final _expected = TextEditingController(text: _form.expectedAnswer);
  late final List<TextEditingController> _rubric = [
    for (final point in _form.rubric) TextEditingController(text: point),
  ];

  ReviewItemEditController get _controller =>
      ref.read(reviewItemEditControllerProvider(widget.item).notifier);

  @override
  void initState() {
    super.initState();
    // A form left with "나가기" earlier must not ask again here.
    Future.microtask(() => ref.read(reviewItemEditDirtyProvider.notifier).set(dirty: false));
    _prompt.addListener(_sync);
    _expected.addListener(_sync);
    for (final point in _rubric) {
      point.addListener(_sync);
    }
  }

  @override
  void dispose() {
    for (final controller in [_prompt, _expected, ..._rubric]) {
      controller.dispose();
    }
    super.dispose();
  }

  void _sync() {
    _controller.edit(
      (form) => form.copyWith(
        prompt: _prompt.text,
        expectedAnswer: _expected.text,
        rubric: [for (final point in _rubric) point.text],
      ),
    );
    final dirty = ref.read(reviewItemEditControllerProvider(widget.item)).form.isDirty;
    ref.read(reviewItemEditDirtyProvider.notifier).set(dirty: dirty);
  }

  void _addPoint() {
    setState(() => _rubric.add(TextEditingController()..addListener(_sync)));
    _sync();
  }

  void _removePoint(int index) {
    setState(() => _rubric.removeAt(index).dispose());
    _sync();
  }

  Future<void> _save() async {
    final l10n = AppLocalizations.of(context);
    final outcome = await _controller.save();
    if (!mounted) {
      return;
    }
    switch (outcome) {
      case ReviewItemSaved(:final existing):
        ref.read(reviewItemEditDirtyProvider.notifier).set(dirty: false);
        showToast(
          context,
          widget.item != null
              ? l10n.reviewEditSaved
              : (existing ? l10n.reviewEditExists : l10n.reviewEditCreated),
        );
        context.go(AppRoutes.reviewItems);
      case ReviewItemStale():
        ref.read(reviewItemEditDirtyProvider.notifier).set(dirty: false);
        showToast(context, l10n.errorConcurrentModification);
        context.go(AppRoutes.reviewItems);
      case ReviewItemSaveFailed(:final error):
        if (!_isInline(error)) {
          await presentActionError(context, ref, error);
        }
    }
  }

  /// The prompt and expected-answer fields show their own server messages.
  static bool _shownAtField(ApiException error) =>
      error.fieldErrorsUnder('prompt').isNotEmpty ||
      error.fieldErrorsUnder('expectedAnswer').isNotEmpty;

  static bool _isInline(ApiException error) =>
      error.code == ApiErrorCode.validationFailed ||
      error.code == ApiErrorCode.secretDetectedBlocked;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final state = ref.watch(reviewItemEditControllerProvider(widget.item));
    final form = state.form;
    final error = state.error;
    return Scaffold(
      appBar: AppBar(
        title: Semantics(
          header: true,
          child: Text(form.isEdit ? l10n.reviewEditTitleEdit : l10n.reviewEditTitleNew),
        ),
      ),
      body: ScreenBody(
        maxWidth: AppBreakpoints.formCardWidth,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            ReviewItemSkillField(
              form: form,
              onChanged: (code) => _controller.edit((form) => form.copyWith(skillCode: code)),
            ),
            const SizedBox(height: AppSpacing.md),
            ReviewItemTypeField(
              form: form,
              onChanged: (type) => _controller.edit((form) => form.copyWith(reviewType: type)),
            ),
            const SizedBox(height: AppSpacing.md),
            _QuestionFields(prompt: _prompt, expected: _expected, error: error),
            ReviewItemRubricField(
              form: form,
              controllers: _rubric,
              onAdd: _addPoint,
              onRemove: _removePoint,
            ),
            _SaveArea(
              formError: error != null && _isInline(error) && !_shownAtField(error)
                  ? messageFor(error, l10n)
                  : null,
              onSave: form.canSave && !state.saving ? () => unawaited(_save()) : null,
            ),
          ],
        ),
      ),
    );
  }
}

/// The question and the expected answer, with their server messages.
class _QuestionFields extends StatelessWidget {
  const _QuestionFields({
    required this.prompt,
    required this.expected,
    required this.error,
  });

  final TextEditingController prompt;
  final TextEditingController expected;
  final ApiException? error;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _TextArea(
          fieldKey: 'reviewEdit.promptField',
          controller: prompt,
          label: l10n.reviewEditPrompt,
          maxLength: InputRules.reviewItemPromptMaxLength,
          serverError: error?.fieldMessage('prompt', fallback: l10n.errorValidationFailed),
        ),
        _TextArea(
          fieldKey: 'reviewEdit.expectedField',
          controller: expected,
          label: l10n.reviewEditExpected,
          maxLength: InputRules.reviewItemExpectedMaxLength,
          serverError: error?.fieldMessage(
            'expectedAnswer',
            fallback: l10n.errorValidationFailed,
          ),
        ),
      ],
    );
  }
}

/// The form-level error (a private key, an unmatched field) and "저장".
class _SaveArea extends StatelessWidget {
  const _SaveArea({required this.formError, required this.onSave});

  final String? formError;
  final VoidCallback? onSave;

  @override
  Widget build(BuildContext context) {
    final error = formError;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (error != null) InlineError(message: error),
        const SizedBox(height: AppSpacing.xl),
        FilledButton(
          key: const Key('reviewEdit.saveButton'),
          onPressed: onSave,
          child: Text(AppLocalizations.of(context).reviewEditSave),
        ),
      ],
    );
  }
}

/// A multi-line field with its `n / max` counter.
class _TextArea extends StatelessWidget {
  const _TextArea({
    required this.fieldKey,
    required this.controller,
    required this.label,
    required this.maxLength,
    required this.serverError,
  });

  final String fieldKey;
  final TextEditingController controller;
  final String label;
  final int maxLength;
  final String? serverError;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return ValueListenableBuilder<TextEditingValue>(
      valueListenable: controller,
      builder: (context, value, _) => TextField(
        key: Key(fieldKey),
        controller: controller,
        minLines: 3,
        maxLines: 8,
        decoration: InputDecoration(
          labelText: label,
          alignLabelWithHint: true,
          counterText: l10n.commonCharacterCount(value.text.length, maxLength),
          errorText: value.text.length > maxLength
              ? l10n.validationMaxLength(maxLength)
              : serverError,
        ),
      ),
    );
  }
}
