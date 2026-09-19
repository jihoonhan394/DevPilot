import 'dart:convert';
import 'dart:typed_data';

import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/dio_provider.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/auth/token_store.dart';
import 'package:devpilot_app/core/auth/token_store_provider.dart';
import 'package:devpilot_app/core/config/app_config.dart';
import 'package:devpilot_app/features/onboarding/data/diagnostic_repository.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_item_models.dart';
import 'package:devpilot_app/features/review/data/review_item_repository.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_models.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_repository.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:devpilot_app/features/today/data/reading_repository.dart';
import 'package:devpilot_app/features/training/data/attempt_models.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';
import 'package:devpilot_app/features/training/data/training_repository.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/test_app.dart';

/// Answers every request with [body] and keeps the requests.
final class _RecordingAdapter implements HttpClientAdapter {
  _RecordingAdapter(this.body);

  Map<String, Object?> body;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      jsonEncode(body),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

const _key = IdempotencyKey('0b6f8d0e-6c7a-4a57-9a3f-2d7f0f0d9c11');

/// Paths, methods, headers and AI receive timeouts of the S3 repositories (docs/05 §4.2, §6.3,
/// §9.6~§9.10, §10, §11.4~§11.6, §19.7).
void main() {
  late _RecordingAdapter adapter;
  late ApiClient apiClient;

  setUp(() {
    adapter = _RecordingAdapter({});
    final container = ProviderContainer.test(
      overrides: [
        appConfigProvider.overrideWithValue(testAppConfig),
        tokenStoreProvider.overrideWithValue(MemoryTokenStore('access-token')),
      ],
      retry: (retryCount, error) => null,
    );
    final dio = container.read(dioProvider)..httpClientAdapter = adapter;
    apiClient = ApiClient(dio, retryDelay: (_) async {});
  });

  RequestOptions last() => adapter.requests.last;

  /// Every repository parses the answer, so an empty body ends in a parse failure; the recorded
  /// request is what these tests check.
  Future<void> call(Future<Object?> Function() request) =>
      expectLater(request(), throwsA(anything));

  test('shouldCallChallengeEndpoints', () async {
    final repository = ApiTrainingRepository(apiClient);
    adapter.body = {'items': <Object?>[], 'nextCursor': null};

    await repository.fetchChallenges(skillId: 's1', purpose: ChallengePurpose.practice);
    expect(last().method, 'GET');
    expect(last().uri.path, '/api/v1/challenges');
    expect(last().uri.queryParameters, {'skillId': 's1', 'purpose': 'PRACTICE'});
    expect(last().headers['Authorization'], 'Bearer access-token');

    adapter.body = {};
    await call(() => repository.fetchChallenge('c1'));
    expect(last().uri.path, '/api/v1/challenges/c1');

    await call(() => repository.startAttempt('c1', idempotencyKey: _key));
    expect(last().method, 'POST');
    expect(last().uri.path, '/api/v1/challenges/c1/attempts');
    expect(last().headers['Idempotency-Key'], _key.value);
  });

  test('shouldCallAttemptEndpointsWithTheAiTimeoutOnHints', () async {
    final repository = ApiTrainingRepository(apiClient);
    adapter.body = {};

    await call(() => repository.fetchAttempt('a1'));
    expect(last().uri.path, '/api/v1/challenge-attempts/a1');

    await call(
      () => repository.recordSelfExplanation(
        'a1',
        const SelfExplanationRequest(text: '먼저 트랜잭션 경계를 본다', skipped: false),
        idempotencyKey: _key,
      ),
    );
    expect(last().uri.path, '/api/v1/challenge-attempts/a1/self-explanation');
    expect(last().data, {'text': '먼저 트랜잭션 경계를 본다', 'skipped': false});

    await call(
      () => repository.requestHint(
        'a1',
        const HintRequest(
          requestedLevel: HintLevel.direction,
          acknowledgeEvidenceImpact: true,
          giveUp: false,
        ),
        idempotencyKey: _key,
      ),
    );
    expect(last().uri.path, '/api/v1/challenge-attempts/a1/hints');
    expect(last().data, containsPair('requestedLevel', 'DIRECTION'));
    expect(last().receiveTimeout, ApiClient.syncAiReceiveTimeout);

    await call(
      () => repository.submitAnswer(
        'a1',
        const SubmissionRequest(answerText: '답', code: 'class A {}', language: CodeLanguage.java),
        idempotencyKey: _key,
      ),
    );
    expect(last().uri.path, '/api/v1/challenge-attempts/a1/submissions');
    expect(last().data, containsPair('language', 'JAVA'));
    expect(last().headers['Idempotency-Key'], _key.value);

    await call(() => repository.retryEvaluation('a1', 2, idempotencyKey: _key));
    expect(last().uri.path, '/api/v1/challenge-attempts/a1/submissions/2/retry');

    await call(() => repository.abandonAttempt('a1'));
    expect(last().uri.path, '/api/v1/challenge-attempts/a1/abandon');
    expect(last().headers.containsKey('Idempotency-Key'), isFalse);
  });

  test('shouldCallRubberDuckEndpointsWithTheirAiTimeouts', () async {
    final repository = ApiRubberDuckRepository(apiClient);
    adapter.body = {};

    await call(
      () => repository.start(
        const RubberDuckStartRequest(
          targetType: RubberDuckTargetType.concept,
          conceptKey: 'SPRING.TRANSACTION',
          skillCode: 'SPRING.TRANSACTION',
        ),
        idempotencyKey: _key,
      ),
    );
    expect(last().method, 'POST');
    expect(last().uri.path, '/api/v1/rubber-duck');
    expect(last().data, containsPair('targetType', 'CONCEPT'));
    expect(last().headers['Idempotency-Key'], _key.value);

    await call(
      () => repository.submitTurn(
        'd1',
        const RubberDuckTurnRequest(explanation: '프록시를 거치지 않기 때문이다'),
        idempotencyKey: _key,
      ),
    );
    expect(last().uri.path, '/api/v1/rubber-duck/d1/turns');
    expect(last().data, {'explanation': '프록시를 거치지 않기 때문이다'});
    expect(last().receiveTimeout, ApiClient.syncAiReceiveTimeout);

    await call(() => repository.complete('d1', idempotencyKey: _key));
    expect(last().uri.path, '/api/v1/rubber-duck/d1/complete');
    expect(last().receiveTimeout, ApiClient.summaryReceiveTimeout);

    await call(() => repository.abandon('d1'));
    expect(last().uri.path, '/api/v1/rubber-duck/d1/abandon');
    expect(last().headers.containsKey('Idempotency-Key'), isFalse);

    await call(() => repository.fetchSession('d1'));
    expect(last().method, 'GET');
    expect(last().uri.path, '/api/v1/rubber-duck/d1');
  });

  test('shouldCallReviewItemEndpoints', () async {
    final repository = ApiReviewItemRepository(apiClient);
    adapter.body = {'items': <Object?>[], 'nextCursor': null};

    await repository.fetchItems(skillId: 's1', status: ReviewItemStatus.suspended, cursor: 'c2');
    expect(last().uri.path, '/api/v1/review-items');
    expect(last().uri.queryParameters, {
      'skillId': 's1',
      'status': 'SUSPENDED',
      'cursor': 'c2',
    });

    adapter.body = {};
    await call(
      () => repository.createItem(
        const ReviewItemCreateRequest(
          skillCode: 'SPRING.TRANSACTION',
          conceptKey: 'MANUAL:5F0C2A1E-0000-4000-8000-000000000001',
          reviewType: ReviewType.explain,
          prompt: '질문',
          expectedAnswer: '정답',
          rubric: ['포인트'],
        ),
        idempotencyKey: _key,
      ),
    );
    expect(last().method, 'POST');
    expect(last().uri.path, '/api/v1/review-items');
    expect(last().headers['Idempotency-Key'], _key.value);

    await call(
      () => repository.updateItem(
        'r1',
        const ReviewItemPatchRequest(status: ReviewItemStatus.archived, version: 3),
      ),
    );
    expect(last().method, 'PATCH');
    expect(last().uri.path, '/api/v1/review-items/r1');
    expect(last().data, {'status': 'ARCHIVED', 'version': 3});
    expect(last().headers.containsKey('Idempotency-Key'), isFalse);
  });

  test('shouldCallReadingDiagnosticAndSkillHistoryEndpoints', () async {
    adapter.body = {};
    await call(() => ApiReadingRepository(apiClient).fetchReading('SPRING.TX.BOOT_SAMPLE'));
    expect(last().method, 'GET');
    expect(last().uri.path, '/api/v1/readings/SPRING.TX.BOOT_SAMPLE');

    adapter.body = {'items': <Object?>[]};
    await ApiDiagnosticRepository(apiClient).fetchSuggestions();
    expect(last().uri.path, '/api/v1/diagnostics/suggestions');

    adapter.body = {'items': <Object?>[], 'nextCursor': null};
    await ApiSkillRepository(apiClient).fetchHistory(skillId: 'b1', cursor: 'c3');
    expect(last().uri.path, '/api/v1/skills/b1/history');
    expect(last().uri.queryParameters, {'cursor': 'c3'});
    expect(last().headers.containsKey('Idempotency-Key'), isFalse);
  });
}
