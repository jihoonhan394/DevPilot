import 'package:devpilot_app/core/time/time_zone_support_stub.dart'
    if (dart.library.js_interop) 'package:devpilot_app/core/time/time_zone_support_web.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// IANA time zone data from the browser's `Intl` API.
///
/// The app shows instants in the user's `GET /me.timezone`, not the device zone (docs/02 §6.6).
/// The browser already carries the IANA database, so no zone data is bundled.
abstract interface class TimeZoneSupport {
  /// Zone of this device (`Intl.DateTimeFormat().resolvedOptions().timeZone`), or null.
  String? deviceTimeZone();

  /// IANA region IDs the picker offers (`Intl.supportedValuesOf('timeZone')`).
  List<String> availableTimeZones();

  /// Wall-clock fields of [instant] in [timeZone], returned in a UTC [DateTime] whose fields are
  /// the local ones. Unknown zones fall back to UTC.
  DateTime wallClock(DateTime instant, String timeZone);
}

/// Fallback zone when the browser gives none (docs/02 SCR-ONBOARDING step 2).
const defaultTimeZone = 'Asia/Seoul';

final timeZoneSupportProvider = Provider<TimeZoneSupport>(
  (ref) => createPlatformTimeZoneSupport(),
);
