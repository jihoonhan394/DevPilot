import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/time/clock.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_models.dart';
import 'package:devpilot_app/features/review/data/review_repository.dart';
import 'package:devpilot_app/features/review/domain/review_card_progress.dart';
import 'package:devpilot_app/features/review/presentation/due_reviews_provider.dart';
import 'package:devpilot_app/features/review/presentation/review_session_state.dart';
import 'package:devpilot_app/features/today/data/learning_session_models.dart';
import 'package:devpilot_app/features/today/data/learning_session_repository.dart';
import 'package:devpilot_app/features/today/data/task_status_updater.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// SCR-REVIEW-SESSION (docs/02 §3.6, §4.3). The argument is the Today REVIEW task id, if any.
final class ReviewSessionController extends AsyncNotifier<ReviewSessionState> {
  ReviewSessionController(this.taskId);

  final String? taskId;

  final _answerKeys = IdempotencyKeyCache();
  final _sessionKeys = IdempotencyKeyCache();
  final _completeKeys = IdempotencyKeyCache();

  /// The last answer that failed with a retryable error, sent again unchanged on "다시 시도".
  ({String itemId, ReviewRating rating, ReviewAnswerRequest request})? _failedAnswer;

  /// The REVIEW task change still owed after the session was recorded (docs/02 §4.2).
  TaskStatus? _pendingTaskStatus;

  static const _skipCodes = {
    ApiErrorCode.invalidStateTransition,
    ApiErrorCode.concurrentModification,
  };

  DateTime _now() => ref.read(clockProvider)();

  /// The due list is fixed for the whole session (docs/02 SCR-REVIEW-SESSION "데이터").
  @override
  Future<ReviewSessionState> build() async {
    final due = await ref.read(dueReviewsProvider.future);
    final first = due.items.firstOrNull;
    return ReviewSessionState(
      planDate: due.planDate,
      cards: due.items,
      current: first == null ? null : ReviewCardProgress(item: first, shownAt: _now()),
    );
  }

  void setAnswer(String text) => _updateCard((card) => card.withAnswer(text));

  void showHint() => _updateCard((card) => card.withHint());

  void showAnswerFirst() => _updateCard((card) => card.withAnswerShownFirst());

  void reveal() => _updateCard((card) => card.withRevealed());

  void _updateCard(ReviewCardProgress Function(ReviewCardProgress card) change) {
    final data = state.value;
    final card = data?.current;
    if (data != null && card != null && !data.submitting) {
      state = AsyncData(data.copyWith(current: () => change(card)));
    }
  }

  /// A rating button: starts the session on the first tap, saves the answer and moves on.
  Future<ReviewRateOutcome> rate(ReviewRating rating) async {
    final data = state.value;
    final card = data?.current;
    if (data == null || card == null || !card.revealed || data.submitting) {
      return const ReviewRateIgnored();
    }
    state = AsyncData(data.copyWith(submitting: true));
    if (data.answeredCount == 0 && data.sessionId == null) {
      await _startSessionQuietly(data.planDate);
    }
    final itemId = card.item.reviewItemId;
    // "다시 시도" sends the first attempt's body again so the Idempotency-Key stays the same.
    final retried = _failedAnswer;
    final request = retried != null && retried.itemId == itemId && retried.rating == rating
        ? retried.request
        : card.request(rating, _now());
    final idempotencyKey = _answerKeys.keyFor({'reviewItemId': itemId, ...request.toJson()});
    try {
      final response = await ref
          .read(reviewRepositoryProvider)
          .answer(itemId, request, idempotencyKey: idempotencyKey);
      _answerKeys.settle(null);
      _failedAnswer = null;
      _advance(finalRating: response.finalRating);
      return ReviewRated(selfRating: rating, response: response);
    } on ApiException catch (error) {
      _answerKeys.settle(error);
      if (_skipCodes.contains(error.code)) {
        _failedAnswer = null;
        _advance();
        return const ReviewCardSkipped();
      }
      _failedAnswer = (itemId: itemId, rating: rating, request: request);
      _update((current) => current.copyWith(submitting: false));
      return ReviewRateFailed(error);
    }
  }

