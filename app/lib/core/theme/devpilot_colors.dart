import 'package:devpilot_app/core/theme/app_colors.dart';
import 'package:flutter/material.dart';

/// Badge tones of docs/02 §6.1: foreground `color.{tone}` on `color.{tone}Container`.
enum AppTone { success, info, warning, danger, neutral, primary }

/// Foreground and background of one [AppTone].
@immutable
final class ToneColors {
  const ToneColors({required this.foreground, required this.background});

  final Color foreground;
  final Color background;
}

/// Color tokens that `ColorScheme` has no slot for (docs/02 §10.1).
@immutable
final class DevPilotColors extends ThemeExtension<DevPilotColors> {
  const DevPilotColors({
    required this.success,
    required this.successContainer,
    required this.info,
    required this.infoContainer,
    required this.warning,
    required this.warningContainer,
    required this.danger,
    required this.dangerContainer,
    required this.neutral,
    required this.neutralContainer,
    required this.skeleton,
    required this.codeBackground,
    required this.textDisabled,
    required this.focus,
  });

  static const light = DevPilotColors(
    success: AppColors.success,
    successContainer: AppColors.successContainer,
    info: AppColors.info,
    infoContainer: AppColors.infoContainer,
    warning: AppColors.warning,
    warningContainer: AppColors.warningContainer,
    danger: AppColors.danger,
    dangerContainer: AppColors.dangerContainer,
    neutral: AppColors.neutral,
    neutralContainer: AppColors.neutralContainer,
    skeleton: AppColors.skeleton,
    codeBackground: AppColors.codeBackground,
    textDisabled: AppColors.textDisabled,
    focus: AppColors.focus,
  );

  final Color success;
  final Color successContainer;
  final Color info;
  final Color infoContainer;
  final Color warning;
  final Color warningContainer;
  final Color danger;
  final Color dangerContainer;
  final Color neutral;
  final Color neutralContainer;
  final Color skeleton;
  final Color codeBackground;
  final Color textDisabled;
  final Color focus;

  /// The tokens of the current theme; falls back to [light] outside a DevPilot theme.
  static DevPilotColors of(BuildContext context) =>
      Theme.of(context).extension<DevPilotColors>() ?? light;

  ToneColors tone(AppTone tone, ColorScheme scheme) => switch (tone) {
    AppTone.success => ToneColors(foreground: success, background: successContainer),
    AppTone.info => ToneColors(foreground: info, background: infoContainer),
    AppTone.warning => ToneColors(foreground: warning, background: warningContainer),
    AppTone.danger => ToneColors(foreground: danger, background: dangerContainer),
    AppTone.neutral => ToneColors(foreground: neutral, background: neutralContainer),
    AppTone.primary => ToneColors(
      foreground: scheme.onPrimaryContainer,
      background: scheme.primaryContainer,
    ),
  };

  @override
  DevPilotColors copyWith() => this;

  @override
  DevPilotColors lerp(ThemeExtension<DevPilotColors>? other, double t) {
    if (other is! DevPilotColors) {
      return this;
    }
    Color mix(Color from, Color to) => Color.lerp(from, to, t)!;
    return DevPilotColors(
      success: mix(success, other.success),
      successContainer: mix(successContainer, other.successContainer),
      info: mix(info, other.info),
      infoContainer: mix(infoContainer, other.infoContainer),
      warning: mix(warning, other.warning),
      warningContainer: mix(warningContainer, other.warningContainer),
      danger: mix(danger, other.danger),
      dangerContainer: mix(dangerContainer, other.dangerContainer),
      neutral: mix(neutral, other.neutral),
      neutralContainer: mix(neutralContainer, other.neutralContainer),
      skeleton: mix(skeleton, other.skeleton),
      codeBackground: mix(codeBackground, other.codeBackground),
      textDisabled: mix(textDisabled, other.textDisabled),
      focus: mix(focus, other.focus),
    );
  }
}
