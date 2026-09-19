import 'dart:js_interop';

import 'package:devpilot_app/core/time/time_zone_support.dart';

/// Web build: reads the browser's `Intl` API.
TimeZoneSupport createPlatformTimeZoneSupport() => _IntlTimeZoneSupport();

@JS('Intl.DateTimeFormat')
extension type _DateTimeFormat._(JSObject _) implements JSObject {
  external factory _DateTimeFormat([JSString locales, JSAny? options]);

  external _ResolvedOptions resolvedOptions();

  external JSArray<_FormatPart> formatToParts(_JsDate date);
}

extension type _ResolvedOptions._(JSObject _) implements JSObject {
  external JSString? get timeZone;
}

extension type _FormatPart._(JSObject _) implements JSObject {
  external JSString get type;

  external JSString get value;
}

@JS('Date')
extension type _JsDate._(JSObject _) implements JSObject {
  external factory _JsDate(JSNumber millisecondsSinceEpoch);
}

@JS('Intl.supportedValuesOf')
external JSArray<JSString> _supportedValuesOf(JSString key);

final class _IntlTimeZoneSupport implements TimeZoneSupport {
  List<String>? _zones;

  @override
  String? deviceTimeZone() {
    try {
      final zone = _DateTimeFormat().resolvedOptions().timeZone?.toDart;
      return zone == null || zone.isEmpty ? null : zone;
    } on Object {
      // Old browsers without Intl time zone support: the caller uses its fallback zone.
      return null;
    }
  }

  @override
  List<String> availableTimeZones() {
    final cached = _zones;
    if (cached != null) {
      return cached;
    }
    try {
      final zones = _supportedValuesOf('timeZone'.toJS).toDart.map((zone) => zone.toDart).toList();
      return _zones = zones;
    } on Object {
      // Intl.supportedValuesOf is missing (Safari < 15.4): offer the fallback zone only.
      return _zones = const [defaultTimeZone];
    }
  }

  @override
  DateTime wallClock(DateTime instant, String timeZone) {
    final utc = instant.toUtc();
    try {
      final options = <String, Object?>{
        'timeZone': timeZone,
        'hourCycle': 'h23',
        'year': 'numeric',
        'month': 'numeric',
        'day': 'numeric',
        'hour': 'numeric',
        'minute': 'numeric',
        'second': 'numeric',
      }.jsify();
      final parts = _DateTimeFormat(
        'en-US'.toJS,
        options,
      ).formatToParts(_JsDate(utc.millisecondsSinceEpoch.toJS)).toDart;
      final fields = {for (final part in parts) part.type.toDart: part.value.toDart};
      return DateTime.utc(
        int.parse(fields['year']!),
        int.parse(fields['month']!),
        int.parse(fields['day']!),
        int.parse(fields['hour']!) % 24,
        int.parse(fields['minute']!),
        int.parse(fields['second']!),
      );
    } on Object {
      // Unknown zone name: show UTC rather than failing the whole screen.
      return utc;
    }
  }
}
