import 'dart:convert';
import 'dart:typed_data';

import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/dio_provider.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/core/auth/profile_refresh_signal.dart';
import 'package:devpilot_app/core/auth/token_store.dart';
import 'package:devpilot_app/core/auth/token_store_provider.dart';
import 'package:devpilot_app/core/config/app_config.dart';
import 'package:devpilot_app/core/time/clock.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/test_app.dart';

typedef _Responder = Future<ResponseBody> Function(RequestOptions options);

/// Records requests and answers them without a network.
final class _FakeAdapter implements HttpClientAdapter {
  _FakeAdapter(this._respond);

  final _Responder _respond;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) {
    requests.add(options);
    return _respond(options);
  }

  @override
  void close({bool force = false}) {}
}

const _traceId = '4bf92f3577b34da6a3ce929d0e0e4736';

Future<ResponseBody> _json(int status, Map<String, Object?> body, {String? contentType}) async =>
    ResponseBody.fromString(
      jsonEncode(body),
      status,
      headers: {
        Headers.contentTypeHeader: [contentType ?? Headers.jsonContentType],
      },
    );

Future<ResponseBody> _problem(
  int status,
  String code, {
  String? detail,
  List<Map<String, Object?>> errors = const [],
}) => _json(status, {
  'type': 'urn:devpilot:problem:test',
  'title': 'Test',
  'status': status,
  'detail': detail,
  'instance': '/api/v1/me',
  'code': code,
  'traceId': _traceId,
  'errors': errors,
}, contentType: 'application/problem+json');

Future<ResponseBody> _connectionError(RequestOptions options) =>
    Future.error(DioException.connectionError(requestOptions: options, reason: 'offline'));

