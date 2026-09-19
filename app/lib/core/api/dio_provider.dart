import 'package:devpilot_app/core/api/auth_token_interceptor.dart';
import 'package:devpilot_app/core/api/idempotency_key_interceptor.dart';
import 'package:devpilot_app/core/api/problem_details_interceptor.dart';
import 'package:devpilot_app/core/api/trace_id_interceptor.dart';
import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/core/auth/profile_refresh_signal.dart';
import 'package:devpilot_app/core/config/app_config.dart';
import 'package:devpilot_app/core/time/clock.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// The single Dio instance. Interceptor order follows docs/08 §8.4.
final dioProvider = Provider<Dio>((ref) {
  final config = ref.watch(appConfigProvider);
  final dio = Dio(
    BaseOptions(
      baseUrl: config.apiRootUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 15),
    ),
  );
  dio.interceptors.addAll([
    TraceIdInterceptor(),
    AuthTokenInterceptor(
      readAccessToken: () => ref.read(authStateProvider).accessToken,
      onSessionExpired: () => ref.read(authStateProvider.notifier).expireSession(),
      onUserStateChanged: () => ref.read(profileRefreshSignalProvider.notifier).request(),
    ),
    IdempotencyKeyInterceptor(),
    ProblemDetailsInterceptor(now: ref.watch(clockProvider)),
  ]);
  ref.onDispose(dio.close);
  return dio;
});
