import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/training/data/attempt_models.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';

/// How one rung of the Hint Ladder looks (docs/02 §6.2).
enum HintRungState {
  /// Content was disclosed; it can be expanded.
  disclosed,

  /// Below the current maximum without a disclosure (skipped on another device): "건너뜀".
  skipped,

  /// `maxHintLevel + 1`: the only rung with a button.
  next,

  /// Above the next rung: text with a lock.
  locked,
}

/// What the current maximum level means for the result (docs/02 SCR-TRAINING-ATTEMPT).
enum HintImpact { independent, withHints, review }

/// Hint Ladder rules the screen needs (docs/02 §6.2, docs/06 §9.1 HL-3~HL-5).
abstract final class HintLadderRules {
  /// The six requestable rungs, 1 (`QUESTION_ONLY`) to 6 (`FULL_EXAMPLE`).
  static const rungs = [
    HintLevel.questionOnly,
    HintLevel.conceptHint,
    HintLevel.direction,
    HintLevel.pseudocode,
    HintLevel.partialCode,
    HintLevel.fullExample,
  ];

  /// Rung number shown before the label ("2 개념 힌트").
  static int number(HintLevel level) => rungs.indexOf(level) + 1;

  /// The one level that may be requested now; null after `FULL_EXAMPLE` (HL-3 skips are not
  /// offered).
  static HintLevel? next(HintLevel max) {
    if (max == HintLevel.selfExplain) {
      return rungs.first;
    }
    final index = rungs.indexOf(max);
    // An unknown level or FULL_EXAMPLE has nothing above it.
    return index < 0 || index + 1 >= rungs.length ? null : rungs[index + 1];
  }

  /// `PSEUDOCODE` and above go through the confirmation dialog (HL-4).
  static bool needsConfirmation(HintLevel level) => _rank(level) >= _rank(HintLevel.pseudocode);

  /// Levels 4~6 are always written by the AI; 1~3 come from the challenge content.
  static bool isAiLevel(HintLevel level) => needsConfirmation(level);

  /// `FULL_EXAMPLE` before any submission is giving up (HL-5): the request sends `giveUp`.
  static bool isGiveUp(HintLevel level, int submissionCount) =>
      level == HintLevel.fullExample && submissionCount == 0;

  static HintRungState stateOf(HintLevel level, AttemptView attempt) {
    if (attempt.hints.any((hint) => hint.level == level)) {
      return HintRungState.disclosed;
    }
    if (_rank(level) <= _rank(attempt.maxHintLevel)) {
      return HintRungState.skipped;
    }
    return level == next(attempt.maxHintLevel) ? HintRungState.next : HintRungState.locked;
  }

  static HintImpact impactOf(HintLevel max) {
    if (_rank(max) <= _rank(HintLevel.questionOnly)) {
      return HintImpact.independent;
    }
    return _rank(max) <= _rank(HintLevel.direction) ? HintImpact.withHints : HintImpact.review;
  }

  /// `DIAGNOSTIC_PASSED` condition shown on the result (docs/06 §7.4): CORRECT with at most the
  /// question hint.
  static bool diagnosticPassed(AttemptView attempt) =>
      attempt.purpose == ChallengePurpose.diagnostic &&
      attempt.evaluatedOutcome == EvaluatedOutcome.correct &&
      _rank(attempt.maxHintLevel) <= _rank(HintLevel.questionOnly);

  static int _rank(HintLevel level) =>
      level == HintLevel.unknown ? -1 : HintLevel.values.indexOf(level);
}
