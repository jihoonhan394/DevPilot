import 'package:flutter/foundation.dart';

/// A calendar date without time or zone, the client form of the API `yyyy-MM-dd` plan date
/// (docs/05 §1.1, docs/02 §6.6). User-facing "today" is always the server's `GET /me.today`.
@immutable
final class LocalDate implements Comparable<LocalDate> {
  const LocalDate(this.year, this.month, this.day);

  /// Parses `yyyy-MM-dd`. Throws [FormatException] for anything else.
  factory LocalDate.parse(String value) {
    final match = _pattern.firstMatch(value);
    if (match == null) {
      throw FormatException('Not a yyyy-MM-dd date', value);
    }
    final date = LocalDate(
      int.parse(match.group(1)!),
      int.parse(match.group(2)!),
      int.parse(match.group(3)!),
    );
    // DateTime rolls 2026-02-30 over to March; a real calendar date survives unchanged.
    if (LocalDate.fromDateTime(date.toDateTime()) != date) {
      throw FormatException('Not a calendar date', value);
    }
    return date;
  }

  /// The date part of [value] read in its own zone fields.
  factory LocalDate.fromDateTime(DateTime value) => LocalDate(value.year, value.month, value.day);

  static final _pattern = RegExp(r'^(\d{4})-(\d{2})-(\d{2})$');

  static LocalDate? tryParse(String? value) {
    if (value == null) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } on FormatException {
      return null;
    }
  }

  final int year;
  final int month;
  final int day;

  /// Midnight UTC of this date; formatting it prints exactly these fields.
  DateTime toDateTime() => DateTime.utc(year, month, day);

  /// 1 = Monday … 7 = Sunday.
  int get weekday => toDateTime().weekday;

  LocalDate addDays(int days) => LocalDate.fromDateTime(toDateTime().add(Duration(days: days)));

  /// Calendar month arithmetic; the day is clamped to the last day of the target month.
  LocalDate addMonths(int months) {
    final monthIndex = year * 12 + (month - 1) + months;
    final targetYear = monthIndex ~/ 12;
    final targetMonth = monthIndex % 12 + 1;
    final lastDay = DateTime.utc(targetYear, targetMonth + 1, 0).day;
    return LocalDate(targetYear, targetMonth, day > lastDay ? lastDay : day);
  }

  LocalDate addYears(int years) => addMonths(years * 12);

  /// Whole days from this date to [other] (negative when [other] is earlier).
  int daysUntil(LocalDate other) => other.toDateTime().difference(toDateTime()).inDays;

  bool isBefore(LocalDate other) => compareTo(other) < 0;

  bool isAfter(LocalDate other) => compareTo(other) > 0;

  String toIso() =>
      '${year.toString().padLeft(4, '0')}-${month.toString().padLeft(2, '0')}-'
      '${day.toString().padLeft(2, '0')}';

  @override
  int compareTo(LocalDate other) => toDateTime().compareTo(other.toDateTime());

  @override
  bool operator ==(Object other) =>
      other is LocalDate && other.year == year && other.month == month && other.day == day;

  @override
  int get hashCode => Object.hash(year, month, day);

  @override
  String toString() => toIso();
}
