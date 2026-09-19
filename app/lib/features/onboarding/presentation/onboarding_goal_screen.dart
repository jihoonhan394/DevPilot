import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/l10n/validation_messages.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/validation/goal_date_rules.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/core/widgets/date_field.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_rules.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_copy.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_draft_controller.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_goal_dates.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_step_scaffold.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_submit_controller.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-ONBOARDING step 1 — goal (`/onboarding/goal`, docs/02 §3.4).
class OnboardingGoalScreen extends ConsumerStatefulWidget {
  const OnboardingGoalScreen({super.key});

  @override
  ConsumerState<OnboardingGoalScreen> createState() => _OnboardingGoalScreenState();
}

class _OnboardingGoalScreenState extends ConsumerState<OnboardingGoalScreen> {
  late final TextEditingController _nameController;

  @override
  void initState() {
    super.initState();
    final draft = ref.read(onboardingDraftProvider);
    _nameController = TextEditingController(
      text: draft.displayName ?? ref.read(meProvider).value?.displayName ?? '',
    );
  }

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  void _editDraft(OnboardingDraftEdit change) =>
      ref.read(onboardingDraftProvider.notifier).edit(change);

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final draft = ref.watch(onboardingDraftProvider);
    final submitError = ref.watch(onboardingSubmitControllerProvider).error;
    final today = profileToday(ref);
    final experienceStart = LocalDate.tryParse(draft.experienceStartDate);
    return ValueListenableBuilder<TextEditingValue>(
      valueListenable: _nameController,
      builder: (context, nameValue, _) {
        return OnboardingStepScaffold(
          step: OnboardingStep.goal,
          title: l10n.onboardingGoalTitle,
          primaryButton: FilledButton(
            key: const Key('onboarding.nextButton'),
            onPressed: OnboardingRules.canLeaveGoalStep(draft, nameValue.text, today)
                ? () => context.go(AppRoutes.onboardingTime)
                : null,
            child: Text(l10n.onboardingNext),
          ),
          children: [
            _DisplayNameField(
              controller: _nameController,
              serverError: submitFieldMessage(submitError, 'displayName', l10n),
              onChanged: (value) => _editDraft((draft) => draft.copyWith(displayName: value)),
            ),
            const SizedBox(height: AppSpacing.lg),
            InputDecorator(
              key: const Key('onboarding.roleField'),
              decoration: InputDecoration(labelText: l10n.onboardingGoalRole, enabled: false),
              child: Text(TargetRole.javaBackend.label(l10n)),
            ),
            const SizedBox(height: AppSpacing.xl),
            _ExperienceProfileGroup(
              value: draft.experienceProfile,
              onChanged: (profile) =>
                  _editDraft((draft) => draft.copyWith(experienceProfile: profile)),
            ),
            const SizedBox(height: AppSpacing.lg),
            _ExperienceStartField(
              experienceStart: experienceStart,
              today: today,
              serverError: submitFieldMessage(submitError, 'experienceStartDate', l10n),
              onChanged: (date) =>
                  _editDraft((draft) => draft.copyWith(experienceStartDate: date?.toIso())),
            ),
            const SizedBox(height: AppSpacing.xl),
            OnboardingGoalDates(
              draft: draft,
              today: today,
              submitError: submitError,
              onChanged: _editDraft,
            ),
          ],
        );
      },
    );
  }
}

/// "표시 이름": 1~100 characters after trimming (docs/02 §3.2).
class _DisplayNameField extends StatelessWidget {
  const _DisplayNameField({
    required this.controller,
    required this.serverError,
    required this.onChanged,
  });

  final TextEditingController controller;
  final String? serverError;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final name = controller.text;
    final String? error;
    if (name.trim().isEmpty) {
      error = l10n.validationRequired;
    } else if (!InputRules.isValidDisplayName(name)) {
      error = l10n.validationMaxLength(InputRules.displayNameMaxLength);
    } else {
      error = serverError;
    }
    return TextField(
      key: const Key('onboarding.displayNameField'),
      controller: controller,
      maxLength: InputRules.displayNameMaxLength,
      decoration: InputDecoration(labelText: l10n.onboardingGoalDisplayName, errorText: error),
      onChanged: onChanged,
    );
  }
}

/// "개발 시작일 (선택)": 1970-01-01 ~ today, with a clear button once set.
class _ExperienceStartField extends StatelessWidget {
  const _ExperienceStartField({
    required this.experienceStart,
    required this.today,
    required this.serverError,
    required this.onChanged,
  });

  final LocalDate? experienceStart;
  final LocalDate today;
  final String? serverError;
  final ValueChanged<LocalDate?> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        DateField(
          key: const Key('onboarding.experienceStartField'),
          label: l10n.onboardingGoalExperienceStart,
          value: experienceStart,
          firstDate: GoalDateRules.earliestExperienceStart,
          lastDate: today,
          errorText:
              goalDateMessage(
                GoalDateRules.experienceStartViolation(experienceStart, today),
                l10n,
              ) ??
              serverError,
          onChanged: onChanged,
        ),
        if (experienceStart != null)
          Align(
            alignment: Alignment.centerRight,
            child: TextButton(
              key: const Key('onboarding.experienceStartClearButton'),
              onPressed: () => onChanged(null),
              child: Text(l10n.onboardingGoalExperienceStartClear),
            ),
          ),
      ],
    );
  }
}

class _ExperienceProfileGroup extends StatelessWidget {
  const _ExperienceProfileGroup({required this.value, required this.onChanged});

  final ExperienceProfile? value;
  final ValueChanged<ExperienceProfile> onChanged;

  static const _choices = [
    ExperienceProfile.workingDeveloper,
    ExperienceProfile.developerStarter,
    ExperienceProfile.other,
  ];

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l10n.onboardingGoalProfile, style: Theme.of(context).textTheme.labelLarge),
        RadioGroup<ExperienceProfile>(
          groupValue: value,
          onChanged: (selected) {
            if (selected != null) {
              onChanged(selected);
            }
          },
          child: Column(
            children: [
              for (final choice in _choices)
                RadioListTile<ExperienceProfile>(
                  key: Key('onboarding.profile.${choice.name}'),
                  value: choice,
                  contentPadding: EdgeInsets.zero,
                  title: Text(choice.label(l10n)),
                  subtitle: switch (choice) {
                    ExperienceProfile.workingDeveloper => Text(
                      l10n.onboardingGoalProfileWorkingDesc,
                    ),
                    ExperienceProfile.developerStarter => Text(
                      l10n.onboardingGoalProfileStarterDesc,
                    ),
                    _ => null,
                  },
                ),
            ],
          ),
        ),
      ],
    );
  }
}
