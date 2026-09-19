/// Spacing, shape, size and motion tokens (docs/02 §10.4, §10.5).
abstract final class AppSpacing {
  static const xs = 4.0;
  static const sm = 8.0;
  static const md = 12.0;
  static const lg = 16.0;
  static const xl = 24.0;
  static const xxl = 32.0;

  /// Side margin of screen content on mobile.
  static const screen = lg;

  /// Gap between sections.
  static const section = xl;
}

abstract final class AppRadius {
  static const sm = 6.0;
  static const md = 8.0;
  static const lg = 12.0;
  static const full = 999.0;
}

abstract final class AppSizes {
  static const button = 48.0;
  static const input = 48.0;
  static const listTile = 56.0;
  static const navBar = 64.0;

  /// Minimum touch target (docs/02 A-1).
  static const minTouchTarget = 48.0;
}

/// Layout breakpoints and content widths (docs/02 §2.1).
abstract final class AppBreakpoints {
  /// `< 600` is Mobile.
  static const tablet = 600.0;

  /// `≥ 1024` is Desktop.
  static const desktop = 1024.0;

  static const tabletContentWidth = 720.0;
  static const desktopContentWidth = 960.0;

  /// Width of centered cards such as onboarding steps on wide screens.
  static const formCardWidth = 560.0;

  static const railWidth = 80.0;
  static const extendedRailWidth = 220.0;
}

abstract final class AppMotion {
  static const fast = Duration(milliseconds: 150);
  static const page = Duration(milliseconds: 250);
  static const shimmer = Duration(milliseconds: 1200);
}
