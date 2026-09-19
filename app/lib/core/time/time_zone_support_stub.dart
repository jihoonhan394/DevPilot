import 'package:devpilot_app/core/time/time_zone_support.dart';

/// Non-web fallback (VM tests): a few fixed-offset zones, enough for deterministic tests.
TimeZoneSupport createPlatformTimeZoneSupport() => FixedOffsetTimeZoneSupport();

final class FixedOffsetTimeZoneSupport implements TimeZoneSupport {
  static const _offsetHours = {'Asia/Seoul': 9, 'Asia/Tokyo': 9, 'UTC': 0, 'Europe/London': 0};

  @override
  String? deviceTimeZone() => defaultTimeZone;

  @override
  List<String> availableTimeZones() => _offsetHours.keys.toList()..sort();

  @override
  DateTime wallClock(DateTime instant, String timeZone) =>
      instant.toUtc().add(Duration(hours: _offsetHours[timeZone] ?? 0));
}
