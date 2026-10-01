import 'dart:convert';

import 'package:devpilot_app/core/auth/jwt_subject.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/time/time_zone_support_stub.dart';
import 'package:devpilot_app/core/validation/goal_date_rules.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('LocalDate', () {
    test('shouldParseAndFormatPlanDates', () {
      expect(LocalDate.parse('2026-09-19'), const LocalDate(2026, 9, 19));
      expect(const LocalDate(2027, 1, 5).toIso(), '2027-01-05');
      expect(LocalDate.tryParse('2026-02-30'), isNull);
      expect(LocalDate.tryParse('26-9-19'), isNull);
      expect(LocalDate.tryParse(null), isNull);
    });

    test('shouldClampDayWhenAddingMonths', () {
      expect(const LocalDate(2026, 11, 30).addMonths(3), const LocalDate(2027, 2, 28));
      expect(const LocalDate(2027, 5, 31).addMonths(-3), const LocalDate(2027, 2, 28));
      expect(const LocalDate(2028, 2, 29).addYears(1), const LocalDate(2029, 2, 28));
      expect(const LocalDate(2026, 12, 31).addDays(1), const LocalDate(2027, 1, 1));
    });

    test('shouldCompareAndCountDays', () {
      const today = LocalDate(2026, 9, 19);
      expect(today.isBefore(const LocalDate(2026, 9, 20)), isTrue);
      expect(today.isAfter(const LocalDate(2026, 9, 20)), isFalse);
      expect(today.daysUntil(const LocalDate(2026, 10, 1)), 12);
      expect(today.weekday, DateTime.saturday);
    });
  });

  group('GoalDateRules (docs/02 §3.2)', () {
    const today = LocalDate(2026, 9, 19);

    test('shouldRequireCompletionAfterTodayAndWithinThreeYears', () {
      expect(GoalDateRules.completionViolation(null, today), GoalDateViolation.required);
      expect(GoalDateRules.completionViolation(today, today), GoalDateViolation.notFuture);
      expect(GoalDateRules.completionViolation(today.addDays(1), today), isNull);
      expect(GoalDateRules.completionViolation(today.addYears(3), today), isNull);
      expect(
        GoalDateRules.completionViolation(today.addYears(3).addDays(1), today),
        GoalDateViolation.tooFar,
      );
    });
  });

  group('InputRules', () {
    test('shouldTrimDisplayNameAndLimitTo100', () {
      expect(InputRules.isValidDisplayName('  민수 '), isTrue);
      expect(InputRules.isValidDisplayName('   '), isFalse);
      expect(InputRules.isValidDisplayName('a' * 100), isTrue);
      expect(InputRules.isValidDisplayName('a' * 101), isFalse);
    });

    test('shouldAcceptOnlyHttpUrlsWithHost', () {
      expect(InputRules.isHttpUrl('https://github.com/example/order-service'), isTrue);
      expect(InputRules.isHttpUrl('http://localhost:8080/repo'), isTrue);
      expect(InputRules.isHttpUrl('ftp://example.com/repo'), isFalse);
      expect(InputRules.isHttpUrl('github.com/example'), isFalse);
    });

    test('shouldDetectPrivateKeyBlocks', () {
      // Headers are assembled at runtime so the repository holds no literal key header.
      String header(String kind) =>
          '-----'
          'BEGIN $kind'
          ' KEY-----';

      expect(InputRules.containsPrivateKey(header('RSA PRIVATE')), isTrue);
      expect(InputRules.containsPrivateKey(header('PRIVATE')), isTrue);
      expect(InputRules.containsPrivateKey(header('PUBLIC')), isFalse);
    });
  });

  test('shouldConvertInstantToWallClockOfUserZone', () {
    final support = FixedOffsetTimeZoneSupport();
    final instant = DateTime.utc(2026, 10, 5, 15, 40);

    final seoul = support.wallClock(instant, 'Asia/Seoul');
    expect([seoul.month, seoul.day, seoul.hour], [10, 6, 0]);
    expect(support.wallClock(instant, 'Unknown/Zone'), instant);
  });

  test('shouldReadSubjectFromJwtPayloadOnly', () {
    // Built at runtime: header and signature are irrelevant, only the payload is read.
    String segment(Object json) => base64Url.encode(utf8.encode(jsonEncode(json)));
    final token = [
      segment({'alg': 'ES256'}),
      segment({'sub': '5f1c3b1e', 'email': 'x'}),
      'c2ln',
    ].join('.');

    expect(jwtSubject(token), '5f1c3b1e');
    expect(jwtSubject('not-a-jwt'), isNull);
    expect(jwtSubject('a.!!!.c'), isNull);
  });
}
