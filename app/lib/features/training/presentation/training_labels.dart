import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';

// Labels and tones of the training enums (docs/02 §3.1 "enum 라벨", §6.1 `OutcomeBadge`).

extension HintLevelLabel on HintLevel {
  String label(AppLocalizations l10n) => switch (this) {
    HintLevel.selfExplain => l10n.enumHintLevelSelfExplain,
    HintLevel.questionOnly => l10n.enumHintLevelQuestionOnly,
    HintLevel.conceptHint => l10n.enumHintLevelConceptHint,
    HintLevel.direction => l10n.enumHintLevelDirection,
    HintLevel.pseudocode => l10n.enumHintLevelPseudocode,
    HintLevel.partialCode => l10n.enumHintLevelPartialCode,
    HintLevel.fullExample => l10n.enumHintLevelFullExample,
    HintLevel.unknown => l10n.enumUnknown,
  };
}

extension EvaluatedOutcomeLabel on EvaluatedOutcome {
  String label(AppLocalizations l10n) => switch (this) {
    EvaluatedOutcome.correct => l10n.enumEvaluatedOutcomeCorrect,
    EvaluatedOutcome.partial => l10n.enumEvaluatedOutcomePartial,
    EvaluatedOutcome.incorrect => l10n.enumEvaluatedOutcomeIncorrect,
    EvaluatedOutcome.notEvaluated => l10n.enumEvaluatedOutcomeNotEvaluated,
    EvaluatedOutcome.unknown => l10n.enumUnknown,
  };

  /// `INCORRECT` never uses the danger tone (U-3).
  AppTone get tone => switch (this) {
    EvaluatedOutcome.correct => AppTone.success,
    EvaluatedOutcome.partial => AppTone.info,
    _ => AppTone.neutral,
  };
}

extension AttemptOutcomeLabel on AttemptOutcome {
  String label(AppLocalizations l10n) => switch (this) {
    AttemptOutcome.solvedIndependently => l10n.enumAttemptOutcomeSolvedIndependently,
    AttemptOutcome.solvedWithHints => l10n.enumAttemptOutcomeSolvedWithHints,
    AttemptOutcome.partial => l10n.enumAttemptOutcomePartial,
    AttemptOutcome.failed => l10n.enumAttemptOutcomeFailed,
    AttemptOutcome.abandoned => l10n.enumAttemptOutcomeAbandoned,
    AttemptOutcome.unknown => l10n.enumUnknown,
  };

  /// `FAILED` never uses the danger tone (U-3).
  AppTone get tone => switch (this) {
    AttemptOutcome.solvedIndependently => AppTone.success,
    AttemptOutcome.solvedWithHints || AttemptOutcome.partial => AppTone.info,
    _ => AppTone.neutral,
  };
}

/// `L{n} 라벨` (docs/02 §3.1 난이도).
String difficultyLabel(int difficulty, AppLocalizations l10n) => l10n.trainingDifficulty(
  difficulty,
  switch (difficulty) {
    1 => l10n.trainingDifficulty1,
    2 => l10n.trainingDifficulty2,
    3 => l10n.trainingDifficulty3,
    4 => l10n.trainingDifficulty4,
    5 => l10n.trainingDifficulty5,
    _ => l10n.enumUnknown,
  },
);
