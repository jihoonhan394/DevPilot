import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/core/auth/auth_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// SCR-LOGIN (`AUTH_MODE=dev`): requests a dev token and starts the session (docs/02 §3.4).
///
/// State is the last submit: data = idle or done, loading = in flight, error = `ApiException`.
final class LoginController extends Notifier<AsyncValue<void>> {
  /// Same limit as the server `DevTokenRequest.email` (`@Size(max = 254)`).
  static const maxEmailLength = 254;

  static final _emailPattern = RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$');

  /// Client-side check so the button stays disabled instead of provoking a 400.
  static bool isValidEmail(String email) {
    final trimmed = email.trim();
    return trimmed.length <= maxEmailLength && _emailPattern.hasMatch(trimmed);
  }

  @override
  AsyncValue<void> build() => const AsyncData<void>(null);

  Future<void> submit(String email) async {
    if (state.isLoading || !isValidEmail(email)) {
      return;
    }
    state = const AsyncLoading<void>();
    final result = await AsyncValue.guard(() async {
      final token = await ref.read(authRepositoryProvider).issueDevToken(email.trim());
      ref.read(authStateProvider.notifier).signIn(token.accessToken);
    });
    // A successful sign-in navigates away and may dispose this controller.
    if (ref.mounted) {
      state = result;
    }
  }
}

final loginControllerProvider = NotifierProvider.autoDispose<LoginController, AsyncValue<void>>(
  LoginController.new,
);
