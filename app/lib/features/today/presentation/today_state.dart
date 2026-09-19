import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/today/data/learning_session_models.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';
import 'package:flutter/foundation.dart';

/// What SCR-TODAY shows (docs/02 SCR-TODAY).
@immutable
final class TodayScreenData {
  const TodayScreenData({
    required this.today,
    this.sessions = const [],
    this.noPlan = false,
    this.busy = false,
  });

  /// Null before the first generation of the plan-day (`404 TODAY_NOT_GENERATED`).
  final TodayView? today;

  /// Learning sessions of this plan-day (`GET /learning-sessions?from=&to=`).
  final List<SessionView> sessions;

  /// `POST /today/generate` answered `404 PLAN_NOT_FOUND`: the "계획 만들기" state.
  final bool noPlan;

  /// An action is in flight; every button waits for it.
  final bool busy;

  MainTaskView? get mainTask => today?.mainTask;

  /// The user's IN_PROGRESS session (at most one, I-05).
  SessionView? get activeSession =>
      sessions.where((session) => session.status == SessionStatus.inProgress).firstOrNull;

  /// The running session of the main task. Null when the task has none (not started yet, or the
  /// session was closed elsewhere).
  SessionView? get mainSession {
    final session = activeSession;
    final main = mainTask;
    return session != null && main != null && session.learningTaskId == main.id ? session : null;
  }

  /// "되돌리기" is possible only while the day has no PLANNED or IN_PROGRESS main (docs/04 §4.1);
  /// the shown main is SKIPPED only when no active main exists.
  bool get canUndoSkip => mainTask?.status == TaskStatus.skipped;

  /// Minutes recorded by the completed sessions of [taskId].
  int recordedMinutes(String taskId) => sessions
      .where(
        (session) => session.learningTaskId == taskId && session.status == SessionStatus.completed,
      )
      .fold(0, (sum, session) => sum + (session.actualMinutes ?? 0));

  TodayScreenData copyWith({TodayView? today, bool? noPlan, bool? busy}) => TodayScreenData(
    today: today ?? this.today,
    sessions: sessions,
    noPlan: noPlan ?? this.noPlan,
    busy: busy ?? this.busy,
  );
}

/// How a Today action ended; the screen turns it into a toast or a dialog.
sealed class TodayOutcome {
  const TodayOutcome();
}

final class TodayActionDone extends TodayOutcome {
  const TodayActionDone();
}

/// `409 TODAY_ALREADY_STARTED` / `TODAY_ALREADY_COMPLETED`: ask, then send again with
/// `force = true` (docs/02 SCR-TODAY "행동·검증").
final class TodayNeedsForce extends TodayOutcome {
  const TodayNeedsForce(this.code);

  final String code;
}

/// The session was saved but the task status change failed twice. Only the status change is
/// retried ("다시 시도"), never the session (docs/02 §4.2).
final class TodayStatusRetryNeeded extends TodayOutcome {
  const TodayStatusRetryNeeded(this.error);

  final Object error;
}

/// `409 INVALID_STATE_TRANSITION` on completing READ_CODE: the rubber duck explanation is the
/// completion condition (RC-1). Toast `today.readCode.needDuck`.
final class TodayReadingNeedsDuck extends TodayOutcome {
  const TodayReadingNeedsDuck();
}

final class TodayActionFailed extends TodayOutcome {
  const TodayActionFailed(this.error);

  final Object error;
}
