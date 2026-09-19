import 'package:devpilot_app/core/auth/token_store.dart';
import 'package:web/web.dart' as web;

/// Web build: the token survives reloads of the same tab only.
TokenStore createPlatformTokenStore() => _SessionStorageTokenStore();

final class _SessionStorageTokenStore implements TokenStore {
  static const _storageKey = 'devpilot.accessToken';

  @override
  String? read() => web.window.sessionStorage.getItem(_storageKey);

  @override
  void write(String accessToken) => web.window.sessionStorage.setItem(_storageKey, accessToken);

  @override
  void clear() => web.window.sessionStorage.removeItem(_storageKey);
}
