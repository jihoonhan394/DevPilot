import 'package:devpilot_app/core/time/local_date.dart';

/// Why a target date is rejected (docs/02 §3.2, docs/05 §4.1 domain checks).
enum GoalDateViolation {
  required,

  /// `validation.dateFuture`: the target date must be after today.
  notFuture,

  /// `validation.dateTooFar`: at most today + 3 years.
  tooFar,
}

/// The target date (목표일) check shared by SCR-ONBOARDING and SCR-LEARNING-GOAL. "Today" is the
/// server plan-day (`GET /me.today`); the server repeats the check.
abstract final class GoalDateRules {
  /// `targetCompletionDate`: required, from tomorrow up to today + 3 years.
  static GoalDateViolation? completionViolation(LocalDate? completion, LocalDate today) {
    if (completion == null) {
      return GoalDateViolation.required;
    }
    if (!completion.isAfter(today)) {
      return GoalDateViolation.notFuture;
    }
    if (completion.isAfter(today.addYears(3))) {
      return GoalDateViolation.tooFar;
    }
    return null;
  }
}
