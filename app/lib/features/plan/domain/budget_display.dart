/// Number formats of the budget card and the replan preview (docs/02 §3.1, SCR-PLAN).
abstract final class BudgetDisplay {
  /// Minutes rounded half up to whole hours: "약 {h}시간".
  static int hours(int minutes) => (minutes + 30) ~/ 60;

  /// Basis points as a whole percent: `floorDiv(bp, 100)`.
  static int percent(int basisPoints) => basisPoints ~/ 100;
}
