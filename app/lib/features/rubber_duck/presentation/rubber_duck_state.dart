import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_models.dart';
import 'package:flutter/foundation.dart';

/// Which SCR-RUBBER-DUCK a route shows: before a session (`/rubber-duck/new?targetType=…`) or an
/// existing session (`/rubber-duck/:sessionId`). A record, so equal routes share one controller.
typedef RubberDuckRoute = ({
  String? sessionId,
  RubberDuckTargetType targetType,
  String? targetId,
  String? conceptKey,
  String? skillCode,
});

/// What SCR-RUBBER-DUCK shows (docs/02 §3.16 states ①~④).
@immutable
final class RubberDuckScreenData {
  const RubberDuckScreenData({
    required this.targetType,
    this.session,
    this.pendingText,
    this.sending = false,
    this.summarizing = false,
    this.abandoning = false,
    this.inputError,
    this.completion,
  });

  final RubberDuckTargetType targetType;

  /// Null before the first "설명 보내기" (state ①).
  final RubberDuckSessionView? session;

  /// "나" bubble of the explanation being sent.
  final String? pendingText;
  final bool sending;
  final bool summarizing;
  final bool abandoning;

  /// `413`, `422 SECRET_DETECTED_BLOCKED` or `502 AI_REFUSED` of the last send, shown under the
  /// input.
  final Object? inputError;

  /// The `complete` answer of this visit: it alone knows how many cards were created.
  final RubberDuckCompleteResponse? completion;

  bool get busy => sending || summarizing || abandoning;

  RubberDuckScreenData copyWith({
    RubberDuckSessionView? session,
    String? pendingText,
    bool clearPendingText = false,
    bool? sending,
    bool? summarizing,
    bool? abandoning,
    Object? inputError,
    bool clearInputError = false,
    RubberDuckCompleteResponse? completion,
  }) => RubberDuckScreenData(
    targetType: session?.targetType ?? targetType,
    session: session ?? this.session,
    pendingText: clearPendingText ? null : (pendingText ?? this.pendingText),
    sending: sending ?? this.sending,
    summarizing: summarizing ?? this.summarizing,
    abandoning: abandoning ?? this.abandoning,
    inputError: clearInputError ? null : (inputError ?? this.inputError),
    completion: completion ?? this.completion,
  );
}

/// How "설명 보내기" / "보내기" ended.
sealed class RubberDuckSendOutcome {
  const RubberDuckSendOutcome();
}

/// The turn was saved and the question is on screen.
final class RubberDuckTurnSent extends RubberDuckSendOutcome {
  const RubberDuckTurnSent();
}

/// The first send made a session: the route becomes `/rubber-duck/{sessionId}`. [turnError] is
/// set when the session exists but its first turn failed (the text stays as a draft).
final class RubberDuckSessionStarted extends RubberDuckSendOutcome {
  const RubberDuckSessionStarted(this.sessionId, {this.abandonedSessionId, this.turnError});

  final String sessionId;
  final String? abandonedSessionId;
  final Object? turnError;
}

/// `409 INVALID_STATE_TRANSITION`: the session ended elsewhere or hit the turn limit; it was read
/// again and the typed text stays for copying.
final class RubberDuckSessionChanged extends RubberDuckSendOutcome {
  const RubberDuckSessionChanged();
}

/// Start answered `400` with `REFERENCE_NOT_FOUND`: the target was deleted.
final class RubberDuckTargetGone extends RubberDuckSendOutcome {
  const RubberDuckTargetGone();
}

final class RubberDuckSendFailed extends RubberDuckSendOutcome {
  const RubberDuckSendFailed(this.error);

  final Object error;
}
