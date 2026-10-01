import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/validation/goal_date_rules.dart';
import 'package:devpilot_app/features/plan/data/learning_goal_models.dart';
import 'package:devpilot_app/features/plan/data/learning_goal_repository.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Editable copy of the learning goal: the target date and focus skills
/// (docs/02 SCR-LEARNING-GOAL).
@immutable
final class LearningGoalForm {
  const LearningGoalForm({
    required this.goal,
    required this.completionDate,
    required this.focusSkillCodes,
    this.isSaving = false,
    this.saveError,
  });

  factory LearningGoalForm.of(LearningGoalView goal) => LearningGoalForm(
    goal: goal,
    completionDate: goal.targetCompletionDate,
    focusSkillCodes: [for (final skill in goal.focusSkills) skill.code],
  );

  /// The saved goal the form started from.
  final LearningGoalView goal;

  /// The target date (목표일, `targetCompletionDate`).
  final String completionDate;
  final List<String> focusSkillCodes;
  final bool isSaving;

  /// Last `VALIDATION_FAILED`, shown under the matching fields.
  final ApiException? saveError;

  bool get isDirty =>
      completionDate != goal.targetCompletionDate ||
      !listEquals(focusSkillCodes, [for (final skill in goal.focusSkills) skill.code]);

  bool isValid(LocalDate today) =>
      GoalDateRules.completionViolation(LocalDate.tryParse(completionDate), today) == null;

  LearningGoalForm copyWith({
    String? completionDate,
    List<String>? focusSkillCodes,
    bool? isSaving,
    ApiException? Function()? saveError,
  }) => LearningGoalForm(
    goal: goal,
    completionDate: completionDate ?? this.completionDate,
    focusSkillCodes: focusSkillCodes ?? this.focusSkillCodes,
    isSaving: isSaving ?? this.isSaving,
    saveError: saveError == null ? this.saveError : saveError(),
  );
}

sealed class LearningGoalSaveOutcome {
  const LearningGoalSaveOutcome();
}

final class LearningGoalSaved extends LearningGoalSaveOutcome {
  const LearningGoalSaved({required this.datesChanged});

  /// True when the target date changed: the server set `replanRecommended` (docs/05 §5.2).
  final bool datesChanged;
}

/// `CONCURRENT_MODIFICATION`: the form now holds the latest goal.
final class LearningGoalReloaded extends LearningGoalSaveOutcome {
  const LearningGoalReloaded();
}

final class LearningGoalSaveFailed extends LearningGoalSaveOutcome {
  const LearningGoalSaveFailed(this.error);

  final Object error;
}

/// SCR-LEARNING-GOAL: `GET /learning-goal` and `PUT /learning-goal` (docs/05 §5).
final class LearningGoalController extends AsyncNotifier<LearningGoalForm> {
  @override
  Future<LearningGoalForm> build() async =>
      LearningGoalForm.of(await ref.read(learningGoalRepositoryProvider).fetchGoal());

  void _edit(LearningGoalForm Function(LearningGoalForm form) change) {
    final form = state.value;
    if (form != null && !form.isSaving) {
      state = AsyncData(change(form).copyWith(saveError: () => null));
    }
  }

  void setCompletionDate(LocalDate date) =>
      _edit((form) => form.copyWith(completionDate: date.toIso()));

  void setFocusSkills(List<String> codes) => _edit((form) => form.copyWith(focusSkillCodes: codes));

  Future<LearningGoalSaveOutcome> save() async {
    final form = state.value;
    if (form == null || form.isSaving) {
      return const LearningGoalReloaded();
    }
    state = AsyncData(form.copyWith(isSaving: true, saveError: () => null));
    final goal = form.goal;
    try {
      final saved = await ref
          .read(learningGoalRepositoryProvider)
          .updateGoal(
            LearningGoalUpdateRequest(
              targetRole: goal.targetRole == TargetRole.unknown
                  ? TargetRole.javaBackend
                  : goal.targetRole,
              targetCompletionDate: form.completionDate,
              focusSkillCodes: form.focusSkillCodes,
              version: goal.version,
            ),
          );
      state = AsyncData(LearningGoalForm.of(saved));
      return LearningGoalSaved(
        datesChanged: saved.targetCompletionDate != goal.targetCompletionDate,
      );
    } on ApiException catch (error) {
      if (error.code == ApiErrorCode.concurrentModification) {
        state = AsyncData(
          LearningGoalForm.of(await ref.read(learningGoalRepositoryProvider).fetchGoal()),
        );
        return const LearningGoalReloaded();
      }
      state = AsyncData(
        form.copyWith(
          isSaving: false,
          saveError: () => error.code == ApiErrorCode.validationFailed ? error : null,
        ),
      );
      return LearningGoalSaveFailed(error);
    }
  }
}

final learningGoalControllerProvider =
    AsyncNotifierProvider.autoDispose<LearningGoalController, LearningGoalForm>(
      LearningGoalController.new,
    );

/// Leaving the screen with edits asks first (docs/02 §6.8).
final learningGoalHasUnsavedChangesProvider = Provider.autoDispose<bool>(
  (ref) => ref.watch(learningGoalControllerProvider).value?.isDirty ?? false,
);
