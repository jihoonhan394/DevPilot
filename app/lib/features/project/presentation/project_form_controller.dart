import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/project/data/side_project_models.dart';
import 'package:devpilot_app/features/project/data/side_project_repository.dart';
import 'package:devpilot_app/features/project/domain/project_form.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

@immutable
final class ProjectFormState {
  const ProjectFormState({required this.values, this.isSaving = false, this.error});

  final ProjectFormValues values;
  final bool isSaving;

  /// Last failure shown in the sheet: field errors, or `SECRET_DETECTED_BLOCKED` at the bottom.
  final ApiException? error;

  ProjectFormState copyWith({
    ProjectFormValues? values,
    bool? isSaving,
    ApiException? Function()? error,
  }) => ProjectFormState(
    values: values ?? this.values,
    isSaving: isSaving ?? this.isSaving,
    error: error == null ? this.error : error(),
  );
}

sealed class ProjectSaveOutcome {
  const ProjectSaveOutcome();
}

final class ProjectSaved extends ProjectSaveOutcome {
  const ProjectSaved({required this.created, required this.pausedOrDone});

  final bool created;

  /// Status changed to `PAUSED`/`DONE`: toast `projects.statusChanged`.
  final bool pausedOrDone;
}

/// `CONCURRENT_MODIFICATION`: the sheet now shows the latest values (docs/02 SCR-PROJECTS).
final class ProjectSaveReloaded extends ProjectSaveOutcome {
  const ProjectSaveReloaded();
}

/// `RESOURCE_NOT_FOUND`: deleted elsewhere; the sheet closes and the list is re-read.
final class ProjectSaveNotFound extends ProjectSaveOutcome {
  const ProjectSaveNotFound();
}

/// Shown inside the sheet (field errors or the private-key message); input is kept.
final class ProjectSaveRejected extends ProjectSaveOutcome {
  const ProjectSaveRejected();
}

final class ProjectSaveFailed extends ProjectSaveOutcome {
  const ProjectSaveFailed(this.error);

  final Object error;
}

/// `ProjectEditSheet` state: create (`POST`, IK) or edit (`PATCH` of changed fields).
final class ProjectFormController extends Notifier<ProjectFormState> {
  ProjectFormController(this._initial);

  final ProjectFormValues _initial;
  final _createKeys = IdempotencyKeyCache();

  @override
  ProjectFormState build() => ProjectFormState(values: _initial);

  void edit(ProjectFormValues Function(ProjectFormValues values) change) {
    if (!state.isSaving) {
      state = state.copyWith(values: change(state.values), error: () => null);
    }
  }

  Future<ProjectSaveOutcome> save() async {
    final values = state.values;
    if (state.isSaving || !values.canSave) {
      return const ProjectSaveRejected();
    }
    state = state.copyWith(isSaving: true, error: () => null);
    final repository = ref.read(sideProjectRepositoryProvider);
    final original = values.original;
    try {
      if (original == null) {
        final request = values.toCreateRequest();
        final key = _createKeys.keyFor(request.toJson());
        try {
          await repository.createProject(request, idempotencyKey: key);
        } on ApiException catch (error) {
          _createKeys.settle(error);
          rethrow;
        }
        _createKeys.settle(null);
      } else {
        await repository.updateProject(original.id, values.toPatchRequest(original));
      }
      if (ref.mounted) {
        state = state.copyWith(isSaving: false);
      }
      return ProjectSaved(
        created: original == null,
        pausedOrDone:
            original != null &&
            values.status != original.status &&
            (values.status == SideProjectStatus.paused || values.status == SideProjectStatus.done),
      );
    } on ApiException catch (error) {
      return _handleFailure(error, original);
    }
  }

  Future<ProjectSaveOutcome> _handleFailure(ApiException error, SideProjectView? original) async {
    if (original != null && error.code == ApiErrorCode.concurrentModification) {
      try {
        final latest = await ref.read(sideProjectRepositoryProvider).fetchProject(original.id);
        if (ref.mounted) {
          state = ProjectFormState(values: ProjectFormValues.edit(latest));
        }
        return const ProjectSaveReloaded();
      } on ApiException catch (reloadError) {
        if (ref.mounted) {
          state = state.copyWith(isSaving: false);
        }
        return reloadError.code == ApiErrorCode.resourceNotFound
            ? const ProjectSaveNotFound()
            : ProjectSaveFailed(reloadError);
      }
    }
    if (ref.mounted) {
      state = state.copyWith(isSaving: false, error: () => error);
    }
    return switch (error.code) {
      ApiErrorCode.resourceNotFound => const ProjectSaveNotFound(),
      ApiErrorCode.validationFailed ||
      ApiErrorCode.secretDetectedBlocked => const ProjectSaveRejected(),
      _ => ProjectSaveFailed(error),
    };
  }
}

/// One sheet at a time; the argument is the project being edited (null = new) and its prefill.
final projectFormControllerProvider = NotifierProvider.autoDispose
    .family<ProjectFormController, ProjectFormState, ProjectFormValues>(
      ProjectFormController.new,
    );
