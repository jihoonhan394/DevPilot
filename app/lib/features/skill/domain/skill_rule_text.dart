import 'package:devpilot_app/l10n/app_localizations.dart';

/// Sentence for a `rule_code` of the skill updater (docs/02 SCR-SKILL-DETAIL `skill.rule.*`,
/// docs/06 §7.2~§7.4). A code this build does not know shows `규칙 {code}` so a server-side rule
/// added later still reads as something.
String skillRuleText(String ruleCode, AppLocalizations l10n) => switch (ruleCode) {
  'K1_ANY_EVENT' => l10n.skillRuleK1AnyEvent,
  'K2_RECALL_GUIDED' => l10n.skillRuleK2RecallGuided,
  'K3_RECALL_INDEPENDENT' => l10n.skillRuleK3RecallIndependent,
  'K4_RECALL_LONG' => l10n.skillRuleK4RecallLong,
  'K5_TRANSFER' => l10n.skillRuleK5Transfer,
  'I1_ATTEMPTED' => l10n.skillRuleI1Attempted,
  'I2_SOLVED_GUIDED' => l10n.skillRuleI2SolvedGuided,
  'I3_SOLVED_INDEPENDENT' => l10n.skillRuleI3SolvedIndependent,
  'I4_PRODUCTION_LIKE' => l10n.skillRuleI4ProductionLike,
  'I5_TRANSFER' => l10n.skillRuleI5Transfer,
  'E1_ANY_EXPLANATION' => l10n.skillRuleE1AnyExplanation,
  'E2_PARTIAL' => l10n.skillRuleE2Partial,
  'E3_COVERAGE' => l10n.skillRuleE3Coverage,
  'E4_TRANSFER_QUESTION' => l10n.skillRuleE4TransferQuestion,
  'E5_TRADEOFF' => l10n.skillRuleE5Tradeoff,
  'D1_ANY_REVIEW' => l10n.skillRuleD1AnyReview,
  'D2_FOUND_WITH_HINT' => l10n.skillRuleD2FoundWithHint,
  'D3_FOUND_UNPROMPTED' => l10n.skillRuleD3FoundUnprompted,
  'D4_REPEATED_UNPROMPTED' => l10n.skillRuleD4RepeatedUnprompted,
  'D5_TRANSFER' => l10n.skillRuleD5Transfer,
  'K_DOWN_RECALL_FAIL' => l10n.skillRuleKDownRecallFail,
  'I_DOWN_TRANSFER_FAIL' => l10n.skillRuleIDownTransferFail,
  'D_DOWN_REPEATED_MISS' => l10n.skillRuleDDownRepeatedMiss,
  'DIAG_PASSED' => l10n.skillRuleDiagPassed,
  _ => l10n.skillRuleOther(ruleCode),
};
