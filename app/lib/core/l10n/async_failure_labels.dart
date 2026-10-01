import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';

/// `asyncFailure.<CODE>` of docs/02 §5.2: why an asynchronous AI step (evaluation, rubber duck
/// summary) did not finish.
extension AsyncFailureCodeMessage on AsyncFailureCode {
  String message(AppLocalizations l10n) => switch (this) {
    AsyncFailureCode.aiUnavailable => l10n.asyncFailureAiUnavailable,
    AsyncFailureCode.aiTimeout => l10n.asyncFailureAiTimeout,
    AsyncFailureCode.aiRefused => l10n.asyncFailureAiRefused,
    AsyncFailureCode.aiOutputInvalid => l10n.asyncFailureAiOutputInvalid,
    AsyncFailureCode.aiBudgetExceeded => l10n.asyncFailureAiBudgetExceeded,
    AsyncFailureCode.aiRateLimited => l10n.asyncFailureAiRateLimited,
    AsyncFailureCode.confidentialSuspected => l10n.asyncFailureConfidentialSuspected,
    AsyncFailureCode.interrupted => l10n.asyncFailureInterrupted,
    AsyncFailureCode.internalError || AsyncFailureCode.unknown => l10n.asyncFailureInternalError,
  };

  /// The same input would fail again, so the screen asks for a new request instead of offering
  /// "다시 평가" (docs/02 §5.2 재시도 버튼 column).
  bool get hidesRetry =>
      this == AsyncFailureCode.aiRefused || this == AsyncFailureCode.confidentialSuspected;

  /// A budget failure keeps its retry button but disabled until the limit recovers.
  bool get retryWaitsForBudget => this == AsyncFailureCode.aiBudgetExceeded;
}
