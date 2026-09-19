import 'package:devpilot_app/core/auth/token_store_provider.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Why the user was signed out. Passed to `/login?reason=` (docs/02 §2.3).
enum SignOutReason {
  /// `401 AUTHENTICATION_REQUIRED`.
  expired,

  /// `GET /me.status = DELETION_REQUESTED` (docs/02 §2.4 rule 5).
  deleted,
}

/// Current session. The access token is kept in memory here and mirrored to the [TokenStore].
sealed class AuthState {
  const AuthState();

  String? get accessToken;
}

final class SignedIn extends AuthState {
  const SignedIn(this.accessToken);

  @override
  final String accessToken;
}

final class SignedOut extends AuthState {
  const SignedOut({this.reason});

  final SignOutReason? reason;

  @override
  String? get accessToken => null;
}

/// Session state for `AUTH_MODE=dev` (docs/18 §6.3). The router redirects on every change.
final class AuthController extends Notifier<AuthState> {
  @override
  AuthState build() {
    final storedToken = ref.watch(tokenStoreProvider).read();
    return storedToken == null || storedToken.isEmpty ? const SignedOut() : SignedIn(storedToken);
  }

  void signIn(String accessToken) {
    ref.read(tokenStoreProvider).write(accessToken);
    state = SignedIn(accessToken);
  }

  void signOut({SignOutReason? reason}) {
    ref.read(tokenStoreProvider).clear();
    state = SignedOut(reason: reason);
  }

  /// Called on `401 AUTHENTICATION_REQUIRED`. devtoken mode cannot refresh, so the token is dropped.
  void expireSession() {
    if (state is! SignedIn) {
      return;
    }
    ref.read(tokenStoreProvider).clear();
    state = const SignedOut(reason: SignOutReason.expired);
  }
}

final authStateProvider = NotifierProvider<AuthController, AuthState>(AuthController.new);
