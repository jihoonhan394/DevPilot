import 'package:dio/dio.dart';
import 'package:uuid/uuid.dart';

/// Adds `X-Trace-Id` (32 lowercase hex) so client and server logs can be correlated (docs/08 §8.4).
final class TraceIdInterceptor extends Interceptor {
  TraceIdInterceptor({this._uuid = const Uuid()});

  static const headerName = 'X-Trace-Id';

  final Uuid _uuid;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    options.headers.putIfAbsent(headerName, () => _uuid.v4().replaceAll('-', ''));
    handler.next(options);
  }
}
