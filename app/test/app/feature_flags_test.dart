import 'package:devpilot_app/app/feature_flags.dart';
import 'package:flutter_test/flutter_test.dart';

/// 화면·진입점 스위치 (docs/02 §2.5).
void main() {
  /// 값을 주지 않은 빌드는 S3까지 켜진 것으로 본다 — 기본값이 "전부 꺼짐"이면 빈 화면이 된다.
  test('shouldDefaultToWhatIsShippedThroughS3', () {
    expect(FeatureFlags.isOn('today'), isTrue);
    expect(FeatureFlags.isOn('rubber_duck'), isTrue);
    expect(FeatureFlags.isOn('terms'), isTrue);
  });

  /// 아직 안 만든 단계는 꺼져 있다.
  test('shouldLeaveLaterSprintsOff', () {
    expect(FeatureFlags.isOn('coach'), isFalse);
    expect(FeatureFlags.isOn('radar'), isFalse);
    expect(FeatureFlags.isOn('evidence'), isFalse);
  });

  /// docs/02 §2.5에 적힌 이름은 전부 알고 있어야 한다.
  test('shouldKnowEveryNameTheSpecLists', () {
    expect(FeatureFlags.known, contains('challenge_generate'));
    expect(FeatureFlags.known, contains('dashboard_full'));
    expect(FeatureFlags.unknown, isEmpty);
  });

  test('shouldNotBeOnForANameNobodyDefined', () {
    expect(FeatureFlags.isOn('nonexistent'), isFalse);
  });
}
