import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/l10n/validation_messages.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/validation/goal_date_rules.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/confirm_dialog.dart';
import 'package:devpilot_app/core/widgets/date_field.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/core/widgets/skill_multi_picker.dart';
import 'package:devpilot_app/features/plan/presentation/learning_goal_controller.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/skill/data/skill_models.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-LEARNING-GOAL: the learning track, the user's own target date (목표일) and focus skills
/// (docs/02 §3.9). DevPilot plans backwards from the date; changing it only recommends a replan.
class LearningGoalScreen extends ConsumerWidget {
  const LearningGoalScreen({super.key});

  Future<void> _save(BuildContext context, WidgetRef ref) async {
    final l10n = AppLocalizations.of(context);
    final outcome = await ref.read(learningGoalControllerProvider.notifier).save();
    if (!context.mounted) {
      return;
    }
    switch (outcome) {
      case LearningGoalSaved(datesChanged: true):
        final adjustNow = await showConfirmDialog(
          context,
          title: l10n.goalSavedDatesChangedTitle,
          body: l10n.goalSavedDatesChangedBody,
          cancelLabel: l10n.goalSavedDatesChangedLater,
          confirmLabel: l10n.goalSavedDatesChangedNow,
          confirmKey: const Key('goal.adjustNowButton'),
        );
        if (context.mounted) {
          context.go(adjustNow ? AppRoutes.replanFrom(AppRoutes.replanFromGoal) : AppRoutes.plan);
        }
      case LearningGoalSaved():
        showToast(context, l10n.goalSaved);
        if (context.canPop()) {
          context.pop();
        } else {
          context.go(AppRoutes.plan);
        }
      case LearningGoalReloaded():
        showToast(context, l10n.errorConcurrentModification);
      case LearningGoalSaveFailed(:final error):
        await presentActionError(context, ref, error);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final form = ref.watch(learningGoalControllerProvider);
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.goalTitle))),
      body: ScreenBody(
        maxWidth: AppBreakpoints.formCardWidth,
        child: form.when(
          loading: () => const SkeletonList(count: 3, lines: 2),
          error: (error, _) => ErrorView(
            error: error,
            onRetry: () => ref.invalidate(learningGoalControllerProvider),
          ),
          data: (form) => _GoalForm(form: form, onSave: () => _save(context, ref)),
        ),
      ),
    );
  }
}

class _GoalForm extends ConsumerWidget {
  const _GoalForm({required this.form, required this.onSave});

  final LearningGoalForm form;
  final VoidCallback onSave;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final controller = ref.read(learningGoalControllerProvider.notifier);
    final today = LocalDate.parse(ref.watch(meProvider).requireValue.today);
    final completion = LocalDate.tryParse(form.completionDate);
    final saveError = form.saveError;
    final fallback = l10n.errorValidationFailed;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        InputDecorator(
          decoration: InputDecoration(labelText: l10n.onboardingGoalRole, enabled: false),
          child: Text(TargetRole.javaBackend.label(l10n)),
        ),
        const SizedBox(height: AppSpacing.lg),
        DateField(
          key: const Key('goal.completionField'),
          label: l10n.onboardingGoalCompletion,
          value: completion,
          firstDate: today.addDays(1),
          lastDate: today.addYears(3),
          enabled: !form.isSaving,
          errorText:
              goalDateMessage(GoalDateRules.completionViolation(completion, today), l10n) ??
              saveError?.fieldMessage('targetCompletionDate', fallback: fallback),
          onChanged: controller.setCompletionDate,
        ),
        const SizedBox(height: AppSpacing.lg),
        _FocusSkills(
          codes: form.focusSkillCodes,
          enabled: !form.isSaving,
          errorText: saveError?.fieldMessage('focusSkillCodes', fallback: fallback),
          onChanged: controller.setFocusSkills,
        ),
        const SizedBox(height: AppSpacing.lg),
        Text(l10n.goalHelp, style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: AppSpacing.xl),
        FilledButton(
          key: const Key('goal.saveButton'),
          onPressed: form.isDirty && form.isValid(today) && !form.isSaving ? onSave : null,
          child: Text(l10n.goalSave),
        ),
      ],
    );
  }
}

class _FocusSkills extends ConsumerWidget {
  const _FocusSkills({
    required this.codes,
    required this.enabled,
    required this.errorText,
    required this.onChanged,
  });

  final List<String> codes;
  final bool enabled;
  final String? errorText;
  final ValueChanged<List<String>> onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final tree = ref.watch(skillTreeProvider).value;
    final names = {
      for (final skill in tree?.skills ?? const <SkillNodeView>[]) skill.code: skill.name,
    };
    final error = errorText;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l10n.goalFocus, style: Theme.of(context).textTheme.labelLarge),
        const SizedBox(height: AppSpacing.sm),
        Wrap(
          spacing: AppSpacing.xs,
          runSpacing: AppSpacing.xs,
          children: [
            for (final code in codes)
              InputChip(
                label: Text(names[code] ?? code),
                onDeleted: enabled
                    ? () => onChanged([
                        for (final selected in codes)
                          if (selected != code) selected,
                      ])
                    : null,
              ),
          ],
        ),
        Align(
          alignment: Alignment.centerLeft,
          child: OutlinedButton.icon(
            key: const Key('goal.focusPickButton'),
            icon: const Icon(Icons.add),
            label: Text(l10n.commonPickSkills),
            onPressed: !enabled || tree == null
                ? null
                : () async {
                    final picked = await showSkillMultiPicker(
                      context,
                      title: l10n.goalFocus,
                      skills: pickableSkills(tree, l10n),
                      initialCodes: codes,
                      maxCount: InputRules.focusSkillsMax,
                    );
                    if (picked != null) {
                      onChanged(picked);
                    }
                  },
          ),
        ),
        if (error != null) InlineError(message: error),
      ],
    );
  }
}
