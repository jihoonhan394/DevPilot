import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/time/time_zone_support.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';

// Display formats of docs/02 §3.1 "표시 형식". Patterns live in the ARB file.

/// Plan date: `M월 d일 (E)`.
String formatPlanDate(LocalDate date, AppLocalizations l10n) =>
    l10n.commonPlanDate(date.toDateTime());

/// Goal dates with the year: `2027년 4월 1일`.
String formatLongDate(LocalDate date, AppLocalizations l10n) =>
    l10n.commonLongDate(date.toDateTime());

/// Month and day of an [instant] in the user's `GET /me.timezone`: `9월 30일` (docs/02 §6.6).
String formatInstantMonthDay(
  DateTime instant,
  String timeZone,
  TimeZoneSupport support,
  AppLocalizations l10n,
) => l10n.commonMonthDay(support.wallClock(instant, timeZone));

/// Minutes: `< 60` → `{m}분`, otherwise `{h}시간 {m}분` with `m = 0` omitted.
String formatMinutes(int minutes, AppLocalizations l10n) {
  if (minutes < 60) {
    return l10n.commonMinutes(minutes);
  }
  final hours = minutes ~/ 60;
  final rest = minutes % 60;
  return rest == 0 ? l10n.commonHours(hours) : l10n.commonHoursMinutes(hours, rest);
}

/// Day start hour option: 0 is midnight, 1~6 are early-morning hours.
String formatDayStartHour(int hour, AppLocalizations l10n) =>
    hour == 0 ? l10n.commonDayStartMidnight : l10n.commonDayStartHour(hour);
