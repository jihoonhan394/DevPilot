import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';

/// User-facing message for [error] (docs/08 §8.5, docs/02 §5.1): mapped code → ARB, else server
/// `detail`, else [AppLocalizations.errorGeneric].
String messageFor(Object error, AppLocalizations l10n) {
  if (error is! ApiException) {
    return l10n.errorGeneric;
  }
  final mapped = _mappedMessage(error.code, l10n);
  if (mapped != null) {
    return mapped;
  }
  final detail = error.detail;
  return detail == null || detail.isEmpty ? l10n.errorGeneric : detail;
}

String? _mappedMessage(String code, AppLocalizations l10n) => switch (code) {
  ApiErrorCode.validationFailed => l10n.errorValidationFailed,
  ApiErrorCode.unknownEnumValue => l10n.errorUnknownEnumValue,
  ApiErrorCode.malformedRequest => l10n.errorMalformedRequest,
  ApiErrorCode.idempotencyKeyRequired => l10n.errorIdempotencyKeyRequired,
  ApiErrorCode.invalidCursor => l10n.errorInvalidCursor,
  ApiErrorCode.authenticationRequired => l10n.errorAuthenticationRequired,
  ApiErrorCode.userNotAllowed => l10n.errorUserNotAllowed,
  ApiErrorCode.forbidden => l10n.errorForbidden,
  ApiErrorCode.resourceNotFound => l10n.errorResourceNotFound,
  ApiErrorCode.learningGoalNotFound => l10n.errorLearningGoalNotFound,
  ApiErrorCode.planNotFound => l10n.errorPlanNotFound,
  ApiErrorCode.onboardingRequired => l10n.errorOnboardingRequired,
  ApiErrorCode.onboardingAlreadyCompleted => l10n.errorOnboardingAlreadyCompleted,
  ApiErrorCode.activePlanExists => l10n.errorActivePlanExists,
  ApiErrorCode.planNotActive => l10n.errorPlanNotActive,
  ApiErrorCode.todayAlreadyStarted => l10n.errorTodayAlreadyStarted,
  ApiErrorCode.todayAlreadyCompleted => l10n.errorTodayAlreadyCompleted,
  ApiErrorCode.invalidStateTransition => l10n.errorInvalidStateTransition,
  ApiErrorCode.concurrentModification => l10n.errorConcurrentModification,
  ApiErrorCode.idempotencyInProgress => l10n.errorIdempotencyInProgress,
  ApiErrorCode.contentTooLarge => l10n.errorContentTooLarge,
  ApiErrorCode.requestTooLarge => l10n.errorRequestTooLarge,
  ApiErrorCode.idempotencyKeyReused => l10n.errorIdempotencyKeyReused,
  ApiErrorCode.secretDetectedBlocked => l10n.errorSecretDetectedBlocked,
  ApiErrorCode.selfExplanationRequired => l10n.errorSelfExplanationRequired,
  ApiErrorCode.hintConfirmationRequired => l10n.errorHintConfirmationRequired,
  ApiErrorCode.fullExampleNotAllowed => l10n.errorFullExampleNotAllowed,
  ApiErrorCode.submissionLimitReached => l10n.errorSubmissionLimitReached,
  ApiErrorCode.evaluationInProgress => l10n.errorEvaluationInProgress,
  ApiErrorCode.aiTaskNotRetryable => l10n.errorAiTaskNotRetryable,
  ApiErrorCode.aiDailyLimitExceeded => l10n.errorAiDailyLimitExceeded,
  ApiErrorCode.aiMonthlyBudgetExceeded => l10n.errorAiMonthlyBudgetExceeded,
  ApiErrorCode.aiConcurrencyLimit => l10n.errorAiConcurrencyLimit,
  ApiErrorCode.aiOutputInvalid => l10n.errorAiOutputInvalid,
  ApiErrorCode.aiRefused => l10n.errorAiRefused,
  ApiErrorCode.aiUnavailable => l10n.errorAiUnavailable,
  ApiErrorCode.aiTimeout => l10n.errorAiTimeout,
  ApiErrorCode.rateLimited => l10n.errorRateLimited,
  ApiErrorCode.networkError || ApiErrorCode.clientTimeout => l10n.errorNetworkError,
  ApiErrorCode.internalError => l10n.errorInternalError,
  _ => null,
};
