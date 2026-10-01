import 'package:devpilot_app/core/time/local_date.dart';

/// Input rules of the SCR-TODAY generate form (docs/02 SCR-TODAY "행동·검증", §3.2).
abstract final class TodayRules {
  /// The minute chips; any other value shows on the "직접 입력" chip.
  static const minuteChips = [15, 30, 45, 60, 90, 120];

  /// `availableMinutes` limits of `POST /today/generate` (docs/05 §8.2).
  static const minMinutes = 5;
  static const maxMinutes = 720;

  /// Used when the user's study time for the day is 0.
  static const fallbackMinutes = 30;

  static bool isValidMinutes(int minutes) => minutes >= minMinutes && minutes <= maxMinutes;

  /// Weekend (Sat, Sun) uses the weekend study time, other days the weekday one; 0 becomes 30.
  /// Values below the server minimum are raised to 5 so the default can always be sent.
  static int defaultMinutes({
    required LocalDate today,
    required int weekdayMinutes,
    required int weekendMinutes,
  }) {
    final configured = today.weekday >= DateTime.saturday ? weekendMinutes : weekdayMinutes;
    if (configured == 0) {
      return fallbackMinutes;
    }
    return configured.clamp(minMinutes, maxMinutes);
  }

  static bool isChipValue(int minutes) => minuteChips.contains(minutes);
}
