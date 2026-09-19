import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/onboarding/data/onboarding_models.dart';
import 'package:devpilot_app/features/onboarding/data/onboarding_repository.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_rules.dart';
import 'package:devpilot_app/features/onboarding/presentation/onboarding_draft_controller.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Result of step 4 kept for step 5 (`POST /onboarding` response plus the chosen mode).
final class OnboardingResult {
  const OnboardingResult({required this.runDiagnostic, required this.response});

  final bool runDiagnostic;
  final OnboardingResponse response;
}

/// Holds the latest [OnboardingResult] in memory. A reload loses it; step 5 then shows the plan
/// with the self-assessment buttons only.
final class OnboardingResultHolder extends Notifier<OnboardingResult?> {
  @override
  OnboardingResult? build() => null;

  void store(OnboardingResult result) => state = result;
}

final onboardingResultProvider = NotifierProvider<OnboardingResultHolder, OnboardingResult?>(
  OnboardingResultHolder.new,
);

/// What the step 4 screen does after a submit.
sealed class OnboardingSubmitOutcome {
  const OnboardingSubmitOutcome();
}

final class OnboardingSubmitted extends OnboardingSubmitOutcome {
  const OnboardingSubmitted();
}

/// `ONBOARDING_ALREADY_COMPLETED` (another tab finished first): go to the start page.
final class OnboardingAlreadyCompleted extends OnboardingSubmitOutcome {
  const OnboardingAlreadyCompleted();
}

/// `VALIDATION_FAILED`: show the step that owns the first field error.
final class OnboardingValidationFailed extends OnboardingSubmitOutcome {
  const OnboardingValidationFailed(this.step);

  final OnboardingStep step;
}

final class OnboardingSubmitFailed extends OnboardingSubmitOutcome {
  const OnboardingSubmitFailed(this.error);

  final Object error;
}

/// A second tap while the first request runs.
final class OnboardingSubmitIgnored extends OnboardingSubmitOutcome {
  const OnboardingSubmitIgnored();
}

final class OnboardingSubmitState {
  const OnboardingSubmitState({this.isSubmitting = false, this.error});

  final bool isSubmitting;

  /// Last failure; steps read its field errors to show them under their inputs.
  final ApiException? error;
}

/// `POST /onboarding` for step 4 "계획 만들기" and "건너뛰고 계획 만들기" (docs/02 step 4).
final class OnboardingSubmitController extends Notifier<OnboardingSubmitState> {
  final _keys = IdempotencyKeyCache();

  @override
  OnboardingSubmitState build() => const OnboardingSubmitState();

  Future<OnboardingSubmitOutcome> submit({
    required String displayName,
    required String timezone,
    required String projectName,
    required String projectDescription,
    required bool withProject,
  }) async {
    if (state.isSubmitting) {
      return const OnboardingSubmitIgnored();
    }
    final request = OnboardingRules.buildRequest(
      draft: ref.read(onboardingDraftProvider),
      displayName: displayName,
      timezone: timezone,
      projectName: projectName,
      projectDescription: projectDescription,
      withProject: withProject,
    );
    state = const OnboardingSubmitState(isSubmitting: true);
    final idempotencyKey = _keys.keyFor(request.toJson());
    try {
      final response = await ref
          .read(onboardingRepositoryProvider)
          .completeOnboarding(request, idempotencyKey: idempotencyKey);
      _keys.settle(null);
      ref
          .read(onboardingResultProvider.notifier)
          .store(OnboardingResult(runDiagnostic: request.runDiagnostic, response: response));
      ref.read(onboardingDraftProvider.notifier).clear();
      state = const OnboardingSubmitState();
      // Last: the new profile lets the router leave the onboarding input steps.
      ref.read(meProvider.notifier).replace(response.user);
      return const OnboardingSubmitted();
    } on ApiException catch (error) {
      _keys.settle(error);
      state = OnboardingSubmitState(error: error);
      switch (error.code) {
        case ApiErrorCode.onboardingAlreadyCompleted:
          await ref.read(meProvider.notifier).refresh();
          return const OnboardingAlreadyCompleted();
        case ApiErrorCode.validationFailed:
          return OnboardingValidationFailed(OnboardingRules.stepForFieldErrors(error.fieldErrors));
        default:
          return OnboardingSubmitFailed(error);
      }
    } finally {
      // An unexpected response shape must not leave the button locked.
      if (state.isSubmitting) {
        state = const OnboardingSubmitState();
      }
    }
  }
}

final onboardingSubmitControllerProvider =
    NotifierProvider<OnboardingSubmitController, OnboardingSubmitState>(
      OnboardingSubmitController.new,
    );
