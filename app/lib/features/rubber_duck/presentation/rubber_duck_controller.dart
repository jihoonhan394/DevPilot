import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_local_store.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_models.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_repository.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_state.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// SCR-RUBBER-DUCK: the session is made by the first send, then turns, the summary and abandon
/// (docs/02 §3.16, docs/05 §9.6~§9.10). The explanation stays in the input on every failure.
final class RubberDuckController extends AsyncNotifier<RubberDuckScreenData> {
  RubberDuckController(this.route);

  final RubberDuckRoute route;
  final _startKeys = IdempotencyKeyCache();
  final _turnKeys = IdempotencyKeyCache();
  final _completeKeys = IdempotencyKeyCache();

  static const _inputCodes = {
    ApiErrorCode.contentTooLarge,
    ApiErrorCode.secretDetectedBlocked,
    ApiErrorCode.aiRefused,
  };
  static const _newKeyCodes = {ApiErrorCode.aiOutputInvalid, ApiErrorCode.aiTimeout};

  RubberDuckRepository get _repository => ref.read(rubberDuckRepositoryProvider);

  RubberDuckLocalStore get _local => ref.read(rubberDuckLocalStoreProvider);

  @override
  Future<RubberDuckScreenData> build() async {
    final sessionId = route.sessionId;
    if (sessionId == null) {
      return RubberDuckScreenData(targetType: route.targetType);
    }
    final session = await _repository.fetchSession(sessionId);
    return RubberDuckScreenData(targetType: session.targetType, session: session);
  }

  /// Screen shown again (browser tab visible): the session may have changed elsewhere.
  void reload() {
    if (route.sessionId != null) {
      ref.invalidateSelf();
    }
  }

  /// "설명 보내기" (first send: `POST /rubber-duck`, then the turn) or "보내기".
  Future<RubberDuckSendOutcome> send(String explanation, {String? taskId}) async {
    final data = state.value;
    if (data == null || data.busy) {
      return const RubberDuckTurnSent();
    }
    _change(
      (data) => data.copyWith(sending: true, pendingText: explanation, clearInputError: true),
    );
    try {
      final session = data.session;
      if (session == null) {
        return await _startAndSend(explanation, taskId);
      }
      await _sendTurn(session.id, explanation);
      return const RubberDuckTurnSent();
    } on ApiException catch (error) {
      return _sendFailed(error);
    } finally {
      _change((data) => data.copyWith(sending: false, clearPendingText: true));
    }
  }

  Future<RubberDuckSendOutcome> _startAndSend(String explanation, String? taskId) async {
    final request = RubberDuckStartRequest(
      targetType: route.targetType,
      targetId: route.targetId,
      conceptKey: route.conceptKey,
      skillCode: route.skillCode,
    );
    final RubberDuckStartResponse started;
    try {
      started = await _repository.start(
        request,
        idempotencyKey: _startKeys.keyFor(request.toJson()),
      );
      _startKeys.settle(null);
    } on ApiException catch (error) {
      _startKeys.settle(error);
      final gone = error.fieldErrors.any(
        (field) => field.code == ApiFieldErrorCode.referenceNotFound,
      );
      return gone ? const RubberDuckTargetGone() : RubberDuckSendFailed(error);
    }
    final sessionId = started.session.id;
    _local.writeActive(sessionId, taskId);
    try {
      await _sendTurn(sessionId, explanation);
      return RubberDuckSessionStarted(sessionId, abandonedSessionId: started.abandonedSessionId);
    } on ApiException catch (error) {
      // The session exists without a turn: the new route opens it with the text as a draft.
      _local.writeDraft(sessionId, explanation);
      return RubberDuckSessionStarted(
        sessionId,
        abandonedSessionId: started.abandonedSessionId,
        turnError: error,
      );
    }
  }

  /// One turn, then the session again for the masked text of the new bubble (RD-6).
  Future<void> _sendTurn(String sessionId, String explanation) async {
    final request = RubberDuckTurnRequest(explanation: explanation);
    try {
      await _repository.submitTurn(
        sessionId,
        request,
        idempotencyKey: _turnKeys.keyFor(request.toJson()),
      );
      _turnKeys.settle(null);
    } on ApiException catch (error) {
      _newKeyCodes.contains(error.code) ? _turnKeys.discard() : _turnKeys.settle(error);
      rethrow;
    }
    _local.writeDraft(sessionId, '');
    if (route.sessionId != null) {
      final session = await _repository.fetchSession(sessionId);
      _change((data) => data.copyWith(session: session));
    }
  }

