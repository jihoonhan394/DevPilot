/// 화면·진입점을 단계별로 여닫는 스위치 (docs/02 §2.5).
///
/// 빌드 시 `--dart-define=DEVPILOT_FEATURES=today,review,…`로 켠다. 값을 주지 않으면 **S3까지 켜진 것으로 본다** —
/// 지금 쓰는 빌드가 그 상태이고, 기본값이 "전부 꺼짐"이면 정의를 빠뜨린 빌드가 빈 화면이 된다.
///
/// 꺼진 기능은 목적지·진입 버튼·라우트를 모두 숨긴다. 라우트 판정은 [RouteAccess]가, 진입 버튼은 각 화면이 [isOn]으로 본다.
library;

/// docs/02 §2.5의 플래그 이름과 켜지는 단계.
abstract final class FeatureFlags {
  /// `--dart-define=DEVPILOT_FEATURES=a,b,c`. 비어 있으면 [_defaults].
  static const _raw = String.fromEnvironment('DEVPILOT_FEATURES');

  /// S3까지 켜지는 것 (docs/02 §2.5). 값을 주지 않은 빌드의 기본값이다.
  static const _defaults = {
    'projects',
    'today',
    'review',
    'replan_preview',
    'training',
    'diagnostics',
    'ai_usage',
    'review_evaluate',
    'read_code',
    'rubber_duck',
    'tips',
    'terms',
  };

  /// docs/02 §2.5가 정한 이름 전체. 여기 없는 이름을 주면 오타이므로 무시하지 않고 [unknown]에 남긴다.
  static const known = {
    ..._defaults,
    'coach',
    'dashboard_full',
    'weekly',
    'calendar',
    'challenge_generate',
    'evidence',
    'account',
    'radar',
  };

  static final Set<String> _enabled = _parse(_raw);

  static Set<String> _parse(String raw) {
    final value = raw.trim();
    if (value.isEmpty) {
      return _defaults;
    }
    return {
      for (final name in value.split(','))
        if (name.trim().isNotEmpty) name.trim(),
    };
  }

  /// 이 기능이 켜져 있는가.
  static bool isOn(String name) => _enabled.contains(name);

  /// 켜 달라고 했지만 docs/02 §2.5에 없는 이름. 오타를 조용히 넘기지 않으려고 남긴다.
  static Set<String> get unknown => _enabled.difference(known);

  /// 지금 켜진 이름 전체. 정렬해 돌려준다.
  static List<String> get enabled => (_enabled.toList()..sort());
}
