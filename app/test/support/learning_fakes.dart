import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/dashboard/data/dashboard_models.dart';
import 'package:devpilot_app/features/dashboard/data/dashboard_repository.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_models.dart';
import 'package:devpilot_app/features/review/data/review_repository.dart';
import 'package:devpilot_app/features/today/data/learning_session_models.dart';
import 'package:devpilot_app/features/today/data/learning_session_repository.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';
import 'package:devpilot_app/features/today/data/today_repository.dart';

import 'fixtures.dart';
import 'learning_fixtures.dart';

// In-memory Today, learning session, review and dashboard endpoints that follow the docs/05 §8,
// §9, §11 and §13 rules the screens depend on.

ApiException? _next(List<ApiException> failures) => failures.isEmpty ? null : failures.removeAt(0);

ApiException _problem(String code, int status) => ApiException(code: code, status: status);

/// Test clock shared by the app (`clockProvider`) and the session fake.
final class TestClock {
  DateTime now = testNow;

  void advance(Duration duration) => now = now.add(duration);
}

class FakeTodayRepository implements TodayRepository {
  FakeTodayRepository({this.today});

  /// Null answers `404 TODAY_NOT_GENERATED`.
  TodayView? today;

  /// `POST /today/generate` answers `404 PLAN_NOT_FOUND`.
  var planMissing = false;
  final fetchFailures = <ApiException>[];
  final generateFailures = <ApiException>[];
  final patchFailures = <ApiException>[];
  final generates = <({TodayGenerateRequest request, IdempotencyKey key})>[];
  final patches = <({String taskId, TaskStatusPatchRequest request})>[];
  var fetchCount = 0;
  var _generated = 0;

  @override
  Future<TodayView> fetchToday() async {
    fetchCount++;
    final failure = _next(fetchFailures);
    if (failure != null) {
      throw failure;
    }
    return today ?? (throw _problem(ApiErrorCode.todayNotGenerated, 404));
  }

  /// docs/06 §5.9: 409 over a started or completed main unless forced.
  @override
  Future<TodayView> generate(
    TodayGenerateRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    generates.add((request: request, key: idempotencyKey));
    final failure = _next(generateFailures);
    if (failure != null) {
      throw failure;
    }
    if (planMissing) {
      throw _problem(ApiErrorCode.planNotFound, 404);
    }
    final main = today?.mainTask;
    if (main != null && !request.force) {
      if (main.status == TaskStatus.inProgress) {
        throw _problem(ApiErrorCode.todayAlreadyStarted, 409);
      }
      if (main.status == TaskStatus.completed) {
        throw _problem(ApiErrorCode.todayAlreadyCompleted, 409);
      }
    }
    final earlier = [
      ...?today?.earlierMainTasks,
      if (main != null && main.status != TaskStatus.planned)
        main.status == TaskStatus.inProgress ? main.copyWith(status: TaskStatus.deferred) : main,
    ];
    _generated++;
    return today = testTodayView(
      availableMinutes: request.availableMinutes,
      energyLevel: request.energyLevel,
      mainTask: testMainTask(
        id: 'e2000000-0000-4000-8000-00000000000$_generated',
        title: '새 과제 $_generated',
      ),
      earlierMainTasks: earlier,
    );
  }

  /// docs/04 §4.1 transitions with the optimistic `version` check.
  @override
  Future<TaskStatusView> updateTaskStatus(String taskId, TaskStatusPatchRequest request) async {
    patches.add((taskId: taskId, request: request));
    final failure = _next(patchFailures);
    if (failure != null) {
      throw failure;
    }
    final current = today!;
    final main = current.mainTask;
    final review = current.reviewTask;
    if (main != null && main.id == taskId) {
      if (main.version != request.version) {
        throw _problem(ApiErrorCode.concurrentModification, 409);
      }
      if (main.status == request.status) {
        throw _problem(ApiErrorCode.invalidStateTransition, 409);
      }
      setMainStatus(request.status);
    } else if (review != null && review.id == taskId) {
      if (review.status == request.status) {
        throw _problem(ApiErrorCode.invalidStateTransition, 409);
      }
      today = current.copyWith(
        reviewTask: review.copyWith(status: request.status, version: review.version + 1),
      );
    } else {
      throw _problem(ApiErrorCode.resourceNotFound, 404);
    }
    return TaskStatusView(
      id: taskId,
      dailyPlanId: current.dailyPlanId,
      planDate: current.planDate,
      main: main?.id == taskId,
      taskType: main?.id == taskId ? main!.taskType : TaskType.review,
      status: request.status,
      version: request.version + 1,
    );
  }

  void setMainStatus(TaskStatus status) {
    final current = today!;
    final main = current.mainTask!;
    today = current.copyWith(
      mainTask: main.copyWith(status: status, version: main.version + 1),
    );
  }
}

final class FakeLearningSessionRepository implements LearningSessionRepository {
  FakeLearningSessionRepository(this._today, this._clock);

  final FakeTodayRepository _today;
  final TestClock _clock;
  List<SessionView> sessions = [];
  final starts = <({String? taskId, IdempotencyKey key})>[];
  final completes = <({String id, SessionCompleteRequest request, IdempotencyKey key})>[];
  final abandons = <String>[];
  final startFailures = <ApiException>[];
  final completeFailures = <ApiException>[];
  var _started = 0;

