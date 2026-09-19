import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/features/project/domain/project_form.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

// Fields of ProjectEditSheet (docs/02 SCR-PROJECTS).

const _statuses = [SideProjectStatus.active, SideProjectStatus.paused, SideProjectStatus.done];

/// Server field error: `URL` and `NOT_BLANK_IF_PRESENT` have their own copy (docs/02
/// SCR-PROJECTS "Error (저장)"); others show the server message.
String? projectServerFieldError(ApiException? error, String field, AppLocalizations l10n) {
  final fieldError = error?.fieldErrorsUnder(field).firstOrNull;
  if (fieldError == null) {
    return null;
  }
  return switch (fieldError.code) {
    ApiFieldErrorCode.url => l10n.validationUrl,
    ApiFieldErrorCode.notBlankIfPresent => l10n.validationRequired,
    _ => fieldError.message ?? l10n.errorValidationFailed,
  };
}

/// Description, repository URL (with the "기록용" note) and stack.
class ProjectOptionalFields extends StatelessWidget {
  const ProjectOptionalFields({
    super.key,
    required this.description,
    required this.repoUrl,
    required this.stack,
    required this.enabled,
    required this.repoUrlInvalid,
    required this.error,
    required this.onEdit,
  });

  final TextEditingController description;
  final TextEditingController repoUrl;
  final TextEditingController stack;
  final bool enabled;
  final bool repoUrlInvalid;
  final ApiException? error;
  final void Function(ProjectFormValues Function(ProjectFormValues values) change) onEdit;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ProjectTextField(
          fieldKey: const Key('projects.descriptionField'),
          controller: description,
          label: l10n.projectsFieldDescription,
          maxLength: InputRules.projectDescriptionMaxLength,
          maxLines: 4,
          enabled: enabled,
          errorText: projectServerFieldError(error, 'description', l10n),
          onChanged: (text) => onEdit((values) => values.copyWith(description: text)),
        ),
        ProjectTextField(
          fieldKey: const Key('projects.repoUrlField'),
          controller: repoUrl,
          label: l10n.projectsFieldRepoUrl,
          maxLength: InputRules.projectRepoUrlMaxLength,
          keyboardType: TextInputType.url,
          enabled: enabled,
          helperText: l10n.projectsRepoUrlNote,
          errorText: repoUrlInvalid
              ? l10n.validationUrl
              : projectServerFieldError(error, 'repoUrl', l10n),
          onChanged: (url) => onEdit((values) => values.copyWith(repoUrl: url)),
        ),
        ProjectTextField(
          fieldKey: const Key('projects.stackField'),
          controller: stack,
          label: l10n.projectsFieldStack,
          hintText: l10n.projectsFieldStackHint,
          maxLength: InputRules.projectStackMaxLength,
          enabled: enabled,
          errorText: projectServerFieldError(error, 'stack', l10n),
          onChanged: (text) => onEdit((values) => values.copyWith(stack: text)),
        ),
      ],
    );
  }
}

/// `StatusSegmented` (edit only): 진행 중 / 잠시 멈춤 / 완료.
class ProjectStatusSegment extends StatelessWidget {
  const ProjectStatusSegment({
    super.key,
    required this.status,
    required this.enabled,
    required this.onChanged,
  });

  final SideProjectStatus status;
  final bool enabled;
  final ValueChanged<SideProjectStatus> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(l10n.projectsFieldStatus, style: Theme.of(context).textTheme.labelLarge),
        const SizedBox(height: AppSpacing.xs),
        SegmentedButton<SideProjectStatus>(
          key: const Key('projects.statusSegmented'),
          segments: [
            for (final status in _statuses)
              ButtonSegment(value: status, label: Text(status.label(l10n))),
          ],
          selected: {status},
          onSelectionChanged: enabled ? (selected) => onChanged(selected.first) : null,
        ),
      ],
    );
  }
}

class ProjectTextField extends StatelessWidget {
  const ProjectTextField({
    super.key,
    required this.fieldKey,
    required this.controller,
    required this.label,
    required this.maxLength,
    required this.enabled,
    required this.onChanged,
    this.errorText,
    this.helperText,
    this.hintText,
    this.maxLines = 1,
    this.keyboardType,
  });

  final Key fieldKey;
  final TextEditingController controller;
  final String label;
  final int maxLength;
  final bool enabled;
  final ValueChanged<String> onChanged;
  final String? errorText;
  final String? helperText;
  final String? hintText;
  final int maxLines;
  final TextInputType? keyboardType;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: TextField(
        key: fieldKey,
        controller: controller,
        enabled: enabled,
        maxLength: maxLength,
        maxLines: maxLines,
        minLines: 1,
        keyboardType: keyboardType,
        decoration: InputDecoration(
          labelText: label,
          errorText: errorText,
          helperText: helperText,
          helperMaxLines: 2,
          hintText: hintText,
        ),
        onChanged: onChanged,
      ),
    );
  }
}
