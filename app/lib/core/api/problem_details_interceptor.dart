import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/trace_id_interceptor.dart';
import 'package:dio/dio.dart';

/// Converts every failed request into an [ApiException] stored in [DioException.error].
final class ProblemDetailsInterceptor extends Interceptor {
  ProblemDetailsInterceptor({required this._now});

  final DateTime Function() _now;

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    if (err.error is ApiException) {
      handler.next(err);
      return;
    }
    handler.next(err.copyWith(error: toApiException(err, occurredAt: _now().toUtc())));
  }

  /// Parses `application/problem+json` (docs/05 §1.2.1). Unparsable responses become
  /// `INTERNAL_ERROR`; failures without a response become `NETWORK_ERROR` or `CLIENT_TIMEOUT`.
  static ApiException toApiException(DioException error, {DateTime? occurredAt}) {
    final cause = error.error;
    if (cause is ApiException) {
      return cause;
    }
    return switch (error.type) {
      DioExceptionType.connectionTimeout ||
      DioExceptionType.sendTimeout ||
      DioExceptionType.receiveTimeout ||
      DioExceptionType.transformTimeout => ApiException(
        code: ApiErrorCode.clientTimeout,
        occurredAt: occurredAt,
      ),
      DioExceptionType.badResponse ||
      DioExceptionType.badCertificate ||
      DioExceptionType.connectionError ||
      DioExceptionType.cancel ||
      DioExceptionType.unknown => _fromResponse(error.response, occurredAt),
    };
  }

  static ApiException _fromResponse(Response<Object?>? response, DateTime? occurredAt) {
    if (response == null) {
      return ApiException(code: ApiErrorCode.networkError, occurredAt: occurredAt);
    }
    final headerTraceId = response.headers.value(TraceIdInterceptor.headerName);
    final retryAfter = parseRetryAfter(response.headers.value('retry-after'));
    final body = response.data;
    if (body is Map<String, Object?>) {
      final code = body['code'];
      if (code is String && code.isNotEmpty) {
        return ApiException(
          code: code,
          status: response.statusCode,
          detail: _nonEmptyString(body['detail']),
          traceId: _nonEmptyString(body['traceId']) ?? headerTraceId,
          fieldErrors: _fieldErrors(body['errors']),
          occurredAt: occurredAt,
          retryAfter: retryAfter,
        );
      }
    }
    return ApiException(
      code: ApiErrorCode.internalError,
      status: response.statusCode,
      traceId: headerTraceId,
      occurredAt: occurredAt,
      retryAfter: retryAfter,
    );
  }

  /// `Retry-After` in seconds (the only form the server sends, docs/05 §1.9.3). Anything else is
  /// ignored.
  static Duration? parseRetryAfter(String? value) {
    final seconds = value == null ? null : int.tryParse(value.trim());
    return seconds == null || seconds < 0 ? null : Duration(seconds: seconds);
  }

  static List<ApiFieldError> _fieldErrors(Object? errors) {
    if (errors is! List<Object?>) {
      return const [];
    }
    return [
      for (final entry in errors)
        if (entry is Map<String, Object?> && entry['field'] is String && entry['code'] is String)
          ApiFieldError(
            field: entry['field']! as String,
            code: entry['code']! as String,
            message: _nonEmptyString(entry['message']),
          ),
    ];
  }

  static String? _nonEmptyString(Object? value) =>
      value is String && value.isNotEmpty ? value : null;
}