  /// Closes a running session (I-05) and moves a PLANNED main task to IN_PROGRESS (docs/05 §9.1).
  @override
  Future<SessionStartResponse> startSession({
    String? learningTaskId,
    required IdempotencyKey idempotencyKey,
  }) async {
    starts.add((taskId: learningTaskId, key: idempotencyKey));
    final failure = _next(startFailures);
    if (failure != null) {
      throw failure;
    }
    final running = sessions.where((session) => session.status == SessionStatus.inProgress);
    final abandoned = running.firstOrNull?.id;
    _started++;
    final session = SessionView(
      id: 'a0000000-0000-4000-8000-00000000000$_started',
      learningTaskId: learningTaskId,
      planDate: testToday,
      startedAt: _clock.now,
      status: SessionStatus.inProgress,
      version: 0,
    );
    sessions = [
      session,
      for (final other in sessions)
        other.id == abandoned ? other.copyWith(status: SessionStatus.abandoned) : other,
    ];
    final main = _today.today?.mainTask;
    if (main != null && main.id == learningTaskId && main.status == TaskStatus.planned) {
      _today.setMainStatus(TaskStatus.inProgress);
    }
    return SessionStartResponse(session: session, abandonedSessionId: abandoned);
  }

  @override
  Future<SessionView> completeSession(
    String sessionId,
    SessionCompleteRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    completes.add((id: sessionId, request: request, key: idempotencyKey));
    final failure = _next(completeFailures);
    if (failure != null) {
      throw failure;
    }
    return _replace(
      sessionId,
      (session) => session.copyWith(
        status: SessionStatus.completed,
        actualMinutes: request.actualMinutes,
        completedAt: _clock.now,
      ),
    );
  }

  @override
  Future<SessionView> abandonSession(String sessionId) async {
    abandons.add(sessionId);
    return _replace(sessionId, (session) => session.copyWith(status: SessionStatus.abandoned));
  }

  @override
  Future<CursorPage<SessionView>> fetchSessions({
    required String from,
    required String to,
  }) async => CursorPage(items: sessions, nextCursor: null);

  SessionView _replace(String id, SessionView Function(SessionView session) change) {
    final current = sessions.firstWhere((session) => session.id == id);
    if (current.status != SessionStatus.inProgress) {
      throw _problem(ApiErrorCode.invalidStateTransition, 409);
    }
    final updated = change(current);
    sessions = [for (final session in sessions) session.id == id ? updated : session];
    return updated;
  }
}

/// `GET /reviews/due` and the answer with the hint caps of docs/06 §6.1.
final class FakeReviewRepository implements ReviewRepository {
  FakeReviewRepository({DueReviewsResponse? due}) : due = due ?? testDueReviews();

  DueReviewsResponse due;
  final fetchFailures = <ApiException>[];
  final answerFailures = <ApiException>[];
  final answers = <({String itemId, ReviewAnswerRequest request, IdempotencyKey key})>[];
  var fetchCount = 0;

  /// `evaluate: true`일 때 루브릭 항목마다 짚었다고 볼지. 길이가 모자라면 나머지는 빠진 것으로 본다.
  var evaluationMet = <bool>[true];

  /// `evaluate: true`일 때 돌려줄 총평. 비우면 응답에 담지 않는다.
  var evaluationFeedback = '두 번째 항목을 덧붙이면 설명이 닫힙니다.';

  @override
  Future<DueReviewsResponse> fetchDue() async {
    fetchCount++;
    final failure = _next(fetchFailures);
    if (failure != null) {
      throw failure;
    }
    return due;
  }

  @override
  Future<ReviewAnswerResponse> answer(
    String reviewItemId,
    ReviewAnswerRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    answers.add((itemId: reviewItemId, request: request, key: idempotencyKey));
    final failure = _next(answerFailures);
    if (failure != null) {
      throw failure;
    }
    final (rating, adjustment) = switch (request.hintLevel) {
      HintLevel.fullExample when request.selfRating != ReviewRating.again => (
        ReviewRating.again,
        RatingAdjustment.hintCapAgain,
      ),
      HintLevel.conceptHint
          when request.selfRating == ReviewRating.good || request.selfRating == ReviewRating.easy =>
        (ReviewRating.hard, RatingAdjustment.hintCapHard),
      _ => (request.selfRating, null),
    };
    final rubric = due.items
        .where((item) => item.reviewItemId == reviewItemId)
        .expand((item) => item.rubric)
        .toList();
    final results = <ReviewRubricResultView>[
      if (request.evaluate)
        for (var index = 0; index < rubric.length; index++)
          ReviewRubricResultView(
            id: rubric[index].id,
            criterion: rubric[index].criterion,
            met: index < evaluationMet.length && evaluationMet[index],
          ),
    ];
    return ReviewAnswerResponse(
      reviewAnswerId: 'ra-${answers.length}',
      finalRating: rating,
      adjustedBy: [?adjustment],
      evaluatedOutcome: request.evaluate ? EvaluatedOutcome.partial : EvaluatedOutcome.notEvaluated,
      rubricResults: results,
      evaluationFeedback: request.evaluate && evaluationFeedback.isNotEmpty
          ? evaluationFeedback
          : null,
      intervalBefore: 1,
      intervalAfter: rating == ReviewRating.again ? 1 : 2,
      nextDueDate: rating == ReviewRating.again ? '2026-09-20' : '2026-09-21',
      status: ReviewItemStatus.active,
      leechDetected: false,
    );
  }
}

final class FakeDashboardRepository implements DashboardRepository {
  DashboardView dashboard = testDashboard();
  final failures = <ApiException>[];

  @override
  Future<DashboardView> fetchDashboard() async {
    final failure = _next(failures);
    if (failure != null) {
      throw failure;
    }
    return dashboard;
  }
}
