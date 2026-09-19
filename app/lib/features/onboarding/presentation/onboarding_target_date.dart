import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/validation_messages.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/validation/goal_date_rules.dart';
import 'package:devpilot_app/core/widgets/date_field.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_rules.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_copy.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// Step 1 "목표일": 3개월 후 / 6개월 후 / 1년 후 / 직접 선택 (tomorrow ~ today + 3 years,
/// docs/02 step 1). The date is the user's own; the chips only fill it in.
class OnboardingTargetDate extends StatelessWidget {
  const OnboardingTargetDate({
    super.key,
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
    final quickDates = {
      for (final months in OnboardingRules.quickTargetMonths)
        months: OnboardingRules.completionAfterMonths(today, months),
    };
    final error =
        goalDateMessage(GoalDateRules.completionViolation(completion, today), l10n) ??
        submitFieldMessage(submitError, 'learningGoal.targetCompletionDate', l10n);
    final date = completion;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l10n.onboardingGoalCompletion, style: textTheme.labelLarge),
        const SizedBox(height: AppSpacing.sm),
        Wrap(
          spacing: AppSpacing.sm,
          runSpacing: AppSpacing.sm,
          children: [
            for (final MapEntry(key: months, value: quick) in quickDates.entries)
              ChoiceChip(
                key: Key('onboarding.completion.quick${months}m'),
                label: Text(_quickLabel(months, l10n)),
                selected: completion == quick,
                onSelected: (_) => onChanged(quick),
              ),
            ChoiceChip(
              key: const Key('onboarding.completion.custom'),
              label: Text(l10n.onboardingGoalQuickCustom),
              selected: completion != null && !quickDates.containsValue(completion),
              onSelected: (_) => _pick(context, l10n),
            ),
          ],
        ),
        if (date != null)
          Padding(
            padding: const EdgeInsets.only(top: AppSpacing.sm),
            child: Text(
              formatLongDate(date, l10n),
              key: const Key('onboarding.completionValue'),
              style: textTheme.bodyLarge,
            ),
          ),
        if (error != null) InlineError(message: error),
        const SizedBox(height: AppSpacing.xs),
        Text(l10n.onboardingGoalCompletionHelp, style: textTheme.bodySmall),
      ],
    );
  }

  static String _quickLabel(int months, AppLocalizations l10n) => switch (months) {
    3 => l10n.onboardingGoalQuick3m,
    6 => l10n.onboardingGoalQuick6m,
    _ => l10n.onboardingGoalQuick1y,
  };
}
