import 'dart:convert';
import 'dart:typed_data';

import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/dio_provider.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/auth/token_store.dart';
import 'package:devpilot_app/core/auth/token_store_provider.dart';
import 'package:devpilot_app/core/config/app_config.dart';
import 'package:devpilot_app/features/dashboard/data/dashboard_repository.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/features/plan/data/plan_repository.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_models.dart';
import 'package:devpilot_app/features/review/data/review_repository.dart';
import 'package:devpilot_app/features/today/data/learning_session_models.dart';
import 'package:devpilot_app/features/today/data/learning_session_repository.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';
import 'package:devpilot_app/features/today/data/today_repository.dart';
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

const _sessionJson = <String, Object?>{
  'id': 'a1',
  'learningTaskId': 't1',
  'planDate': '2026-09-19',
  'startedAt': '2026-09-19T03:00:00Z',
  'completedAt': null,
  'actualMinutes': null,
  'selfReflection': null,
  'status': 'IN_PROGRESS',
  'version': 0,
};

/// Paths, methods, headers and bodies of the S2 repositories (docs/05 §7.7, §7.9, §8, §9, §11, §13).
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

  test('shouldCallTodayEndpoints', () async {
    final repository = ApiTodayRepository(apiClient);
    adapter.body = {
      'id': 't1',
      'dailyPlanId': 'd1',
      'planDate': '2026-09-19',
      'main': true,
      'taskType': 'EXPLAIN',
      'status': 'IN_PROGRESS',
      'completedAt': null,
      'version': 1,
    };

    await repository.updateTaskStatus(
      't1',
      const TaskStatusPatchRequest(status: TaskStatus.inProgress, version: 0),
    );
    expect(last().method, 'PATCH');
    expect(last().uri.path, '/api/v1/today/tasks/t1');
    expect(last().data, {'status': 'IN_PROGRESS', 'version': 0});
    expect(last().headers.containsKey('Idempotency-Key'), isFalse);

    await expectLater(
      repository.generate(
        const TodayGenerateRequest(availableMinutes: 30, energyLevel: EnergyLevel.low, force: true),
        idempotencyKey: _key,
      ),
      throwsA(anything),
    );
    expect(last().method, 'POST');
    expect(last().uri.path, '/api/v1/today/generate');
    expect(last().headers['Idempotency-Key'], _key.value);
    expect(last().data, {'availableMinutes': 30, 'energyLevel': 'LOW', 'force': true});
  });

  test('shouldCallLearningSessionEndpoints', () async {
    final repository = ApiLearningSessionRepository(apiClient);
    adapter.body = {'session': _sessionJson, 'abandonedSessionId': null};

    await repository.startSession(learningTaskId: 't1', idempotencyKey: _key);
    expect(last().uri.path, '/api/v1/learning-sessions');
    expect(last().data, {'learningTaskId': 't1'});
    expect(last().headers['Idempotency-Key'], _key.value);

    adapter.body = _sessionJson;
    await repository.completeSession(
      'a1',
      const SessionCompleteRequest(actualMinutes: 25, selfReflection: '정리'),
      idempotencyKey: _key,
    );
    expect(last().uri.path, '/api/v1/learning-sessions/a1/complete');
    expect(last().data, {'actualMinutes': 25, 'selfReflection': '정리'});

    await repository.abandonSession('a1');
    expect(last().method, 'POST');
    expect(last().uri.path, '/api/v1/learning-sessions/a1/abandon');
    expect(last().headers.containsKey('Idempotency-Key'), isFalse);
    expect(last().headers['Authorization'], 'Bearer access-token');

    adapter.body = {
      'items': [_sessionJson],
      'nextCursor': null,
    };
    final page = await repository.fetchSessions(from: '2026-09-19', to: '2026-09-19');
    expect(last().uri.path, '/api/v1/learning-sessions');
    expect(last().uri.queryParameters, {'from': '2026-09-19', 'to': '2026-09-19'});
    expect(page.items.single.status, SessionStatus.inProgress);
  });

  test('shouldCallReviewEndpoints', () async {
    final repository = ApiReviewRepository(apiClient);
    adapter.body = {
      'planDate': '2026-09-19',
      'cap': 20,
      'comebackMode': false,
      'totalDueCount': 0,
      'items': <Object?>[],
    };

    await repository.fetchDue();
    expect(last().method, 'GET');
    expect(last().uri.path, '/api/v1/reviews/due');
    expect(last().uri.queryParameters, isEmpty);

    adapter.body = {
      'reviewAnswerId': 'ra1',
      'finalRating': 'GOOD',
      'adjustedBy': <Object?>[],
      'evaluatedOutcome': 'NOT_EVALUATED',
      'intervalBefore': 2,
      'intervalAfter': 4,
      'nextDueDate': '2026-09-23',
      'evaluationSkippedReason': null,
      'status': 'ACTIVE',
      'leechDetected': false,
    };
    await repository.answer(
      'r1',
      const ReviewAnswerRequest(
        answerText: null,
        selfRating: ReviewRating.good,
        hintLevel: HintLevel.selfExplain,
        responseSeconds: 40,
        wasVariant: false,
        evaluate: false,
      ),
      idempotencyKey: _key,
    );
    expect(last().uri.path, '/api/v1/reviews/r1/answer');
    expect(last().headers['Idempotency-Key'], _key.value);
    expect(last().data, containsPair('hintLevel', 'SELF_EXPLAIN'));
  });

  test('shouldCallBudgetPreviewWithoutKeyAndDashboard', () async {
    final plans = ApiPlanRepository(apiClient);
    adapter.body = {};

    await expectLater(
      plans.previewReplan('p1', const ReplanRequest(reason: '', version: 0, milestones: [])),
      throwsA(anything),
    );
    expect(last().method, 'POST');
    expect(last().uri.path, '/api/v1/plans/p1/replan/preview');
    expect(last().headers.containsKey('Idempotency-Key'), isFalse);
    expect((last().data as Map<String, Object?>)['restoredDeferrals'], isEmpty);

    await expectLater(plans.fetchActiveBudget(), throwsA(anything));
    expect(last().uri.path, '/api/v1/plans/active/budget');

    await expectLater(ApiDashboardRepository(apiClient).fetchDashboard(), throwsA(anything));
    expect(last().uri.path, '/api/v1/dashboard');
  });
}
