import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_rules.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// Common layout of the onboarding steps (docs/02 SCR-ONBOARDING "공통 레이아웃"): progress bar with
/// `n / 5`, title, scrolling body, and a bottom button area fixed to the screen edge. On wide screens
/// the content is a 560 px column in the middle.
///
/// The button area is the Scaffold's bottom bar, so toasts float above it instead of covering the
/// buttons.
class OnboardingStepScaffold extends StatelessWidget {
  const OnboardingStepScaffold({
    super.key,
    required this.step,
    required this.title,
    required this.children,
    required this.primaryButton,
    this.subtitle,
    this.backButton,
    this.secondaryButton,
  });

  final OnboardingStep step;
  final String title;
  final String? subtitle;
  final List<Widget> children;
  final Widget primaryButton;

  /// "이전", absent on steps 1 and 5.
  final Widget? backButton;

  /// Extra action next to the primary one (step 4 "건너뛰기").
  final Widget? secondaryButton;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    final subtitleText = subtitle;
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(
            AppSpacing.screen,
            AppSpacing.lg,
            AppSpacing.screen,
            AppSpacing.xl,
          ),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: AppBreakpoints.formCardWidth),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _StepProgress(step: step),
                  const SizedBox(height: AppSpacing.lg),
                  Semantics(header: true, child: Text(title, style: textTheme.titleLarge)),
                  if (subtitleText != null) ...[
                    const SizedBox(height: AppSpacing.xs),
                    Text(subtitleText, style: textTheme.bodyMedium),
                  ],
                  const SizedBox(height: AppSpacing.xl),
                  ...children,
                ],
              ),
            ),
          ),
        ),
      ),
      bottomNavigationBar: SafeArea(
        child: _BottomBar(
          backButton: backButton,
          secondaryButton: secondaryButton,
          primaryButton: primaryButton,
        ),
      ),
    );
  }
}

/// `StepProgress`: bar plus the `n / 5` text that screen readers read.
class _StepProgress extends StatelessWidget {
  const _StepProgress({required this.step});

  final OnboardingStep step;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: ExcludeSemantics(
            child: SelectionContainer.disabled(
              child: LinearProgressIndicator(
                value: step.number / OnboardingStep.count,
                minHeight: 4,
              ),
            ),
          ),
        ),
        const SizedBox(width: AppSpacing.md),
        Text(
          AppLocalizations.of(context).onboardingProgress(step.number),
          key: const Key('onboarding.progress'),
        ),
      ],
    );
  }
}

class _BottomBar extends StatelessWidget {
  const _BottomBar({
    required this.backButton,
    required this.secondaryButton,
    required this.primaryButton,
  });

  final Widget? backButton;
  final Widget? secondaryButton;
  final Widget primaryButton;

  @override
  Widget build(BuildContext context) {
    final back = backButton;
    final secondary = secondaryButton;
    final colorScheme = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: colorScheme.surface,
        border: Border(top: BorderSide(color: colorScheme.outlineVariant)),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.screen, vertical: AppSpacing.sm),
        child: Center(
          heightFactor: 1,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: AppBreakpoints.formCardWidth),
            child: Row(
              children: [
                ?back,
                const Spacer(),
                if (secondary != null) ...[secondary, const SizedBox(width: AppSpacing.sm)],
                Flexible(child: primaryButton),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
