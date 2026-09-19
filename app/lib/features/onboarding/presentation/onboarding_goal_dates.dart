import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/validation_messages.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/validation/goal_date_rules.dart';
import 'package:devpilot_app/core/widgets/date_field.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_draft.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_rules.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_copy.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_draft_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// Step 1 goal dates: completion date (required) and checkpoint date (optional), each with
/// quick chips (docs/02 step 1). The dates are the user's own; the chips only fill them in.
class OnboardingGoalDates extends StatelessWidget {
  const OnboardingGoalDates({
    super.key,
    required this.draft,
    required this.today,
    required this.submitError,
    required this.onChanged,
  });

  final OnboardingDraft draft;
  final LocalDate today;
  final ApiException? submitError;
  final ValueChanged<OnboardingDraftEdit> onChanged;

  @override
  Widget build(BuildContext context) {
    final completion = LocalDate.tryParse(draft.targetCompletionDate);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _CompletionDate(
          completion: completion,
          today: today,
          submitError: submitError,
          onChanged: (date) =>
              onChanged((draft) => draft.copyWith(targetCompletionDate: date.toIso())),
        ),
        const SizedBox(height: AppSpacing.xl),
        _CheckpointDate(
          checkpoint: LocalDate.tryParse(draft.checkpointDate),
          completion: completion,
          today: today,
          submitError: submitError,
          onChanged: (date) => onChanged((draft) => draft.copyWith(checkpointDate: date?.toIso())),
        ),
      ],
    );
  }
}

/// "학습 완료 목표일": 3개월 후 / 6개월 후 / 직접 선택 (today + 1 day ~ today + 3 years).
class _CompletionDate extends StatelessWidget {
  const _CompletionDate({
    required this.completion,
    required this.today,
    required this.submitError,
    required this.onChanged,
  });

  final LocalDate? completion;
  final LocalDate today;
  final ApiException? submitError;
  final ValueChanged<LocalDate> onChanged;

  Future<void> _pick(BuildContext context, AppLocalizations l10n) async {
    final picked = await pickLocalDate(
      context,
      initialDate: completion,
      firstDate: today.addDays(1),
      lastDate: today.addYears(3),
      helpText: l10n.onboardingGoalCompletion,
    );
    if (picked != null) {
      onChanged(picked);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final threeMonths = OnboardingRules.completionAfterMonths(today, 3);
    final sixMonths = OnboardingRules.completionAfterMonths(today, 6);
    final error =
        goalDateMessage(GoalDateRules.completionViolation(completion, today), l10n) ??
        submitFieldMessage(submitError, 'learningGoal.targetCompletionDate', l10n);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l10n.onboardingGoalCompletion, style: textTheme.labelLarge),
        const SizedBox(height: AppSpacing.sm),
        Wrap(
          spacing: AppSpacing.sm,
          runSpacing: AppSpacing.sm,
          children: [
            ChoiceChip(
              key: const Key('onboarding.completion.quick3m'),
              label: Text(l10n.onboardingGoalQuick3m),
              selected: completion == threeMonths,
              onSelected: (_) => onChanged(threeMonths),
            ),
            ChoiceChip(
              key: const Key('onboarding.completion.quick6m'),
              label: Text(l10n.onboardingGoalQuick6m),
              selected: completion == sixMonths,
              onSelected: (_) => onChanged(sixMonths),
            ),
            ChoiceChip(
              key: const Key('onboarding.completion.custom'),
              label: Text(l10n.onboardingGoalQuickCustom),
              selected: completion != null && completion != threeMonths && completion != sixMonths,
              onSelected: (_) => _pick(context, l10n),
            ),
          ],
        ),
        _DateValue(key: const Key('onboarding.completionValue'), date: completion),
        if (error != null) InlineError(message: error),
        const SizedBox(height: AppSpacing.xs),
        Text(l10n.onboardingGoalCompletionHelp, style: textTheme.bodySmall),
      ],
    );
  }
}

/// "중간 점검일 (선택)": 완료일 3개월 전 / 직접 / 없음 (today − 1 year ~ completion).
class _CheckpointDate extends StatelessWidget {
  const _CheckpointDate({
    required this.checkpoint,
    required this.completion,
    required this.today,
    required this.submitError,
    required this.onChanged,
  });

  final LocalDate? checkpoint;
  final LocalDate? completion;
  final LocalDate today;
  final ApiException? submitError;
  final ValueChanged<LocalDate?> onChanged;

  Future<void> _pick(BuildContext context, AppLocalizations l10n, LocalDate? suggestion) async {
    final picked = await pickLocalDate(
      context,
      initialDate: checkpoint ?? suggestion,
      firstDate: today.addYears(-1),
      lastDate: completion ?? today.addYears(3),
      helpText: l10n.onboardingGoalCheckpoint,
    );
    if (picked != null) {
      onChanged(picked);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final beforeCompletion = OnboardingRules.checkpointThreeMonthsBefore(completion, today);
    final textTheme = Theme.of(context).textTheme;
    final error =
        goalDateMessage(GoalDateRules.checkpointViolation(checkpoint, completion, today), l10n) ??
        submitFieldMessage(submitError, 'learningGoal.checkpointDate', l10n);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l10n.onboardingGoalCheckpoint, style: textTheme.labelLarge),
        const SizedBox(height: AppSpacing.sm),
        Wrap(
          spacing: AppSpacing.sm,
          runSpacing: AppSpacing.sm,
          children: [
            ChoiceChip(
              key: const Key('onboarding.checkpoint.before3m'),
              label: Text(l10n.onboardingGoalQuickBefore3m),
              selected: beforeCompletion != null && checkpoint == beforeCompletion,
              onSelected: beforeCompletion == null ? null : (_) => onChanged(beforeCompletion),
            ),
            ChoiceChip(
              key: const Key('onboarding.checkpoint.custom'),
              label: Text(l10n.onboardingGoalQuickCheckpointCustom),
              selected: checkpoint != null && checkpoint != beforeCompletion,
              onSelected: (_) => _pick(context, l10n, beforeCompletion),
            ),
            ChoiceChip(
              key: const Key('onboarding.checkpoint.none'),
              label: Text(l10n.onboardingGoalQuickNone),
              selected: checkpoint == null,
              onSelected: (_) => onChanged(null),
            ),
          ],
        ),
        _DateValue(key: const Key('onboarding.checkpointValue'), date: checkpoint),
        if (error != null) InlineError(message: error),
        const SizedBox(height: AppSpacing.xs),
        Text(l10n.onboardingGoalCheckpointHelp, style: textTheme.bodySmall),
      ],
    );
  }
}

class _DateValue extends StatelessWidget {
  const _DateValue({super.key, required this.date});

  final LocalDate? date;

  @override
  Widget build(BuildContext context) {
    final value = date;
    if (value == null) {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.sm),
      child: Text(
        formatLongDate(value, AppLocalizations.of(context)),
        style: Theme.of(context).textTheme.bodyLarge,
      ),
    );
  }
}
