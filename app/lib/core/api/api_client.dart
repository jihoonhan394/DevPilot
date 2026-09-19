import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/auth_token_interceptor.dart';
import 'package:devpilot_app/core/api/dio_provider.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/api/idempotency_key_interceptor.dart';
import 'package:devpilot_app/core/api/problem_details_interceptor.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Waits between automatic retries. Replaced in tests so they do not sleep.
typedef RetryDelay = Future<void> Function(Duration duration);

/// JSON calls against `/api/v1`. Every failure surfaces as an [ApiException].
///
/// Automatic retries follow docs/02 §6.6: failures without a response are retried twice (after 1 s
/// and 3 s) with the same request, so a POST keeps its `Idempotency-Key`. GET also retries 502, 503
/// and 504. `IDEMPOTENCY_IN_PROGRESS` is retried every second up to five times (docs/05 §1.7).
/// `RATE_LIMITED` and every other status are returned to the caller.
final class ApiClient {
  ApiClient(this._dio, {RetryDelay? retryDelay}) : _retryDelay = retryDelay ?? _wait;

  static const networkRetryDelays = [Duration(seconds: 1), Duration(seconds: 3)];
  static const inProgressRetryDelay = Duration(seconds: 1);
  static const maxInProgressRetries = 5;
  static const _retriedGetStatuses = {502, 503, 504};

  final Dio _dio;
  final RetryDelay _retryDelay;

  /// GET [path] (relative to `/api/v1`) and return the JSON object body.
  Future<Map<String, Object?>> getJson(String path, {Map<String, Object?>? queryParameters}) =>
      _sendJson(
        isGet: true,
        () => _dio.get<Object?>(path, queryParameters: queryParameters),
      );

  /// Authenticated POST that creates a resource: [idempotencyKey] is required (docs/05 §1.7).
  Future<Map<String, Object?>> postJson(
    String path, {
    required Map<String, Object?> body,
    required IdempotencyKey idempotencyKey,
  }) => _sendJson(
    () => _dio.post<Object?>(
      path,
      data: body,
      options: Options(extra: {IdempotencyKeyInterceptor.extraKey: idempotencyKey.value}),
    ),
  );

  /// Authenticated POST outside docs/05 §1.7: `…/replan/preview` (nothing is stored) and
  /// `…/abandon` (an idempotent state transition). No `Idempotency-Key` header is sent.
  Future<Map<String, Object?>> postWithoutIdempotencyKey(
    String path, {
    Map<String, Object?>? body,
  }) => _sendJson(
    () => _dio.post<Object?>(
      path,
      data: body,
      options: Options(extra: {IdempotencyKeyInterceptor.exemptKey: true}),
    ),
  );

  /// POST outside authentication (`POST /dev/token`): no Bearer token, no Idempotency-Key.
  Future<Map<String, Object?>> postWithoutSession(
    String path, {
    required Map<String, Object?> body,
  }) => _sendJson(
    () => _dio.post<Object?>(
      path,
      data: body,
      options: Options(extra: {AuthTokenInterceptor.skipAuthKey: true}),
    ),
  );

  Future<Map<String, Object?>> patchJson(String path, {required Map<String, Object?> body}) =>
      _sendJson(() => _dio.patch<Object?>(path, data: body));

  Future<Map<String, Object?>> putJson(String path, {required Map<String, Object?> body}) =>
      _sendJson(() => _dio.put<Object?>(path, data: body));

  /// DELETE [path]. The 204 body is ignored.
  Future<void> delete(String path) async {
    await _send(() => _dio.delete<Object?>(path));
  }

  Future<Map<String, Object?>> _sendJson(
    Future<Response<Object?>> Function() request, {
    bool isGet = false,
  }) async {
    final response = await _send(request, isGet: isGet);
    final body = response.data;
    if (body is Map<String, Object?>) {
      return body;
    }
    throw ApiException(code: ApiErrorCode.internalError, status: response.statusCode);
  }

  Future<Response<Object?>> _send(
    Future<Response<Object?>> Function() request, {
    bool isGet = false,
  }) async {
    var networkRetries = 0;
    var inProgressRetries = 0;
    while (true) {
      try {
        return await request();
      } on DioException catch (error, stackTrace) {
        final failure = ProblemDetailsInterceptor.toApiException(error);
        if (failure.code == ApiErrorCode.idempotencyInProgress &&
            inProgressRetries < maxInProgressRetries) {
          inProgressRetries++;
          await _retryDelay(inProgressRetryDelay);
          continue;
        }
        if (_isRetryable(failure, isGet: isGet) && networkRetries < networkRetryDelays.length) {
          await _retryDelay(networkRetryDelays[networkRetries]);
          networkRetries++;
          continue;
        }
        Error.throwWithStackTrace(failure, stackTrace);
      }
    }
  }

  static bool _isRetryable(ApiException failure, {required bool isGet}) =>
      failure.isNetworkFailure || (isGet && _retriedGetStatuses.contains(failure.status));

  static Future<void> _wait(Duration duration) => Future<void>.delayed(duration);
}

final apiClientProvider = Provider<ApiClient>((ref) => ApiClient(ref.watch(dioProvider)));
