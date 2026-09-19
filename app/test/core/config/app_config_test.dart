import 'package:devpilot_app/core/config/app_config.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  AppConfigResult parse(String authMode, String apiBaseUrl) =>
      AppConfig.parse(authMode: authMode, apiBaseUrl: apiBaseUrl);

  test('shouldUseSameOriginWhenApiBaseUrlIsEmpty', () {
    final result = parse('dev', '');

    expect(result, isA<AppConfigValid>());
    final config = (result as AppConfigValid).config;
    expect(config.authMode, AuthMode.dev);
    expect(config.apiRootUrl, '/api/v1');
  });

  test('shouldNormalizeOriginWhenApiBaseUrlHasTrailingSlash', () {
    final result = parse('dev', 'http://localhost:8080/');

    expect((result as AppConfigValid).config.apiRootUrl, 'http://localhost:8080/api/v1');
  });

  test('shouldRejectAuthModeWhenValueIsNotDev', () {
    for (final authMode in ['', 'DEV', 'devtoken', 'supabase']) {
      final result = parse(authMode, '');

      expect(result, isA<AppConfigInvalid>(), reason: authMode);
      expect((result as AppConfigInvalid).invalidKey, AppConfig.authModeKey);
    }
  });

  test('shouldRejectApiBaseUrlWhenValueIsNotAnHttpOrigin', () {
    for (final apiBaseUrl in [
      'localhost:8080',
      'ftp://localhost',
      'http://localhost:8080/api',
      'http://localhost:8080?debug=1',
      'http://user@localhost:8080',
      '/api',
    ]) {
      final result = parse('dev', apiBaseUrl);

      expect(result, isA<AppConfigInvalid>(), reason: apiBaseUrl);
      expect((result as AppConfigInvalid).invalidKey, AppConfig.apiBaseUrlKey);
    }
  });
}
