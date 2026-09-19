import 'package:devpilot_app/core/api/auth_token_interceptor.dart';
import 'package:dio/dio.dart';
import 'package:uuid/uuid.dart';

/// Sends the `Idempotency-Key` header on authenticated POSTs (docs/08 §8.4 order 3).
///
/// The key comes from `RequestOptions.extra`, set by [ApiClient] from the action's key, so every
/// automatic retry of the same request carries the same key.
final class IdempotencyKeyInterceptor extends Interceptor {
  IdempotencyKeyInterceptor({this._uuid = const Uuid()});

  static const headerName = 'Idempotency-Key';

  /// `RequestOptions.extra` entry holding the key value.
  static const extraKey = 'devpilot.idempotencyKey';

  /// `RequestOptions.extra` flag for POSTs outside §1.7 (`/replan/preview`).
  static const exemptKey = 'devpilot.idempotencyExempt';

  final Uuid _uuid;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    if (options.method.toUpperCase() != 'POST' || _isExempt(options)) {
      handler.next(options);
      return;
    }
    final Object? key = options.extra[extraKey];
    if (key is String && key.isNotEmpty) {
      options.headers[headerName] = key;
    } else {
      assert(false, 'POST ${options.path} has no Idempotency-Key');
      // Release builds still send a key so the server does not reject the request (docs/08 §8.4).
      options.headers[headerName] = _uuid.v4();
    }
    handler.next(options);
  }

  static bool _isExempt(RequestOptions options) {
    final Object? exempt = options.extra[exemptKey];
    final Object? skipAuth = options.extra[AuthTokenInterceptor.skipAuthKey];
    return exempt == true || skipAuth == true;
  }
}
