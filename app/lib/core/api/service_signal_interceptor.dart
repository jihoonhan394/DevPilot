import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/problem_details_interceptor.dart';
import 'package:dio/dio.dart';

/// Reacts to answers that change app-wide state rather than the failed call alone.
///
/// - AI `429`/`503` (`AI_*`): the AI status or usage changed, so `GET /me` is read again and every
///   AI button and banner follows the new `aiStatus` (docs/02 §2.4, §6.5).
/// - `429 RATE_LIMITED`: polling stops until `Retry-After` has passed (docs/02 §6.6). The call
///   itself is not retried; the screen shows the toast and keeps the input.
final class ServiceSignalInterceptor extends Interceptor {
  ServiceSignalInterceptor({required this._onAiStatusChanged, required this._onRateLimited});

  final void Function() _onAiStatusChanged;
  final void Function(Duration? retryAfter) _onRateLimited;

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    if (err.response != null) {
      final failure = ProblemDetailsInterceptor.toApiException(err);
      if (ApiErrorCode.aiStatusCodes.contains(failure.code)) {
        _onAiStatusChanged();
      } else if (failure.code == ApiErrorCode.rateLimited) {
        _onRateLimited(failure.retryAfter);
      }
    }
    handler.next(err);
  }
}
