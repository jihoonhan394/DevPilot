import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/confirm_dialog.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/features/project/domain/project_form.dart';
import 'package:devpilot_app/features/project/presentation/project_edit_fields.dart';
import 'package:devpilot_app/features/project/presentation/project_form_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// `ProjectEditSheet` (docs/02 SCR-PROJECTS): add or edit one project. Pops with the save outcome;
/// null when closed without saving.
class ProjectEditSheet extends ConsumerStatefulWidget {
  const ProjectEditSheet({super.key, required this.initial});

  final ProjectFormValues initial;

  @override
  ConsumerState<ProjectEditSheet> createState() => _ProjectEditSheetState();
}

class _ProjectEditSheetState extends ConsumerState<ProjectEditSheet> {
  late final _name = TextEditingController(text: widget.initial.name);
  late final _description = TextEditingController(text: widget.initial.description);
  late final _repoUrl = TextEditingController(text: widget.initial.repoUrl);
  late final _stack = TextEditingController(text: widget.initial.stack);

  ProjectFormController get _controller =>
      ref.read(projectFormControllerProvider(widget.initial).notifier);

  @override
  void dispose() {
    for (final controller in [_name, _description, _repoUrl, _stack]) {
      controller.dispose();
    }
    super.dispose();
  }

  /// A 409 reload replaces the values; the text fields follow.
  void _syncFields(ProjectFormValues values) {
    for (final (controller, value) in [
      (_name, values.name),
      (_description, values.description),
      (_repoUrl, values.repoUrl),
      (_stack, values.stack),
    ]) {
      if (controller.text != value) {
        controller.text = value;
      }
    }
  }

  Future<void> _save() async {
    final outcome = await _controller.save();
    if (!mounted) {
      return;
    }
    switch (outcome) {
      case ProjectSaved() || ProjectSaveNotFound() || ProjectSaveFailed():
        Navigator.of(context).pop(outcome);
      case ProjectSaveReloaded():
        _syncFields(ref.read(projectFormControllerProvider(widget.initial)).values);
        showToast(context, AppLocalizations.of(context).projectsReloaded);
      case ProjectSaveRejected():
        return;
    }
  }

  /// Closing a sheet with edits asks first (docs/02 §6.8).
  Future<void> _confirmClose(bool didPop, Object? _) async {
    if (!didPop && await confirmDiscardChanges(context) && mounted) {
      Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final state = ref.watch(projectFormControllerProvider(widget.initial));
    final values = state.values;
    final violations = values.violations;
    final error = state.error;
    return PopScope(
      canPop: !values.hasChanges || state.isSaving,
      onPopInvokedWithResult: _confirmClose,
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            SectionTitle(values.isCreate ? l10n.projectsEditTitleNew : l10n.projectsEditTitle),
            const SizedBox(height: AppSpacing.lg),
            ProjectTextField(
              fieldKey: const Key('projects.nameField'),
              controller: _name,
              label: l10n.projectsFieldName,
              maxLength: InputRules.projectNameMaxLength,
              enabled: !state.isSaving,
              errorText: violations.contains(ProjectFieldViolation.nameRequired)
                  ? l10n.validationRequired
                  : projectServerFieldError(error, 'name', l10n),
              onChanged: (name) => _controller.edit((values) => values.copyWith(name: name)),
            ),
            ProjectOptionalFields(
              description: _description,
              repoUrl: _repoUrl,
              stack: _stack,
              enabled: !state.isSaving,
              repoUrlInvalid: violations.contains(ProjectFieldViolation.repoUrlInvalid),
              error: error,
              onEdit: _controller.edit,
            ),
            if (!values.isCreate)
              ProjectStatusSegment(
                status: values.status,
                enabled: !state.isSaving,
                onChanged: (status) =>
                    _controller.edit((values) => values.copyWith(status: status)),
              ),
            if (error?.code == ApiErrorCode.secretDetectedBlocked)
              InlineError(message: l10n.projectsSecretBlocked),
            const SizedBox(height: AppSpacing.xl),
            FilledButton(
              key: const Key('projects.saveButton'),
              onPressed: values.canSave && !state.isSaving ? _save : null,
              child: Text(l10n.projectsSave),
            ),
          ],
        ),
      ),
    );
  }
}
