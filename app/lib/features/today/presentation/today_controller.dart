import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/plan/data/plan_repository.dart';
import 'package:devpilot_app/features/today/data/learning_session_models.dart';
import 'package:devpilot_app/features/today/data/learning_session_repository.dart';
import 'package:devpilot_app/features/today/data/task_status_updater.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';
import 'package:devpilot_app/features/today/data/today_repository.dart';
import 'package:devpilot_app/features/today/presentation/today_state.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// SCR-TODAY: `GET /today`, generation, and the main task's start → complete flow
/// (docs/02 SCR-TODAY, §4.2; docs/05 §8, §9.1~9.4). Every write re-reads the screen data.
final class TodayController extends AsyncNotifier<TodayScreenData> {
  final _generateKeys = IdempotencyKeyCache();
  final _startKeys = IdempotencyKeyCache();
  final _completeKeys = IdempotencyKeyCache();
  final _createPlanKeys = IdempotencyKeyCache();

  /// A status change still owed after its session was finished (docs/02 §4.2).
  ({String taskId, TaskStatus target})? _pendingStatus;

  TodayRepository get _today => ref.read(todayRepositoryProvider);

  LearningSessionRepository get _sessions => ref.read(learningSessionRepositoryProvider);

  TaskStatusUpdater get _statusUpdater => ref.read(taskStatusUpdaterProvider);

  @override
  Future<TodayScreenData> build() => _load();

  /// `GET /today` (404 `TODAY_NOT_GENERATED` → not generated) and, once generated, the sessions of
  /// the plan-day: the running one and the minutes already recorded.
  Future<TodayScreenData> _load() async {
    TodayView? today;
    try {
      today = await _today.fetchToday();
    } on ApiException catch (error) {
      if (error.code != ApiErrorCode.todayNotGenerated) {
        rethrow;
      }
    }
    if (today == null) {
      return const TodayScreenData(today: null);
    }
    final sessions = await _sessions.fetchSessions(from: today.planDate, to: today.planDate);
    return TodayScreenData(today: today, sessions: sessions.items);
  }

  /// Re-reads everything while the current data stays on screen.
  void reload() => ref.invalidateSelf();

  /// "오늘 계획 만들기", "변경", "다시 만들기", "하나 더 하기".
  Future<TodayOutcome> generate({
    required int availableMinutes,
    required EnergyLevel energyLevel,
    required bool force,
  }) async {
    final request = TodayGenerateRequest(
      availableMinutes: availableMinutes,
      energyLevel: energyLevel,
      force: force,
    );
    final idempotencyKey = _generateKeys.keyFor(request.toJson());
    _setBusy(true);
    try {
      final today = await _today.generate(request, idempotencyKey: idempotencyKey);
      _generateKeys.settle(null);
      _update((data) => data.copyWith(today: today, noPlan: false, busy: false));
      return const TodayActionDone();
    } on ApiException catch (error) {
      _generateKeys.settle(error);
      _setBusy(false);
      switch (error.code) {
        case ApiErrorCode.todayAlreadyStarted || ApiErrorCode.todayAlreadyCompleted:
          return TodayNeedsForce(error.code);
        case ApiErrorCode.planNotFound:
          _update((data) => data.copyWith(noPlan: true));
          return const TodayActionDone();
        case ApiErrorCode.concurrentModification:
          reload();
      }
      return TodayActionFailed(error);
    }
  }

  /// No-plan state "계획 만들기": `POST /plans`, then the same generation again.
  Future<TodayOutcome> createPlanAndGenerate({
    required int availableMinutes,
    required EnergyLevel energyLevel,
  }) async {
    _setBusy(true);
    try {
      await ref
          .read(planRepositoryProvider)
          .createPlan(idempotencyKey: _createPlanKeys.keyFor(const {}));
      _createPlanKeys.settle(null);
    } on ApiException catch (error) {
      _createPlanKeys.settle(error);
      if (error.code != ApiErrorCode.activePlanExists) {
        _setBusy(false);
        return TodayActionFailed(error);
      }
    }
    return generate(availableMinutes: availableMinutes, energyLevel: energyLevel, force: false);
  }

  /// "시작": PATCH to IN_PROGRESS, then a session for this task unless one already runs
  /// (docs/02 §4.2 steps 1~2).
  Future<TodayOutcome> start() => _run((data, main) async {
    if (main.status == TaskStatus.planned) {
      await _statusUpdater.update(main.id, TaskStatus.inProgress, knownVersion: main.version);
    }
    final planDate = data.today!.planDate;
    final sessions = await _sessions.fetchSessions(from: planDate, to: planDate);
    final running = sessions.items
        .where((session) => session.status == SessionStatus.inProgress)
        .firstOrNull;
    if (running == null || running.learningTaskId != main.id) {
      await _startSession(main.id);
    }
  });