  Future<RubberDuckSendOutcome> _sendFailed(ApiException error) async {
    if (_inputCodes.contains(error.code)) {
      _change((data) => data.copyWith(inputError: error));
      return RubberDuckSendFailed(error);
    }
    if (error.code == ApiErrorCode.invalidStateTransition ||
        error.code == ApiErrorCode.concurrentModification) {
      await _refresh();
      return error.code == ApiErrorCode.invalidStateTransition
          ? const RubberDuckSessionChanged()
          : RubberDuckSendFailed(error);
    }
    return RubberDuckSendFailed(error);
  }

  /// "정리하고 끝내기" / "정리하기": one summary per session (RD-4). With no turn the server ends
  /// the session as ABANDONED without the AI. `409` means it already ended: it is read again.
  Future<Object?> complete({String? taskId}) async {
    final session = state.value?.session;
    if (session == null || state.value!.busy) {
      return null;
    }
    _change((data) => data.copyWith(summarizing: true));
    try {
      final result = await _repository.complete(
        session.id,
        idempotencyKey: _completeKeys.keyFor({'sessionId': session.id}),
      );
      _completeKeys.settle(null);
      _afterEnd(session, taskId, completed: result.status == RubberDuckStatus.completed);
      _change(
        (data) => data.copyWith(
          completion: result,
          session: session.copyWith(
            status: result.status,
            summary: RubberDuckSummaryView(
              gaps: result.gaps,
              confirmed: result.confirmed,
              overallNote: result.overallNote,
            ),
            summarySkippedReason: result.summarySkippedReason,
            version: result.version,
          ),
        ),
      );
      return null;
    } on ApiException catch (error) {
      _completeKeys.settle(error);
      if (error.code == ApiErrorCode.invalidStateTransition) {
        await _refresh();
        return null;
      }
      return error;
    } finally {
      _change((data) => data.copyWith(summarizing: false));
    }
  }

  /// Menu "그만두기": no summary, no cards; the turns stay.
  Future<Object?> abandon() async {
    final session = state.value?.session;
    if (session == null || state.value!.busy) {
      return null;
    }
    _change((data) => data.copyWith(abandoning: true));
    try {
      final ended = await _repository.abandon(session.id);
      _afterEnd(session, null, completed: false);
      _change((data) => data.copyWith(session: ended));
      return null;
    } on ApiException catch (error) {
      if (error.code == ApiErrorCode.invalidStateTransition) {
        await _refresh();
        return null;
      }
      return error;
    } finally {
      _change((data) => data.copyWith(abandoning: false));
    }
  }

  /// The running record goes; a finished code reading remembers its session for Today (RC-1).
  void _afterEnd(RubberDuckSessionView session, String? taskId, {required bool completed}) {
    final active = _local.readActive();
    if (active?.sessionId == session.id) {
      _local.clearActive();
    }
    final readingTask = session.targetType == RubberDuckTargetType.codeReading
        ? (session.targetId ?? taskId)
        : null;
    if (completed && readingTask != null) {
      _local.writeTaskSession(readingTask, session.id);
    }
  }

  Future<void> _refresh() async {
    final sessionId = route.sessionId ?? state.value?.session?.id;
    if (sessionId == null) {
      return;
    }
    try {
      final session = await _repository.fetchSession(sessionId);
      _change((data) => data.copyWith(session: session));
    } on ApiException {
      // The failure that led here is already shown; the next visit reads the session again.
    }
  }

  void _change(RubberDuckScreenData Function(RubberDuckScreenData data) change) {
    final data = state.value;
    if (data != null && ref.mounted) {
      state = AsyncData(change(data));
    }
  }
}

final rubberDuckControllerProvider = AsyncNotifierProvider.autoDispose
    .family<RubberDuckController, RubberDuckScreenData, RubberDuckRoute>(RubberDuckController.new);