  void _advance({ReviewRating? finalRating}) {
    _update((current) {
      final nextIndex = current.index + 1;
      final next = nextIndex < current.cards.length ? current.cards[nextIndex] : null;
      if (next == null) {
        // The next visit of Review reads the list again.
        ref.invalidate(dueReviewsProvider);
      }
      final struggled = finalRating == ReviewRating.again || finalRating == ReviewRating.hard;
      return current.copyWith(
        index: nextIndex,
        current: () => next == null ? null : ReviewCardProgress(item: next, shownAt: _now()),
        finalRatings: [...current.finalRatings, ?finalRating],
        struggled: struggled ? [...current.struggled, current.cards[current.index]] : null,
        submitting: false,
      );
    });
  }

  /// First rating without a running session: the Today REVIEW task goes IN_PROGRESS and a session
  /// starts. A running session (for example a started Today main task) is left alone (I-05). A
  /// failure here does not block the answer.
  Future<void> _startSessionQuietly(String planDate) async {
    try {
      final sessions = await ref
          .read(learningSessionRepositoryProvider)
          .fetchSessions(from: planDate, to: planDate);
      final running = sessions.items
          .where((session) => session.status == SessionStatus.inProgress)
          .firstOrNull;
      if (running != null) {
        if (taskId != null && running.learningTaskId == taskId) {
          _rememberSession(running);
        }
        return;
      }
      final task = taskId;
      if (task != null) {
        await ref.read(taskStatusUpdaterProvider).update(task, TaskStatus.inProgress);
      }
      final idempotencyKey = _sessionKeys.keyFor({'learningTaskId': task});
      final response = await ref
          .read(learningSessionRepositoryProvider)
          .startSession(learningTaskId: task, idempotencyKey: idempotencyKey);
      _sessionKeys.settle(null);
      _rememberSession(response.session);
    } on ApiException catch (error) {
      _sessionKeys.settle(error);
    }
  }

  void _rememberSession(SessionView session) => _update(
    (current) => current.copyWith(
      sessionId: () => session.id,
      sessionStartedAt: () => session.startedAt,
    ),
  );

  /// Summary "완료 기록" and the partial record on leaving: finishes the session (0 minutes on a
  /// partial record abandons it), then moves the REVIEW task to COMPLETED or DEFERRED.
  /// Returns the failure to show in the sheet, or null. Sent again after a failed task change,
  /// only the task change is repeated.
  Future<Object?> record({
    required int actualMinutes,
    required String reflection,
    required bool partial,
  }) async {
    final data = state.value;
    final sessionId = data?.sessionId;
    if (data == null || sessionId == null) {
      return null;
    }
    if (!data.recorded) {
      final failure = await _finishSession(sessionId, actualMinutes, reflection, partial: partial);
      if (failure != null) {
        return failure;
      }
      _update((current) => current.copyWith(recorded: true));
      _pendingTaskStatus = partial ? TaskStatus.deferred : TaskStatus.completed;
    }
    final task = taskId;
    final target = _pendingTaskStatus;
    if (task == null || target == null) {
      return null;
    }
    try {
      await ref.read(taskStatusUpdaterProvider).update(task, target);
      _pendingTaskStatus = null;
      return null;
    } on ApiException catch (error) {
      return error;
    }
  }

  Future<ApiException?> _finishSession(
    String sessionId,
    int actualMinutes,
    String reflection, {
    required bool partial,
  }) async {
    final sessions = ref.read(learningSessionRepositoryProvider);
    try {
      if (partial && actualMinutes == 0) {
        await sessions.abandonSession(sessionId);
        return null;
      }
      final request = SessionCompleteRequest(
        actualMinutes: actualMinutes,
        selfReflection: reflection.trim().isEmpty ? null : reflection,
      );
      await sessions.completeSession(
        sessionId,
        request,
        idempotencyKey: _completeKeys.keyFor(request.toJson()),
      );
      _completeKeys.settle(null);
      return null;
    } on ApiException catch (error) {
      _completeKeys.settle(error);
      return error;
    }
  }

  void _update(ReviewSessionState Function(ReviewSessionState current) change) {
    final data = state.value;
    if (data != null && ref.mounted) {
      state = AsyncData(change(data));
    }
  }
}

final reviewSessionControllerProvider = AsyncNotifierProvider.autoDispose
    .family<ReviewSessionController, ReviewSessionState, String?>(ReviewSessionController.new);
