import 'package:devpilot_app/core/time/local_date.dart';

/// Why a goal date is rejected (docs/02 §3.2 date rows, docs/05 §4.1 domain checks).
enum GoalDateViolation {
  required,

  /// `validation.dateFuture`: completion must be after today.
  notFuture,

  /// `validation.dateTooFar`: at most today + 3 years.
  tooFar,

  /// Earlier than the allowed range (checkpoint < today − 1 year, experience start < 1970).
  tooEarly,

  /// `validation.checkpointAfterCompletion`.
  afterCompletion,

  /// `validation.datePast`: experience start must not be after today.
  notPast,
}

/// Goal date checks shared by SCR-ONBOARDING and SCR-LEARNING-GOAL. "Today" is the server
/// plan-day (`GET /me.today`); the server repeats every check.
abstract final class GoalDateRules {
  static const earliestExperienceStart = LocalDate(1970, 1, 1);

  /// `targetCompletionDate`: required, today < value ≤ today + 3 years.
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

  /// `checkpointDate`: optional, today − 1 year ≤ value ≤ completion.
  static GoalDateViolation? checkpointViolation(
    LocalDate? checkpoint,
    LocalDate? completion,
    LocalDate today,
  ) {
    if (checkpoint == null) {
      return null;
    }
    if (checkpoint.isBefore(today.addYears(-1))) {
      return GoalDateViolation.tooEarly;
    }
    if (completion != null && checkpoint.isAfter(completion)) {
      return GoalDateViolation.afterCompletion;
    }
    return null;
  }

  /// `experienceStartDate`: optional, 1970-01-01 ≤ value ≤ today.
  static GoalDateViolation? experienceStartViolation(LocalDate? experienceStart, LocalDate today) {
    if (experienceStart == null) {
      return null;
    }
    if (experienceStart.isAfter(today)) {
      return GoalDateViolation.notPast;
    }
    if (experienceStart.isBefore(earliestExperienceStart)) {
      return GoalDateViolation.tooEarly;
    }
    return null;
  }
}
