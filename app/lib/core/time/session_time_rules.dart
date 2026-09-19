/// Learning session time rules shared by SCR-TODAY and SCR-REVIEW-SESSION
/// (docs/02 SCR-TODAY 완료 시트, docs/05 §9.2).
abstract final class SessionTimeRules {
  static const maxMinutes = 720;

  static int elapsedSeconds(DateTime startedAt, DateTime now) {
    final seconds = now.difference(startedAt).inSeconds;
    return seconds < 0 ? 0 : seconds;
  }

  /// Whole minutes since the start, for "진행 중 · {n}분째".
  static int elapsedMinutes(DateTime startedAt, DateTime now) =>
      elapsedSeconds(startedAt, now) ~/ 60;

  /// Upper limit the server accepts: `min(720, ceilDiv(elapsedSeconds × 3, 120))` (= ⌈min × 1.5⌉).
  static int maxActualMinutes(DateTime startedAt, DateTime now) {
    final limit = _ceilDiv(elapsedSeconds(startedAt, now) * 3, 120);
    return limit > maxMinutes ? maxMinutes : limit;
  }

  /// Sheet default: elapsed minutes rounded half up, at least 1, never above the limit.
  static int defaultActualMinutes(DateTime startedAt, DateTime now) {
    final rounded = (elapsedSeconds(startedAt, now) + 30) ~/ 60;
    final atLeastOne = rounded < 1 ? 1 : rounded;
    final limit = maxActualMinutes(startedAt, now);
    return atLeastOne > limit ? limit : atLeastOne;
  }

  static int _ceilDiv(int value, int divisor) => (value + divisor - 1) ~/ divisor;
}
