import 'package:devpilot_app/core/validation/goal_date_rules.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';

/// Validation copy of a [GoalDateViolation] (docs/02 §3.3 `validation.*`). `required` shows
/// nothing: the save button stays disabled instead.
String? goalDateMessage(GoalDateViolation? violation, AppLocalizations l10n) => switch (violation) {
  null || GoalDateViolation.required => null,
  GoalDateViolation.notFuture => l10n.validationDateFuture,
  GoalDateViolation.tooFar => l10n.validationDateTooFar,
  GoalDateViolation.tooEarly => l10n.validationDateTooEarly,
  GoalDateViolation.afterCompletion => l10n.validationCheckpointAfterCompletion,
  GoalDateViolation.notPast => l10n.validationDatePast,
};
