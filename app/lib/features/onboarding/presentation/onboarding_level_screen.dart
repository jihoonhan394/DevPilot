import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/status_badge.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_draft.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_rules.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_copy.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_draft_controller.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_focus_skills.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_step_scaffold.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_submit_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-ONBOARDING step 3 — short diagnostic or self-assessment (`/onboarding/level`, docs/02 §3.4).
///
/// The diagnostic is the default. Its questions are not solved here: the server returns them as
/// `suggestedDiagnostics` after submit (always empty before S3).
class OnboardingLevelScreen extends ConsumerWidget {
  const OnboardingLevelScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final draft = ref.watch(onboardingDraftProvider);
    final notifier = ref.read(onboardingDraftProvider.notifier);
    final submitError = ref.watch(onboardingSubmitControllerProvider).error;
    final selfAssessmentError = submitFieldMessage(submitError, 'selfAssessments', l10n);
    return OnboardingStepScaffold(
      step: OnboardingStep.level,
      title: l10n.onboardingLevelTitle,
      backButton: TextButton(
        key: const Key('onboarding.backButton'),
        onPressed: () => context.go(AppRoutes.onboardingTime),
        child: Text(l10n.onboardingBack),
      ),
      primaryButton: FilledButton(
        key: const Key('onboarding.nextButton'),
        onPressed: () => context.go(AppRoutes.onboardingProject),
        child: Text(l10n.onboardingNext),
      ),
      children: [
        _LevelModeCards(
          runDiagnostic: draft.runDiagnostic,
          onChanged: (runDiagnostic) =>
              notifier.edit((draft) => draft.copyWith(runDiagnostic: runDiagnostic)),
        ),
        if (!draft.runDiagnostic)
          _SelfAssessment(
            draft: draft,
            onChanged: (category, level) => notifier.edit(
              (draft) => draft.copyWith(
                selfAssessmentLevels: {...draft.selfAssessmentLevels, category: level},
              ),
            ),
          ),
        if (selfAssessmentError != null) InlineError(message: selfAssessmentError),
        const SizedBox(height: AppSpacing.lg),
        OnboardingFocusSkills(
          selectedCodes: draft.focusSkillCodes,
          errorText: submitFieldMessage(submitError, OnboardingRules.focusSkillCodesField, l10n),
          onChanged: (codes) => notifier.edit((draft) => draft.copyWith(focusSkillCodes: codes)),
        ),
      ],
    );
  }
}

/// `LevelModeCards`: short diagnostic (default, recommended) or self-assessment.
class _LevelModeCards extends StatelessWidget {
  const _LevelModeCards({required this.runDiagnostic, required this.onChanged});

  final bool runDiagnostic;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return RadioGroup<bool>(
      groupValue: runDiagnostic,
      onChanged: (value) {
        if (value != null) {
          onChanged(value);
        }
      },
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _ModeCard(
            tileKey: const Key('onboarding.level.diagnostic'),
            value: true,
            title: l10n.onboardingLevelDiagnostic,
            description: l10n.onboardingLevelDiagnosticDesc,
            badge: l10n.onboardingLevelDiagnosticBadge,
          ),
          const SizedBox(height: AppSpacing.md),
          _ModeCard(
            tileKey: const Key('onboarding.level.self'),
            value: false,
            title: l10n.onboardingLevelSelf,
            description: l10n.onboardingLevelSelfDesc,
          ),
        ],
      ),
    );
  }
}

/// Self-assessment chips with the help text and, from level 3, the diagnostic note.
class _SelfAssessment extends StatelessWidget {
  const _SelfAssessment({required this.draft, required this.onChanged});

  final OnboardingDraft draft;
  final void Function(SkillCategory category, int level) onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: AppSpacing.lg),
        Text(l10n.onboardingLevelHelp, style: textTheme.bodySmall),
        const SizedBox(height: AppSpacing.sm),
        _SelfAssessmentChips(levels: draft.selfAssessmentLevels, onChanged: onChanged),
        if (OnboardingRules.hasHighSelfAssessment(draft)) ...[
          const SizedBox(height: AppSpacing.sm),
          Text(
            l10n.onboardingLevelDiagnosticNote,
            key: const Key('onboarding.level.diagnosticNote'),
            style: textTheme.bodySmall,
          ),
        ],
      ],
    );
  }
}

class _ModeCard extends StatelessWidget {
  const _ModeCard({
    required this.tileKey,
    required this.value,
    required this.title,
    required this.description,
    this.badge,
  });

  final Key tileKey;
  final bool value;
  final String title;
  final String description;
  final String? badge;

  @override
  Widget build(BuildContext context) {
    final badgeLabel = badge;
    return Card(
      child: RadioListTile<bool>(
        key: tileKey,
        value: value,
        title: Wrap(
          spacing: AppSpacing.sm,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Text(title),
            if (badgeLabel != null) StatusBadge(label: badgeLabel, tone: AppTone.primary),
          ],
        ),
        subtitle: Text(description),
      ),
    );
  }
}

/// 13 categories × 5 chips (0~4), in `SkillCategory` order (docs/02 step 3).
class _SelfAssessmentChips extends StatelessWidget {
  const _SelfAssessmentChips({required this.levels, required this.onChanged});

  final Map<SkillCategory, int> levels;
  final void Function(SkillCategory category, int level) onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final category in SkillCategory.known) ...[
          const SizedBox(height: AppSpacing.md),
          Text(category.label(l10n), style: Theme.of(context).textTheme.labelLarge),
          const SizedBox(height: AppSpacing.xs),
          Wrap(
            spacing: AppSpacing.xs,
            runSpacing: AppSpacing.xs,
            children: [
              for (var level = 0; level <= OnboardingDefaults.maxSelfAssessmentLevel; level++)
                ChoiceChip(
                  key: Key('onboarding.level.${category.name}.$level'),
                  label: Text(skillLevelLabel(level, l10n)),
                  selected: (levels[category] ?? 0) == level,
                  onSelected: (_) => onChanged(category, level),
                ),
            ],
          ),
        ],
      ],
    );
  }
}
