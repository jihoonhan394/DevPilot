import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/form_modal.dart';
import 'package:devpilot_app/features/project/domain/project_form.dart';
import 'package:devpilot_app/features/project/presentation/project_edit_sheet.dart';
import 'package:devpilot_app/features/project/presentation/project_form_controller.dart';
import 'package:devpilot_app/features/project/presentation/projects_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Opens `ProjectEditSheet` and reports the result (docs/02 SCR-PROJECTS "데이터"): a save re-reads
/// the list because `updatedAt` moves the project and possibly the "Today 과제 대상" label.
Future<void> openProjectSheet(
  BuildContext context,
  WidgetRef ref,
  ProjectFormValues initial,
) async {
  final l10n = AppLocalizations.of(context);
  final outcome = await showFormModal<ProjectSaveOutcome>(
    context,
    builder: (_) => ProjectEditSheet(initial: initial),
  );
  if (!context.mounted || outcome == null) {
    return;
  }
  final controller = ref.read(projectsControllerProvider.notifier);
  switch (outcome) {
    case ProjectSaved(:final created, :final pausedOrDone):
      controller.reload();
      showToast(
        context,
        created
            ? l10n.projectsCreated
            : (pausedOrDone ? l10n.projectsStatusChanged : l10n.projectsSaved),
      );
    case ProjectSaveNotFound():
      controller.reload();
      showToast(context, l10n.errorResourceNotFound);
    case ProjectSaveFailed(:final error):
      await presentActionError(context, ref, error);
    case ProjectSaveReloaded() || ProjectSaveRejected():
      return;
  }
}
