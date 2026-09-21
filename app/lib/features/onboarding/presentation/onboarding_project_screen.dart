import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/time_zone_support.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/confirm_dialog.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_rules.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_copy.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_draft_controller.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_step_scaffold.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_submit_controller.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-ONBOARDING step 4 — side project and submit (`/onboarding/project`, docs/02 §3.4).
///
/// The name starts as "주문 시스템"; the client fills it in (docs/05 §4.1 step 10). Only the name
/// can be changed here; the rest is edited later on SCR-PROJECTS.
class OnboardingProjectScreen extends ConsumerStatefulWidget {
  const OnboardingProjectScreen({super.key});

  @override
  ConsumerState<OnboardingProjectScreen> createState() => _OnboardingProjectScreenState();
}

class _OnboardingProjectScreenState extends ConsumerState<OnboardingProjectScreen> {
  TextEditingController? _nameController;

  TextEditingController _controllerFor(AppLocalizations l10n) =>
      _nameController ??= TextEditingController(
        text: ref.read(onboardingDraftProvider).projectName ?? l10n.onboardingProjectDefaultName,
      );

  @override
  void dispose() {
    _nameController?.dispose();
    super.dispose();
  }

  Future<void> _submit({required bool withProject}) async {
    final l10n = AppLocalizations.of(context);
    final draft = ref.read(onboardingDraftProvider);
    final outcome = await ref
        .read(onboardingSubmitControllerProvider.notifier)
        .submit(
          displayName: draft.displayName ?? ref.read(meProvider).requireValue.displayName,
          timezone: OnboardingRules.timeZone(draft, ref.read(timeZoneSupportProvider)),
          projectName: _controllerFor(l10n).text,
          projectDescription: l10n.onboardingProjectDefaultDescription,
          withProject: withProject,
        );
    if (!mounted) {
      return;
    }
    switch (outcome) {
      case OnboardingSubmitted():
        context.go(AppRoutes.onboardingPlan);
      case OnboardingAlreadyCompleted():
        showToast(context, l10n.errorOnboardingAlreadyCompleted);
        context.go(AppRoutes.start);
      case OnboardingValidationFailed(:final step) when step != OnboardingStep.project:
        context.go(switch (step) {
          OnboardingStep.goal => AppRoutes.onboardingGoal,
          OnboardingStep.time => AppRoutes.onboardingTime,
          _ => AppRoutes.onboardingLevel,
        });
      case OnboardingValidationFailed() || OnboardingSubmitIgnored():
        return;
      case OnboardingSubmitFailed(:final error):
        if (error is ApiException && error.code == ApiErrorCode.secretDetectedBlocked) {
          return; // Shown under the name field.
        }
        await presentActionError(context, ref, error);
    }
  }

  Future<void> _confirmSkip() async {
    final l10n = AppLocalizations.of(context);
    final skip = await showConfirmDialog(
      context,
      title: l10n.onboardingProjectSkipTitle,
      body: l10n.onboardingProjectSkipBody,
      cancelLabel: l10n.onboardingProjectSkipCancel,
      confirmLabel: l10n.onboardingProjectSkipConfirm,
      confirmKey: const Key('onboarding.skipConfirmButton'),
    );
    if (skip) {
      await _submit(withProject: false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final controller = _controllerFor(l10n);
    final submitState = ref.watch(onboardingSubmitControllerProvider);
    final isSubmitting = submitState.isSubmitting;
    return PopScope(
      canPop: !isSubmitting,
      child: ValueListenableBuilder<TextEditingValue>(
        valueListenable: controller,
        builder: (context, nameValue, _) {
          final nameValid = InputRules.isRequiredText(
            nameValue.text,
            InputRules.projectNameMaxLength,
          );
          return OnboardingStepScaffold(
            step: OnboardingStep.project,
            title: l10n.onboardingProjectTitle,
            subtitle: l10n.onboardingProjectSubtitle,
            backButton: TextButton(
              key: const Key('onboarding.backButton'),
              onPressed: isSubmitting ? null : () => context.go(AppRoutes.onboardingLevel),
              child: Text(l10n.onboardingBack),
            ),
            secondaryButton: TextButton(
              key: const Key('onboarding.skipButton'),
              onPressed: isSubmitting ? null : _confirmSkip,
              child: Text(l10n.onboardingProjectSkip),
            ),
            primaryButton: _CreatePlanButton(
              isSubmitting: isSubmitting,
              onPressed: nameValid && !isSubmitting ? () => _submit(withProject: true) : null,
            ),
            children: [
              TextField(
                key: const Key('onboarding.projectNameField'),
                controller: controller,
                enabled: !isSubmitting,
                maxLength: InputRules.projectNameMaxLength,
                decoration: InputDecoration(
                  labelText: l10n.onboardingProjectName,
                  errorText: _nameError(nameValue.text, nameValid, submitState.error, l10n),
                ),
                onChanged: (value) => ref
                    .read(onboardingDraftProvider.notifier)
                    .edit((draft) => draft.copyWith(projectName: value)),
              ),
              const SizedBox(height: AppSpacing.lg),
              const _ReferenceProjectCard(),
              const SizedBox(height: AppSpacing.md),
              Text(l10n.onboardingProjectLater, style: Theme.of(context).textTheme.bodySmall),
            ],
          );
        },
      ),
    );
  }

  static String? _nameError(
    String name,
    bool valid,
    ApiException? submitError,
    AppLocalizations l10n,
  ) {
    if (!valid) {
      return name.trim().isEmpty
          ? l10n.validationRequired
          : l10n.validationMaxLength(InputRules.projectNameMaxLength);
    }
    if (submitError?.code == ApiErrorCode.secretDetectedBlocked) {
      return l10n.errorSecretDetectedBlocked;
    }
    return submitFieldMessage(submitError, 'sideProject', l10n);
  }
}

/// "계획 만들기" with an in-button progress indicator while `POST /onboarding` runs.
class _CreatePlanButton extends StatelessWidget {
  const _CreatePlanButton({required this.isSubmitting, required this.onPressed});

  final bool isSubmitting;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return FilledButton(
      key: const Key('onboarding.submitButton'),
      onPressed: onPressed,
      child: isSubmitting
          ? SizedBox.square(
              dimension: 20,
              child: SelectionContainer.disabled(
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  semanticsLabel: l10n.commonSubmitting,
                ),
              ),
            )
          : Text(l10n.onboardingSubmit),
    );
  }
}

/// `ReferenceProjectCard`: fixed summary of the template milestone order (docs/02 step 4).
class _ReferenceProjectCard extends StatelessWidget {
  const _ReferenceProjectCard();

  @override
  Widget build(BuildContext context) {
    return Card(
      key: const Key('onboarding.referenceProjectCard'),
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Text(AppLocalizations.of(context).onboardingProjectReference),
      ),
    );
  }
}
