import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/auth/dev_token_response.dart';
import 'package:devpilot_app/core/config/app_config.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Obtains access tokens. `AUTH_MODE=dev` is the only implementation until BL-SEC-18.
abstract interface class AuthRepository {
  /// Exchanges an allowlisted [email] for a signed token. Throws `ApiException`
  /// (`USER_NOT_ALLOWED`, `VALIDATION_FAILED`, `RATE_LIMITED`, ...).
  Future<DevTokenResponse> issueDevToken(String email);
}

/// `POST /api/v1/dev/token {email}` (docs/05 §1.4.5). No JWT and no Idempotency-Key.
final class DevTokenAuthRepository implements AuthRepository {
  DevTokenAuthRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<DevTokenResponse> issueDevToken(String email) async {
    final json = await _apiClient.postWithoutSession('/dev/token', body: {'email': email});
    return DevTokenResponse.fromJson(json);
  }
}

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  final authMode = ref.watch(appConfigProvider).authMode;
  return switch (authMode) {
    AuthMode.dev => DevTokenAuthRepository(ref.watch(apiClientProvider)),
    // AppConfig.validate rejects supabase, so this branch is unreachable in a running app.
    AuthMode.supabase => throw UnsupportedError('AUTH_MODE=supabase ships with BL-SEC-18'),
  };
});
