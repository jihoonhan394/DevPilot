import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/time_zone_support.dart';
import 'package:devpilot_app/core/widgets/labeled_dropdown.dart';
import 'package:devpilot_app/core/widgets/minutes_input_dialog.dart';
import 'package:devpilot_app/core/widgets/time_zone_picker.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_draft.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_rules.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_draft_controller.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_step_scaffold.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-ONBOARDING step 2 — study time and day boundary (`/onboarding/time`, docs/02 §3.4).
class OnboardingTimeScreen extends ConsumerWidget {
  const OnboardingTimeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final draft = ref.watch(onboardingDraftProvider);
    final timeZoneSupport = ref.watch(timeZoneSupportProvider);
    final notifier = ref.read(onboardingDraftProvider.notifier);
    final timeZone = OnboardingRules.timeZone(draft, timeZoneSupport);
    final textTheme = Theme.of(context).textTheme;
    return OnboardingStepScaffold(
      step: OnboardingStep.time,
      title: l10n.onboardingTimeTitle,
      backButton: TextButton(
        key: const Key('onboarding.backButton'),
        onPressed: () => context.go(AppRoutes.onboardingGoal),
        child: Text(l10n.onboardingBack),
      ),
      primaryButton: FilledButton(
        key: const Key('onboarding.nextButton'),
        onPressed: () => context.go(AppRoutes.onboardingLevel),
        child: Text(l10n.onboardingNext),
      ),
      children: [
        _MinutesChoice(
          keyPrefix: 'onboarding.weekday',
          label: l10n.onboardingTimeWeekday,
          choices: OnboardingDefaults.weekdayMinuteChoices,
          value: draft.weekdayStudyMinutes,
          onChanged: (minutes) =>
              notifier.edit((draft) => draft.copyWith(weekdayStudyMinutes: minutes)),
        ),
        const SizedBox(height: AppSpacing.lg),
        _MinutesChoice(
          keyPrefix: 'onboarding.weekend',
          label: l10n.onboardingTimeWeekend,
          choices: OnboardingDefaults.weekendMinuteChoices,
          value: draft.weekendStudyMinutes,
          onChanged: (minutes) =>
              notifier.edit((draft) => draft.copyWith(weekendStudyMinutes: minutes)),
        ),
        const SizedBox(height: AppSpacing.md),
        Text(
          l10n.onboardingTimeWeeklyTotal(
            formatMinutes(OnboardingRules.weeklyMinutes(draft), l10n),
          ),
          key: const Key('onboarding.weeklyTotal'),
          style: textTheme.bodyLarge,
        ),
        const SizedBox(height: AppSpacing.xl),
        _DayStartField(
          hour: draft.dayStartHour,
          onChanged: (hour) => notifier.edit((draft) => draft.copyWith(dayStartHour: hour)),
        ),
        const SizedBox(height: AppSpacing.xl),
        _TimeZoneRow(
          timeZone: timeZone,
          zones: timeZoneSupport.availableTimeZones,
          onChanged: (zone) => notifier.edit((draft) => draft.copyWith(timezone: zone)),
        ),
      ],
    );
  }
}

/// "하루 시작 시각" (0~6) with its effect on the plan-day.
class _DayStartField extends StatelessWidget {
  const _DayStartField({required this.hour, required this.onChanged});

  final int hour;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        LabeledDropdown<int>(
          key: const Key('onboarding.dayStartDropdown'),
          label: l10n.onboardingTimeDayStart,
          value: hour,
          items: OnboardingDefaults.dayStartHours,
          itemLabel: (hour) => formatDayStartHour(hour, l10n),
          onChanged: onChanged,
        ),
        const SizedBox(height: AppSpacing.xs),
        Text(
          l10n.onboardingTimeDayStartHelp(formatDayStartHour(hour, l10n)),
          style: Theme.of(context).textTheme.bodySmall,
        ),
      ],
    );
  }
}

/// "시간대 Asia/Seoul [변경]" with the searchable IANA picker.
class _TimeZoneRow extends StatelessWidget {
  const _TimeZoneRow({required this.timeZone, required this.zones, required this.onChanged});

  final String timeZone;
  final List<String> Function() zones;
  final ValueChanged<String> onChanged;

  Future<void> _pick(BuildContext context) async {
    final zone = await showTimeZonePicker(context, zones: zones(), current: timeZone);
    if (zone != null) {
      onChanged(zone);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Row(
      children: [
        Text(l10n.onboardingTimeTimezone, style: Theme.of(context).textTheme.labelLarge),
        const SizedBox(width: AppSpacing.md),
        Expanded(child: Text(timeZone, key: const Key('onboarding.timezoneValue'))),
        TextButton(
          key: const Key('onboarding.timezoneChangeButton'),
          onPressed: () => _pick(context),
          child: Text(l10n.onboardingTimeTimezoneChange),
        ),
      ],
    );
  }
}

/// Minute chips plus "직접 입력". A custom value shows as its own selected chip.
class _MinutesChoice extends StatelessWidget {
  const _MinutesChoice({
    required this.keyPrefix,
    required this.label,
    required this.choices,
    required this.value,
    required this.onChanged,
  });

  final String keyPrefix;
  final String label;
  final List<int> choices;
  final int value;
  final ValueChanged<int> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final isCustom = !choices.contains(value);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(label, style: Theme.of(context).textTheme.labelLarge),
        const SizedBox(height: AppSpacing.sm),
        Wrap(
          spacing: AppSpacing.sm,
          runSpacing: AppSpacing.sm,
          children: [
            for (final minutes in choices)
              ChoiceChip(
                key: Key('$keyPrefix.$minutes'),
                label: Text(formatMinutes(minutes, l10n)),
                selected: value == minutes,
                onSelected: (_) => onChanged(minutes),
              ),
            ChoiceChip(
              key: Key('$keyPrefix.custom'),
              label: Text(
                isCustom ? formatMinutes(value, l10n) : l10n.onboardingTimeCustom,
              ),
              selected: isCustom,
              onSelected: (_) async {
                final minutes = await showMinutesInputDialog(
                  context,
                  title: label,
                  initialMinutes: value,
                );
                if (minutes != null) {
                  onChanged(minutes);
                }
              },
            ),
          ],
        ),
      ],
    );
  }
}
