/// Persists the dev access token for the lifetime of the browser tab.
///
/// The web build uses `sessionStorage`, never `localStorage`, so closing the tab ends the session
/// (docs/07 §3.3).
abstract interface class TokenStore {
  String? read();

  void write(String accessToken);

  void clear();
}

/// In-memory store for non-web platforms and tests.
final class MemoryTokenStore implements TokenStore {
  MemoryTokenStore([this._accessToken]);

  String? _accessToken;

  @override
  String? read() => _accessToken;

  @override
  void write(String accessToken) => _accessToken = accessToken;

  @override
  void clear() => _accessToken = null;
}
