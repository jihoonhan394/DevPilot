import 'package:flutter_riverpod/flutter_riverpod.dart';

/// How the app signs users in (docs/18 §6.3, docs/03 §4.2).
enum AuthMode {
  /// `POST /api/v1/dev/token` with an allowlisted email. Default, also in production.
  dev,

  /// Supabase Auth. Later (BL-SEC-18): [AppConfig.validate] rejects it until that sign-in exists.
  supabase,
}

/// Build-time settings passed with `--dart-define` or `--dart-define-from-file`.
final class AppConfig {
  const AppConfig({required this.authMode, required this.apiBaseUrl});

  static const authModeKey = 'AUTH_MODE';
  static const apiBaseUrlKey = 'API_BASE_URL';

  // String.fromEnvironment must be const to be resolved at compile time.
  static const _authModeValue = String.fromEnvironment(authModeKey, defaultValue: 'dev');
  static const _apiBaseUrlValue = String.fromEnvironment(apiBaseUrlKey);

  final AuthMode authMode;

  /// Origin of the backend without a trailing slash. Empty means same-origin (production).
  final String apiBaseUrl;

  /// Base URL for Dio. Every API path is appended to `/api/v1`.
  String get apiRootUrl => '$apiBaseUrl/api/v1';

  /// Validates the compiled-in values.
  static AppConfigResult validate() =>
      parse(authMode: _authModeValue, apiBaseUrl: _apiBaseUrlValue);

  /// Validates raw values. Separate from [validate] so tests can pass arbitrary input.
  static AppConfigResult parse({required String authMode, required String apiBaseUrl}) {
    // supabase is a known mode, but its sign-in flow ships with BL-SEC-18 (Later).
    if (authMode != AuthMode.dev.name) {
      return const AppConfigInvalid(authModeKey);
    }
    final normalizedBaseUrl = _normalizeBaseUrl(apiBaseUrl);
    if (normalizedBaseUrl == null) {
      return const AppConfigInvalid(apiBaseUrlKey);
    }
    return AppConfigValid(AppConfig(authMode: AuthMode.dev, apiBaseUrl: normalizedBaseUrl));
  }

  /// Accepts an empty value or an http(s) origin such as `http://localhost:8080`.
  static String? _normalizeBaseUrl(String rawValue) {
    final value = rawValue.trim();
    if (value.isEmpty) {
      return '';
    }
    final uri = Uri.tryParse(value);
    final isOrigin =
        uri != null &&
        (uri.scheme == 'http' || uri.scheme == 'https') &&
        uri.host.isNotEmpty &&
        uri.userInfo.isEmpty &&
        (uri.path.isEmpty || uri.path == '/') &&
        !uri.hasQuery &&
        !uri.hasFragment;
    if (!isOrigin) {
      return null;
    }
    return uri.origin;
  }
}

/// Result of [AppConfig.validate].
sealed class AppConfigResult {
  const AppConfigResult();
}

final class AppConfigValid extends AppConfigResult {
  const AppConfigValid(this.config);

  final AppConfig config;
}

final class AppConfigInvalid extends AppConfigResult {
  const AppConfigInvalid(this.invalidKey);

  /// The dart-define key whose value was rejected.
  final String invalidKey;
}

/// Injected once in `main.dart` after [AppConfig.validate] succeeds.
final appConfigProvider = Provider<AppConfig>(
  (ref) => throw StateError('appConfigProvider must be overridden in main.dart'),
);