void main() {
  late MemoryTokenStore tokenStore;
  late _FakeAdapter adapter;
  late ProviderContainer container;
  late List<Duration> waits;
  late ApiClient apiClient;
  final fixedNow = DateTime.utc(2026, 9, 19, 12);

  void setUpClient({String? storedToken, required _Responder respond}) {
    tokenStore = MemoryTokenStore(storedToken);
    adapter = _FakeAdapter(respond);
    waits = [];
    container = ProviderContainer.test(
      overrides: [
        appConfigProvider.overrideWithValue(testAppConfig),
        tokenStoreProvider.overrideWithValue(tokenStore),
        clockProvider.overrideWithValue(() => fixedNow),
      ],
      retry: (retryCount, error) => null,
    );
    final dio = container.read(dioProvider)..httpClientAdapter = adapter;
    apiClient = ApiClient(dio, retryDelay: (duration) async => waits.add(duration));
  }

  Matcher throwsApiException(String code) =>
      throwsA(isA<ApiException>().having((error) => error.code, 'code', code));

  test('shouldSendBearerTokenAndTraceIdWhenSignedIn', () async {
    setUpClient(storedToken: 'access-token', respond: (_) => _json(200, {'sub': 'subject'}));

    final body = await apiClient.getJson('/me');

    expect(body, {'sub': 'subject'});
    final request = adapter.requests.single;
    expect(request.uri.toString(), 'http://localhost:8080/api/v1/me');
    expect(request.headers['Authorization'], 'Bearer access-token');
    expect(request.headers['X-Trace-Id'], matches(RegExp(r'^[0-9a-f]{32}$')));
    expect(request.headers.containsKey('Idempotency-Key'), isFalse);
  });

  test('shouldPostDevTokenWithoutAuthorizationOrIdempotencyKey', () async {
    setUpClient(respond: (_) => _json(200, {'accessToken': 'issued'}));

    await apiClient.postWithoutSession('/dev/token', body: {'email': 'learner@example.com'});

    final request = adapter.requests.single;
    expect(request.method, 'POST');
    expect(request.uri.path, '/api/v1/dev/token');
    expect(request.headers.containsKey('Authorization'), isFalse);
    expect(request.headers.containsKey('Idempotency-Key'), isFalse);
    expect(request.data, {'email': 'learner@example.com'});
  });

  test('shouldSendActionIdempotencyKeyOnAuthenticatedPost', () async {
    setUpClient(storedToken: 'access-token', respond: (_) => _json(201, {'id': 'created'}));
    const key = IdempotencyKey('0b6f8d0e-6c7a-4a57-9a3f-2d7f0f0d9c11');

    await apiClient.postJson('/side-projects', body: {'name': '주문 시스템'}, idempotencyKey: key);

    expect(adapter.requests.single.headers['Idempotency-Key'], key.value);
  });

  test('shouldNotSendIdempotencyKeyOnPatchPutOrDelete', () async {
    setUpClient(storedToken: 'access-token', respond: (_) => _json(200, {'version': 1}));

    await apiClient.patchJson('/me', body: {'version': 0});
    await apiClient.putJson('/learning-goal', body: {'version': 0});

    for (final request in adapter.requests) {
      expect(request.headers.containsKey('Idempotency-Key'), isFalse, reason: request.method);
    }
  });

  test('shouldThrowApiExceptionWithProblemDetailFieldsWhenServerRejects', () async {
    setUpClient(
      respond: (_) => _problem(
        400,
        'VALIDATION_FAILED',
        detail: '입력값을 확인해 주세요.',
        errors: [
          {'field': 'repoUrl', 'code': 'URL', 'message': 'URL 형식이 아닙니다.'},
        ],
      ),
    );

    final future = apiClient.postWithoutSession('/dev/token', body: {'email': 'x@example.com'});

    await expectLater(
      future,
      throwsA(
        isA<ApiException>()
            .having((error) => error.code, 'code', ApiErrorCode.validationFailed)
            .having((error) => error.status, 'status', 400)
            .having((error) => error.traceId, 'traceId', _traceId)
            .having((error) => error.occurredAt, 'occurredAt', fixedNow)
            .having((error) => error.fieldErrors.single.field, 'field', 'repoUrl')
            .having((error) => error.fieldErrors.single.code, 'field code', 'URL'),
      ),
    );
  });

  test('shouldExpireSessionWhenServerAnswersAuthenticationRequired', () async {
    setUpClient(
      storedToken: 'expired-token',
      respond: (_) => _problem(401, 'AUTHENTICATION_REQUIRED'),
    );

    await expectLater(
      apiClient.getJson('/me'),
      throwsApiException(ApiErrorCode.authenticationRequired),
    );

    final authState = container.read(authStateProvider);
    expect(authState, isA<SignedOut>());
    expect((authState as SignedOut).reason, SignOutReason.expired);
    expect(tokenStore.read(), isNull);
  });

  test('shouldAskForProfileRefreshWhenUserStateChangedOnServer', () async {
    setUpClient(storedToken: 'access-token', respond: (_) => _problem(403, 'USER_NOT_ALLOWED'));
    final before = container.read(profileRefreshSignalProvider);

    await expectLater(
      apiClient.getJson('/plans/active'),
      throwsApiException(ApiErrorCode.userNotAllowed),
    );

    expect(container.read(profileRefreshSignalProvider), before + 1);
    expect(container.read(authStateProvider), isA<SignedIn>());
  });

  test('shouldNotCallServerWhenAuthenticatedRequestHasNoSession', () async {
    setUpClient(respond: (_) => _json(200, {}));

    await expectLater(
      apiClient.getJson('/me'),
      throwsApiException(ApiErrorCode.authenticationRequired),
    );

    expect(adapter.requests, isEmpty);
    expect(waits, isEmpty);
    expect((container.read(authStateProvider) as SignedOut).reason, isNull);
  });

  test('shouldRetryConnectionFailureTwiceThenReportNetworkError', () async {
    setUpClient(storedToken: 'access-token', respond: _connectionError);

    await expectLater(apiClient.getJson('/me'), throwsApiException(ApiErrorCode.networkError));

    expect(adapter.requests, hasLength(3));
    expect(waits, [const Duration(seconds: 1), const Duration(seconds: 3)]);
  });

  test('shouldRetryPostWithSameIdempotencyKeyAfterLostResponse', () async {
    var calls = 0;
    setUpClient(
      storedToken: 'access-token',
      respond: (options) => ++calls == 1 ? _connectionError(options) : _json(201, {'id': 'p1'}),
    );
    const key = IdempotencyKey('1b6f8d0e-6c7a-4a57-9a3f-2d7f0f0d9c11');

    final body = await apiClient.postJson(
      '/side-projects',
      body: {'name': '주문 시스템'},
      idempotencyKey: key,
    );

    expect(body, {'id': 'p1'});
    expect(adapter.requests.map((request) => request.headers['Idempotency-Key']), [
      key.value,
      key.value,
    ]);
  });

  test('shouldRetryGetOnBadGatewayButNotPostServerError', () async {
    setUpClient(storedToken: 'access-token', respond: (_) => _problem(503, 'INTERNAL_ERROR'));

    await expectLater(apiClient.getJson('/me'), throwsApiException(ApiErrorCode.internalError));
    expect(adapter.requests, hasLength(3));

    adapter.requests.clear();
    await expectLater(
      apiClient.postJson(
        '/side-projects',
        body: const {},
        idempotencyKey: const IdempotencyKey('2b6f8d0e-6c7a-4a57-9a3f-2d7f0f0d9c11'),
      ),
      throwsApiException(ApiErrorCode.internalError),
    );
    expect(adapter.requests, hasLength(1));
  });

  test('shouldRetryIdempotencyInProgressEverySecondUpToFiveTimes', () async {
    setUpClient(
      storedToken: 'access-token',
      respond: (_) => _problem(409, 'IDEMPOTENCY_IN_PROGRESS'),
    );

    await expectLater(
      apiClient.postJson(
        '/onboarding',
        body: const {},
        idempotencyKey: const IdempotencyKey('3b6f8d0e-6c7a-4a57-9a3f-2d7f0f0d9c11'),
      ),
      throwsApiException(ApiErrorCode.idempotencyInProgress),
    );

    expect(adapter.requests, hasLength(6));
    expect(waits, List.filled(5, const Duration(seconds: 1)));
  });

  test('shouldNotRetryRateLimited', () async {
    setUpClient(storedToken: 'access-token', respond: (_) => _problem(429, 'RATE_LIMITED'));

    await expectLater(apiClient.getJson('/me'), throwsApiException(ApiErrorCode.rateLimited));

    expect(adapter.requests, hasLength(1));
  });

  test('shouldMapNonProblemErrorResponseToInternalError', () async {
    setUpClient(
      storedToken: 'access-token',
      respond: (_) async => ResponseBody.fromString(
        '<html>bad gateway</html>',
        500,
        headers: {
          Headers.contentTypeHeader: ['text/html'],
        },
      ),
    );

    await expectLater(
      apiClient.getJson('/me'),
      throwsA(
        isA<ApiException>()
            .having((error) => error.code, 'code', ApiErrorCode.internalError)
            .having((error) => error.status, 'status', 500),
      ),
    );
  });
}
