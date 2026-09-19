import 'dart:convert';
import 'dart:typed_data';

import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/dio_provider.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/api/problem_details_interceptor.dart';
import 'package:devpilot_app/core/api/rate_limit_pause.dart';
import 'package:devpilot_app/core/auth/profile_refresh_signal.dart';
import 'package:devpilot_app/core/auth/token_store.dart';
import 'package:devpilot_app/core/auth/token_store_provider.dart';
import 'package:devpilot_app/core/config/app_config.dart';
import 'package:devpilot_app/core/time/clock.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/test_app.dart';

/// Answers every request with one Problem Details body.
final class _ProblemAdapter implements HttpClientAdapter {
  _ProblemAdapter(this.status, this.code, {this.retryAfter});

  final int status;
  final String code;
  final String? retryAfter;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async => ResponseBody.fromString(
    jsonEncode({'code': code, 'status': status, 'detail': 'detail', 'errors': <Object?>[]}),
    status,
    headers: {
      Headers.contentTypeHeader: ['application/problem+json'],
      if (retryAfter != null) 'retry-after': [retryAfter!],
    },
  );

  @override
  void close({bool force = false}) {}
}

/// AI status refresh after AI 429/503 and the global rate-limit pause (docs/02 §2.4, §6.5, §6.6).
void main() {
  final now = DateTime.utc(2026, 9, 19, 3);
  late ProviderContainer container;

  ApiClient clientAnswering(int status, String code, {String? retryAfter}) {
    container = ProviderContainer.test(
      overrides: [
        appConfigProvider.overrideWithValue(testAppConfig),
        tokenStoreProvider.overrideWithValue(MemoryTokenStore('access-token')),
        clockProvider.overrideWithValue(() => now),
      ],
      retry: (retryCount, error) => null,
    );
    final dio = container.read(dioProvider)
      ..httpClientAdapter = _ProblemAdapter(status, code, retryAfter: retryAfter);
    return ApiClient(dio, retryDelay: (_) async {});
  }

  Future<ApiException> send(ApiClient client) async {
    try {
      await client.postJson(
        '/rubber-duck/x/turns',
        body: const {'explanation': 'x'},
        idempotencyKey: const IdempotencyKey('key-00000001'),
      );
    } on ApiException catch (error) {
      return error;
    }
    fail('the call should fail');
  }

  test('shouldParseRetryAfterSeconds', () {
    expect(ProblemDetailsInterceptor.parseRetryAfter('5'), const Duration(seconds: 5));
    expect(ProblemDetailsInterceptor.parseRetryAfter('soon'), isNull);
    expect(ProblemDetailsInterceptor.parseRetryAfter(null), isNull);
  });

  for (final code in ApiErrorCode.aiStatusCodes) {
    test('shouldRereadTheProfileAfter $code', () async {
      final client = clientAnswering(code == ApiErrorCode.aiUnavailable ? 503 : 429, code);

      final failure = await send(client);

      expect(failure.code, code);
      expect(container.read(profileRefreshSignalProvider), 1);
      expect(container.read(rateLimitPauseProvider), isNull);
    });
  }

  test('shouldPausePollingUntilRetryAfterOnRateLimited', () async {
    final client = clientAnswering(429, ApiErrorCode.rateLimited, retryAfter: '7');

    final failure = await send(client);

    expect(failure.retryAfter, const Duration(seconds: 7));
    expect(container.read(rateLimitPauseProvider), now.add(const Duration(seconds: 7)));
    expect(container.read(profileRefreshSignalProvider), 0);
  });

  test('shouldIgnoreAiOutputFailuresForTheProfile', () async {
    final client = clientAnswering(502, ApiErrorCode.aiOutputInvalid);

    await send(client);

    expect(container.read(profileRefreshSignalProvider), 0);
    expect(container.read(rateLimitPauseProvider), isNull);
  });

  test('shouldKeepTheLaterEndOfTheRateLimitPause', () {
    clientAnswering(200, 'unused');
    final pause = container.read(rateLimitPauseProvider.notifier);

    pause.pauseFor(const Duration(seconds: 10));
    pause.pauseFor(const Duration(seconds: 3));

    expect(container.read(rateLimitPauseProvider), now.add(const Duration(seconds: 10)));
  });
}
