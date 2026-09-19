import 'package:devpilot_app/core/theme/app_colors.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:flutter/material.dart';

/// Light theme with bundled fonts (docs/18 §6.6, docs/02 §10). Dark mode is Later.
abstract final class AppTheme {
  static const fontFamily = 'Pretendard';
  static const codeFontFamily = 'JetBrainsMono';

  /// Code and identifiers. Hangul falls back to the bundled Pretendard, never to a CDN font.
  static const codeTextStyle = TextStyle(
    fontFamily: codeFontFamily,
    fontFamilyFallback: [fontFamily],
    fontSize: 14,
    height: 20 / 14,
  );

  static const _colorScheme = ColorScheme(
    brightness: Brightness.light,
    primary: AppColors.primary,
    onPrimary: AppColors.onPrimary,
    primaryContainer: AppColors.primaryContainer,
    onPrimaryContainer: AppColors.onPrimaryContainer,
    secondary: AppColors.textSecondary,
    onSecondary: AppColors.onPrimary,
    secondaryContainer: AppColors.primaryContainer,
    onSecondaryContainer: AppColors.onPrimaryContainer,
    error: AppColors.danger,
    onError: AppColors.onPrimary,
    errorContainer: AppColors.dangerContainer,
    onErrorContainer: AppColors.danger,
    surface: AppColors.surface,
    onSurface: AppColors.textPrimary,
    onSurfaceVariant: AppColors.textSecondary,
    surfaceContainerHighest: AppColors.surfaceVariant,
    surfaceContainerHigh: AppColors.surfaceVariant,
    surfaceContainer: AppColors.surface,
    surfaceContainerLow: AppColors.surface,
    surfaceContainerLowest: AppColors.surface,
    outline: AppColors.borderStrong,
    outlineVariant: AppColors.border,
    scrim: AppColors.scrim,
  );

  // docs/02 §10.3. Pretendard is bundled in 400, 600 and 700 only; styles not listed here keep the
  // Material defaults, and a 500 weight renders with the nearest bundled face.
  static const _textTheme = TextTheme(
    displaySmall: TextStyle(fontSize: 28, height: 36 / 28, fontWeight: FontWeight.w700),
    titleLarge: TextStyle(fontSize: 22, height: 28 / 22, fontWeight: FontWeight.w600),
    titleMedium: TextStyle(fontSize: 18, height: 24 / 18, fontWeight: FontWeight.w600),
    bodyLarge: TextStyle(fontSize: 16, height: 24 / 16, fontWeight: FontWeight.w400),
    bodyMedium: TextStyle(fontSize: 15, height: 22 / 15, fontWeight: FontWeight.w400),
    bodySmall: TextStyle(fontSize: 13, height: 18 / 13, fontWeight: FontWeight.w400),
    labelLarge: TextStyle(fontSize: 15, height: 20 / 15, fontWeight: FontWeight.w600),
    labelSmall: TextStyle(fontSize: 12, height: 16 / 12, fontWeight: FontWeight.w600),
  );

  static ThemeData light() {
    const buttonShape = RoundedRectangleBorder(
      borderRadius: BorderRadius.all(Radius.circular(AppRadius.md)),
    );
    const minimumButtonSize = Size(AppSizes.minTouchTarget, AppSizes.button);
    return ThemeData(
      colorScheme: _colorScheme,
      fontFamily: fontFamily,
      textTheme: _textTheme,
      scaffoldBackgroundColor: AppColors.background,
      extensions: const [DevPilotColors.light],
      materialTapTargetSize: MaterialTapTargetSize.padded,
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.surface,
        foregroundColor: AppColors.textPrimary,
        surfaceTintColor: AppColors.surface,
      ),
      cardTheme: const CardThemeData(
        color: AppColors.surface,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          side: BorderSide(color: AppColors.border),
          borderRadius: BorderRadius.all(Radius.circular(AppRadius.lg)),
        ),
      ),
      inputDecorationTheme: const InputDecorationTheme(
        border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(AppRadius.md))),
        filled: true,
        fillColor: AppColors.surface,
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(minimumSize: minimumButtonSize, shape: buttonShape),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(minimumSize: minimumButtonSize, shape: buttonShape),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(minimumSize: minimumButtonSize, shape: buttonShape),
      ),
      chipTheme: const ChipThemeData(
        shape: StadiumBorder(side: BorderSide(color: AppColors.borderStrong)),
        selectedColor: AppColors.primaryContainer,
        showCheckmark: true,
      ),
      snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating),
      navigationBarTheme: const NavigationBarThemeData(
        height: AppSizes.navBar,
        backgroundColor: AppColors.surface,
        indicatorColor: AppColors.primaryContainer,
      ),
      navigationRailTheme: const NavigationRailThemeData(
        backgroundColor: AppColors.surface,
        indicatorColor: AppColors.primaryContainer,
      ),
      dividerTheme: const DividerThemeData(color: AppColors.border, space: 1),
    );
  }
}
