import 'package:flutter/painting.dart';

/// Light theme color tokens (docs/02 §10.2). Widgets read colors from `Theme.of(context)` and
/// [DevPilotColors], never from here and never as `Color(0x…)` literals.
abstract final class AppColors {
  static const background = Color(0xFFF7F8FA);
  static const surface = Color(0xFFFFFFFF);
  static const surfaceVariant = Color(0xFFEEF1F5);
  static const border = Color(0xFFD5DAE1);
  static const borderStrong = Color(0xFF8A939E);
  static const textPrimary = Color(0xFF1B1F24);
  static const textSecondary = Color(0xFF505A66);
  static const textDisabled = Color(0xFF8A939E);
  static const primary = Color(0xFF2F5BD3);
  static const onPrimary = Color(0xFFFFFFFF);
  static const primaryContainer = Color(0xFFE3EAFB);
  static const onPrimaryContainer = Color(0xFF1C3C94);
  static const focus = Color(0xFF2F5BD3);
  static const success = Color(0xFF1E7B45);
  static const successContainer = Color(0xFFE6F4EC);
  static const info = Color(0xFF1F5FA8);
  static const infoContainer = Color(0xFFE8F1FB);
  static const warning = Color(0xFF8A5A00);
  static const warningContainer = Color(0xFFFFF4DB);
  static const danger = Color(0xFFB42318);
  static const dangerContainer = Color(0xFFFDECEA);
  static const neutral = Color(0xFF505A66);
  static const neutralContainer = Color(0xFFEEF1F5);
  static const skeleton = Color(0xFFE4E8ED);
  static const codeBackground = Color(0xFFF3F5F8);
  static const scrim = Color(0x66000000);
}
