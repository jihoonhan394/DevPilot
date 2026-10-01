import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/features/plan/data/plan_repository.dart';
import 'package:devpilot_app/features/plan/domain/milestone_ordering.dart';
import 'package:devpilot_app/features/plan/domain/replan_draft.dart';
import 'package:devpilot_app/features/plan/domain/replan_suggestion_selection.dart';
import 'package:devpilot_app/features/plan/presentation/plan_controller.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

@immutable
final class ReplanState {
  const ReplanState({
    required this.draft,
    this.isSaving = false,
    this.saveError,
    this.saved = false,
    this.removed,
  });

  final ReplanDraft draft;
  final bool isSaving;

  /// Last `VALIDATION_FAILED`: field errors shown under the matching card field.
  final ApiException? saveError;

  /// True after a successful save; leaving no longer asks for confirmation.
  final bool saved;

  /// The last deleted milestone and its position, for the "되돌리기" toast action.
  final ({MilestoneDraft milestone, int index})? removed;

  bool get hasUnsavedChanges => !saved && draft.isDirty;

  ReplanState copyWith({
    ReplanDraft? draft,
    bool? isSaving,
    ApiException? Function()? saveError,
    bool? saved,
    ({MilestoneDraft milestone, int index})? Function()? removed,
  }) => ReplanState(
    draft: draft ?? this.draft,
    isSaving: isSaving ?? this.isSaving,
    saveError: saveError == null ? this.saveError : saveError(),
    saved: saved ?? this.saved,
    removed: removed == null ? this.removed : removed(),
  );
}

sealed class ReplanSaveOutcome {
  const ReplanSaveOutcome();
}

final class ReplanSaved extends ReplanSaveOutcome {
  const ReplanSaved(this.planVersion);

  final int planVersion;
}

/// `CONCURRENT_MODIFICATION` / `PLAN_NOT_ACTIVE`: another tab saved first (docs/02 SCR-REPLAN).
final class ReplanConflict extends ReplanSaveOutcome {
  const ReplanConflict();
}

final class ReplanSaveFailed extends ReplanSaveOutcome {
  const ReplanSaveFailed(this.error);

  final Object error;
}

/// SCR-REPLAN edit step and saving: `GET /plans/active` then `POST /plans/{planId}/replan`. The
/// preview step lives in [ReplanPreviewController] (replan_preview_controller.dart).
final class ReplanController extends AsyncNotifier<ReplanState> {
  final _keys = IdempotencyKeyCache();
  var _newMilestoneCount = 0;

  static const _conflictCodes = {
    ApiErrorCode.concurrentModification,
    ApiErrorCode.planNotActive,
  };

  @override
  Future<ReplanState> build() async =>
      ReplanState(draft: ReplanDraft.of(await ref.read(planRepositoryProvider).fetchActivePlan()));

  void _editDraft(ReplanDraft Function(ReplanDraft draft) change) {
    final current = state.value;
    if (current != null && !current.isSaving) {
      state = AsyncData(current.copyWith(draft: change(current.draft)));
    }
  }

  void setReason(String reason) => _editDraft((draft) => draft.copyWith(reason: reason));

  void updateMilestone(String localKey, MilestoneDraft Function(MilestoneDraft milestone) change) =>
      _editDraft(
        (draft) => draft.copyWith(
          milestones: [
            for (final milestone in draft.milestones)
              milestone.localKey == localKey ? change(milestone) : milestone,
          ],
        ),
      );

  void addMilestone(LocalDate today) {
    _newMilestoneCount++;
    _editDraft(
      (draft) => draft.copyWith(
        milestones: [
          ...draft.milestones,
          ReplanRules.newMilestone(localKey: 'new-$_newMilestoneCount', today: today),
        ],
      ),
    );
  }

  /// Removes without asking; the toast offers "되돌리기" (docs/02 SCR-REPLAN).
  void removeMilestone(String localKey) {
    final current = state.value;
    if (current == null || current.isSaving) {
      return;
    }
    final index = current.draft.milestones.indexWhere(
      (milestone) => milestone.localKey == localKey,
    );
    if (index < 0) {
      return;
    }
    state = AsyncData(
      current.copyWith(
        draft: current.draft.copyWith(
          milestones: [...current.draft.milestones]..removeAt(index),
        ),
        removed: () => (milestone: current.draft.milestones[index], index: index),
      ),
    );
  }

  void undoRemove() {
    final current = state.value;
    final removed = current?.removed;
    if (current == null || removed == null) {
      return;
    }
    final milestones = [...current.draft.milestones];
    milestones.insert(removed.index.clamp(0, milestones.length), removed.milestone);
    state = AsyncData(
      current.copyWith(
        draft: current.draft.copyWith(milestones: milestones),
        removed: () => null,
      ),
    );
  }

  void move(String localKey, MoveDirection direction) => _editDraft((draft) {
    final milestones = [...draft.milestones];
    final index = milestones.indexWhere((milestone) => milestone.localKey == localKey);
    final target = direction == MoveDirection.up ? index - 1 : index + 1;
    if (index < 0 || target < 0 || target >= milestones.length) {
      return draft;
    }
    milestones.insert(target, milestones.removeAt(index));
    return draft.copyWith(milestones: milestones);
  });

  /// Discards the edit and starts again from the latest active plan (conflict dialog).
  void reloadLatest() => ref.invalidateSelf();

  /// Shows the field errors of a failed preview under the edit fields.
  void showFieldErrors(ApiException error) {
    final current = state.value;
    if (current != null) {
      state = AsyncData(current.copyWith(saveError: () => error));
    }
  }

  /// "새 버전으로 저장" with the checked suggestions (docs/05 §7.8).
  Future<ReplanSaveOutcome> save({
    ReplanSuggestionSelection selection = ReplanSuggestionSelection.empty,
  }) async {
    final current = state.value;
    if (current == null || current.isSaving) {
      return const ReplanConflict();
    }
    state = AsyncData(current.copyWith(isSaving: true, saveError: () => null));
    final draft = current.draft;
    final request = selection.applyTo(draft.toRequest());
    final idempotencyKey = _keys.keyFor(request.toJson());
    try {
      final response = await ref
          .read(planRepositoryProvider)
          .replan(draft.basePlan.id, request, idempotencyKey: idempotencyKey);
      _keys.settle(null);
      if (ref.mounted) {
        state = AsyncData(current.copyWith(isSaving: false, saved: true));
      }
      ref.read(planControllerProvider.notifier).applyReplan(response);
      return ReplanSaved(response.plan.planVersion);
    } on ApiException catch (error) {
      _keys.settle(error);
      if (ref.mounted) {
        state = AsyncData(
          current.copyWith(
            isSaving: false,
            saveError: () => error.code == ApiErrorCode.validationFailed ? error : null,
          ),
        );
      }
      return _conflictCodes.contains(error.code) ? const ReplanConflict() : ReplanSaveFailed(error);
    }
  }
}

final replanControllerProvider = AsyncNotifierProvider.autoDispose<ReplanController, ReplanState>(
  ReplanController.new,
);

/// Leaving the screen with edits asks first (docs/02 §6.8).
final replanHasUnsavedChangesProvider = Provider.autoDispose<bool>(
  (ref) => ref.watch(replanControllerProvider).value?.hasUnsavedChanges ?? false,
);
