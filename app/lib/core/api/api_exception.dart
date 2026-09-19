/// Error codes the client branches on (docs/05 §1.3, docs/02 §5.1).
///
/// Server codes arrive in the Problem Details `code` field. `NETWORK_ERROR` and `CLIENT_TIMEOUT` are
/// produced by the client only.
abstract final class ApiErrorCode {
  static const validationFailed = 'VALIDATION_FAILED';
  static const unknownEnumValue = 'UNKNOWN_ENUM_VALUE';
  static const malformedRequest = 'MALFORMED_REQUEST';
  static const idempotencyKeyRequired = 'IDEMPOTENCY_KEY_REQUIRED';
  static const invalidCursor = 'INVALID_CURSOR';
  static const authenticationRequired = 'AUTHENTICATION_REQUIRED';
  static const userNotAllowed = 'USER_NOT_ALLOWED';
  static const forbidden = 'FORBIDDEN';
  static const resourceNotFound = 'RESOURCE_NOT_FOUND';
  static const learningGoalNotFound = 'LEARNING_GOAL_NOT_FOUND';
  static const planNotFound = 'PLAN_NOT_FOUND';
  static const todayNotGenerated = 'TODAY_NOT_GENERATED';
  static const onboardingRequired = 'ONBOARDING_REQUIRED';
  static const onboardingAlreadyCompleted = 'ONBOARDING_ALREADY_COMPLETED';
  static const activePlanExists = 'ACTIVE_PLAN_EXISTS';
  static const planNotActive = 'PLAN_NOT_ACTIVE';
  static const todayAlreadyStarted = 'TODAY_ALREADY_STARTED';
  static const todayAlreadyCompleted = 'TODAY_ALREADY_COMPLETED';
  static const invalidStateTransition = 'INVALID_STATE_TRANSITION';
  static const concurrentModification = 'CONCURRENT_MODIFICATION';
  static const idempotencyInProgress = 'IDEMPOTENCY_IN_PROGRESS';
  static const contentTooLarge = 'CONTENT_TOO_LARGE';
  static const requestTooLarge = 'REQUEST_TOO_LARGE';
  static const idempotencyKeyReused = 'IDEMPOTENCY_KEY_REUSED';
  static const secretDetectedBlocked = 'SECRET_DETECTED_BLOCKED';
  static const selfExplanationRequired = 'SELF_EXPLANATION_REQUIRED';
  static const hintConfirmationRequired = 'HINT_CONFIRMATION_REQUIRED';
  static const fullExampleNotAllowed = 'FULL_EXAMPLE_NOT_ALLOWED';
  static const submissionLimitReached = 'SUBMISSION_LIMIT_REACHED';
  static const evaluationInProgress = 'EVALUATION_IN_PROGRESS';
  static const aiTaskNotRetryable = 'AI_TASK_NOT_RETRYABLE';
  static const aiDailyLimitExceeded = 'AI_DAILY_LIMIT_EXCEEDED';
  static const aiMonthlyBudgetExceeded = 'AI_MONTHLY_BUDGET_EXCEEDED';
  static const aiConcurrencyLimit = 'AI_CONCURRENCY_LIMIT';
  static const aiOutputInvalid = 'AI_OUTPUT_INVALID';
  static const aiRefused = 'AI_REFUSED';
  static const aiUnavailable = 'AI_UNAVAILABLE';
  static const aiTimeout = 'AI_TIMEOUT';
  static const rateLimited = 'RATE_LIMITED';
  static const internalError = 'INTERNAL_ERROR';
  static const networkError = 'NETWORK_ERROR';
  static const clientTimeout = 'CLIENT_TIMEOUT';

  /// AI answers that change `GET /me.aiStatus` or the usage numbers: the profile is read again
  /// right after them (docs/02 §2.4, §6.5).
  static const aiStatusCodes = {
    aiDailyLimitExceeded,
    aiMonthlyBudgetExceeded,
    aiConcurrencyLimit,
    aiUnavailable,
  };
}

/// Field error codes of `errors[].code` that screens map to their own copy (docs/05 §1.2.3).
abstract final class ApiFieldErrorCode {
  static const url = 'URL';
  static const notBlankIfPresent = 'NOT_BLANK_IF_PRESENT';
  static const actualMinutesExceedsElapsed = 'ACTUAL_MINUTES_EXCEEDS_ELAPSED';
  static const referenceNotFound = 'REFERENCE_NOT_FOUND';
}

/// One entry of the Problem Details `errors` array (docs/05 §1.2.1 `FieldError`).
final class ApiFieldError {
  const ApiFieldError({required this.field, required this.code, this.message});

  /// JSON path such as `learningGoal.targetCompletionDate` or `milestones[0].endDate`.
  final String field;

  /// Bean Validation annotation name (`Size`) or domain code (`DATE_OUT_OF_RANGE`).
  final String code;

  /// Korean sentence from the server, shown under the matching field.
  final String? message;
}

/// Failure of an API call, parsed from an RFC 9457 Problem Details body.
///
/// Callers branch on [code] only, never on [status] or [detail] (docs/05 §1.2.1).
final class ApiException implements Exception {
  const ApiException({
    required this.code,
    this.status,
    this.detail,
    this.traceId,
    this.fieldErrors = const [],
    this.occurredAt,
    this.retryAfter,
  });

  final String code;

  /// HTTP status. Null for client-side failures that never reached the server.
  final int? status;

  /// User-facing Korean sentence from the server, if any.
  final String? detail;

  /// 32 hex chars. Shown only in the collapsed report section of error views.
  final String? traceId;

  final List<ApiFieldError> fieldErrors;

  /// UTC time the client received the failure. Shown in the report section.
  final DateTime? occurredAt;

  /// `Retry-After` of a 429 or 503 (docs/05 §1.9.3): how long to wait before the same call.
  final Duration? retryAfter;

  /// True when the request never got a response: the same request may be sent again.
  bool get isNetworkFailure =>
      code == ApiErrorCode.networkError || code == ApiErrorCode.clientTimeout;

  /// Field errors whose path is [field] or starts with `field.` / `field[`.
  List<ApiFieldError> fieldErrorsUnder(String field) => [
    for (final error in fieldErrors)
      if (error.field == field ||
          error.field.startsWith('$field.') ||
          error.field.startsWith('$field['))
        error,
  ];

  /// Server message of the first field error under [field], shown below that input
  /// (docs/02 §3.2). Falls back to the generic validation text when the server sent none.
  String? fieldMessage(String field, {required String fallback}) {
    final errors = fieldErrorsUnder(field);
    if (errors.isEmpty) {
      return null;
    }
    return errors.first.message ?? fallback;
  }

  @override
  String toString() => 'ApiException(code: $code, status: $status, traceId: $traceId)';
}
