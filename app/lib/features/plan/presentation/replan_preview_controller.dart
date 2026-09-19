import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/features/plan/data/plan_budget_models.dart';
import 'package:devpilot_app/features/plan/data/plan_repository.dart';
import 'package:devpilot_app/features/plan/domain/replan_suggestion_selection.dart';
import 'package:devpilot_app/features/plan/presentation/replan_controller.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

enum ReplanStage { edit, preview }

/// The preview step of SCR-REPLAN (docs/02 SCR-REPLAN ②·③).
@immutable
final class ReplanPreviewState {
  const ReplanPreviewState({
    this.stage = ReplanStage.edit,
    this.preview,
    this.recalculated,
    this.selection = ReplanSuggestionSelection.empty,
    this.busy = false,
    this.rowErrors = const {},
  });

  final ReplanStage stage;

  /// The first preview, requested with nothing checked. Its suggestion lists stay fixed.
  final ReplanPreviewResponse? preview;

  /// "선택 반영해 다시 계산": the preview with the checked suggestions applied.
  final ReplanPreviewResponse? recalculated;
  final ReplanSuggestionSelection selection;
  final bool busy;

  /// Server messages under suggestion rows, by `SuggestionIdentity` (empty: no message sent).
  final Map<String, String> rowErrors;

  ReplanPreviewState copyWith({
    ReplanStage? stage,
    ReplanPreviewResponse? Function()? preview,
    ReplanPreviewResponse? Function()? recalculated,
    ReplanSuggestionSelection? selection,
    bool? busy,
    Map<String, String>? rowErrors,
  }) => ReplanPreviewState(
    stage: stage ?? this.stage,
    preview: preview == null ? this.preview : preview(),
    recalculated: recalculated == null ? this.recalculated : recalculated(),
    selection: selection ?? this.selection,
    busy: busy ?? this.busy,
    rowErrors: rowErrors ?? this.rowErrors,
  );
}

sealed class ReplanPreviewOutcome {
  const ReplanPreviewOutcome();
}

final class ReplanPreviewShown extends ReplanPreviewOutcome {
  const ReplanPreviewShown();
}

final class ReplanPreviewConflict extends ReplanPreviewOutcome {
  const ReplanPreviewConflict();
}

/// Field errors are already shown (edit fields or suggestion rows).
final class ReplanPreviewRejected extends ReplanPreviewOutcome {
  const ReplanPreviewRejected();
}

final class ReplanPreviewFailed extends ReplanPreviewOutcome {
  const ReplanPreviewFailed(this.error);

  final Object error;
}

/// `POST /plans/{planId}/replan/preview` for the current edit, suggestion checks and the
/// recalculation. Any change to the edit drops the preview and the checks (docs/02 SCR-REPLAN).
final class ReplanPreviewController extends Notifier<ReplanPreviewState> {
  static const _conflictCodes = {
    ApiErrorCode.concurrentModification,
    ApiErrorCode.planNotActive,
  };

  @override
  ReplanPreviewState build() {
    ref.listen(
      replanControllerProvider.select((replan) => replan.value?.draft),
      (previous, next) {
        if (!identical(previous, next)) {
          state = const ReplanPreviewState();
        }
      },
    );
    return const ReplanPreviewState();
  }

  /// "변경 미리보기": the first preview with nothing checked.
  Future<ReplanPreviewOutcome> preview() async {
    final outcome = await _request(ReplanSuggestionSelection.empty);
    if (outcome case (final ReplanPreviewResponse response, null)) {
      state = ReplanPreviewState(stage: ReplanStage.preview, preview: response);
      return const ReplanPreviewShown();
    }
    return _failure(outcome.$2!, fromEdit: true);
  }

  /// "선택 반영해 다시 계산".
  Future<ReplanPreviewOutcome> recalculate() async {
    final outcome = await _request(state.selection);
    if (outcome case (final ReplanPreviewResponse response, null)) {
      state = state.copyWith(recalculated: () => response, rowErrors: const {});
      return const ReplanPreviewShown();
    }
    return _failure(outcome.$2!, fromEdit: false);
  }

  void backToEdit() => state = state.copyWith(stage: ReplanStage.edit);

  void toggle(ReplanSuggestionSelection Function(ReplanSuggestionSelection selection) change) {
    if (!state.busy) {
      state = state.copyWith(selection: change(state.selection), recalculated: () => null);
    }
  }

  /// Save failures on suggestion rows: inline under the row, then fresh suggestions
  /// (docs/02 SCR-REPLAN "상태").
  Future<bool> showSaveErrors(ApiException error) async {
    final rowErrors = _rowErrorsOf(error);
    if (rowErrors.isEmpty) {
      return false;
    }
    final outcome = await _request(ReplanSuggestionSelection.empty);
    final response = outcome.$1;
    state = ReplanPreviewState(
      stage: ReplanStage.preview,
      preview: response ?? state.preview,
      rowErrors: rowErrors,
    );
    return true;
  }

  Future<(ReplanPreviewResponse?, ApiException?)> _request(
    ReplanSuggestionSelection selection,
  ) async {
    final draft = ref.read(replanControllerProvider).value?.draft;
    if (draft == null || state.busy) {
      return (null, const ApiException(code: ApiErrorCode.internalError));
    }
    state = state.copyWith(busy: true);
    try {
      final response = await ref
          .read(planRepositoryProvider)
          .previewReplan(draft.basePlan.id, selection.applyTo(draft.toRequest()));
      if (ref.mounted) {
        state = state.copyWith(busy: false);
      }
      return (response, null);
    } on ApiException catch (error) {
      if (ref.mounted) {
        state = state.copyWith(busy: false);
      }
      return (null, error);
    }
  }

  ReplanPreviewOutcome _failure(ApiException error, {required bool fromEdit}) {
    if (_conflictCodes.contains(error.code)) {
      return const ReplanPreviewConflict();
    }
    if (error.code != ApiErrorCode.validationFailed || error.fieldErrors.isEmpty) {
      return ReplanPreviewFailed(error);
    }
    final rowErrors = _rowErrorsOf(error);
    if (!fromEdit && rowErrors.isNotEmpty) {
      state = state.copyWith(rowErrors: rowErrors);
      return const ReplanPreviewRejected();
    }
    // Milestone or reason problems are fixed in the edit step.
    ref.read(replanControllerProvider.notifier).showFieldErrors(error);
    state = state.copyWith(stage: ReplanStage.edit);
    return const ReplanPreviewRejected();
  }

  Map<String, String> _rowErrorsOf(ApiException error) => {
    for (final fieldError in error.fieldErrors)
      ?state.selection.identityOfField(fieldError.field): fieldError.message ?? '',
  };
}

final replanPreviewControllerProvider =
    NotifierProvider.autoDispose<ReplanPreviewController, ReplanPreviewState>(
      ReplanPreviewController.new,
    );