  /// "오늘은 건너뛰기" (PLANNED → SKIPPED) and its "되돌리기" (SKIPPED → PLANNED).
  Future<TodayOutcome> changeMainStatus(TaskStatus target) => _run(
    (data, main) => _statusUpdater.update(main.id, target, knownVersion: main.version),
  );

  /// Completion sheet: finishes the session, then moves the main task to COMPLETED, or to
  /// DEFERRED for "여기까지 기록" (0 minutes abandons the session instead). A failed session call
  /// is returned as is so the sheet can show it; the status change follows only after it.
  Future<TodayOutcome> finish({
    required int actualMinutes,
    required String reflection,
    required bool partial,
  }) async {
    final data = state.value;
    final main = data?.mainTask;
    if (data == null || main == null || data.busy) {
      return const TodayActionDone();
    }
    _setBusy(true);
    final session = data.mainSession;
    try {
      if (session != null) {
        await _finishSession(
          session.id,
          actualMinutes,
          reflection,
          abandon: partial && actualMinutes == 0,
        );
      }
    } on ApiException catch (error) {
      _setBusy(false);
      return TodayActionFailed(error);
    }
    _pendingStatus = (
      taskId: main.id,
      target: partial ? TaskStatus.deferred : TaskStatus.completed,
    );
    return _sendPendingStatus(knownVersion: main.version);
  }

  /// Toast "다시 시도" after [TodayStatusRetryNeeded]: only the status change is sent again.
  Future<TodayOutcome> retryPendingStatus() {
    _setBusy(true);
    return _sendPendingStatus();
  }

  Future<TodayOutcome> _sendPendingStatus({int? knownVersion}) async {
    final pending = _pendingStatus;
    if (pending == null) {
      _setBusy(false);
      return const TodayActionDone();
    }
    try {
      try {
        await _statusUpdater.update(pending.taskId, pending.target, knownVersion: knownVersion);
      } on ApiException {
        if (knownVersion == null) {
          rethrow;
        }
        // One more try with the version read again (docs/02 §4.2).
        await _statusUpdater.update(pending.taskId, pending.target);
      }
      _pendingStatus = null;
      await _reloadData();
      return const TodayActionDone();
    } on ApiException catch (error) {
      _setBusy(false);
      return TodayStatusRetryNeeded(error);
    }
  }

  Future<void> _startSession(String taskId) async {
    final idempotencyKey = _startKeys.keyFor({'learningTaskId': taskId});
    try {
      await _sessions.startSession(learningTaskId: taskId, idempotencyKey: idempotencyKey);
      _startKeys.settle(null);
    } on ApiException catch (error) {
      _startKeys.settle(error);
      rethrow;
    }
  }

  Future<void> _finishSession(
    String sessionId,
    int actualMinutes,
    String reflection, {
    required bool abandon,
  }) async {
    if (abandon) {
      await _sessions.abandonSession(sessionId);
      return;
    }
    final request = SessionCompleteRequest(
      actualMinutes: actualMinutes,
      selfReflection: reflection.trim().isEmpty ? null : reflection,
    );
    final idempotencyKey = _completeKeys.keyFor(request.toJson());
    try {
      await _sessions.completeSession(sessionId, request, idempotencyKey: idempotencyKey);
      _completeKeys.settle(null);
    } on ApiException catch (error) {
      _completeKeys.settle(error);
      rethrow;
    }
  }

  /// Runs a main-task action while the buttons wait, then re-reads the screen. A failure
  /// re-reads too: the server state decides what the card shows next.
  Future<TodayOutcome> _run(
    Future<void> Function(TodayScreenData data, MainTaskView main) action,
  ) async {
    final data = state.value;
    final main = data?.mainTask;
    if (data == null || main == null || data.busy) {
      return const TodayActionDone();
    }
    _setBusy(true);
    try {
      await action(data, main);
      await _reloadData();
      return const TodayActionDone();
    } on ApiException catch (error) {
      _setBusy(false);
      reload();
      return TodayActionFailed(error);
    }
  }

  /// Replaces the data after a write without the loading state (docs/02 §4 "다시 읽는다").
  Future<void> _reloadData() async {
    final fresh = await _load();
    if (ref.mounted) {
      state = AsyncData(fresh);
    }
  }

  void _setBusy(bool busy) => _update((data) => data.copyWith(busy: busy));

  void _update(TodayScreenData Function(TodayScreenData data) change) {
    final data = state.value;
    if (data != null && ref.mounted) {
      state = AsyncData(change(data));
    }
  }
}

final todayControllerProvider = AsyncNotifierProvider.autoDispose<TodayController, TodayScreenData>(
  TodayController.new,
);
