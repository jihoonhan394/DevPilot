import 'dart:convert';

/// The `sub` claim of [accessToken], read without verifying the signature.
///
/// Used only as a local storage namespace (the onboarding draft key is per external auth id,
/// docs/02 SCR-ONBOARDING). Never used for any access decision; the server verifies the token.
String? jwtSubject(String accessToken) {
  final parts = accessToken.split('.');
  if (parts.length != 3) {
    return null;
  }
  try {
    final payload = utf8.decode(base64Url.decode(base64Url.normalize(parts[1])));
    final Object? claims = jsonDecode(payload);
    if (claims is Map<String, Object?>) {
      final subject = claims['sub'];
      return subject is String && subject.isNotEmpty ? subject : null;
    }
    return null;
  } on FormatException {
    return null;
  }
}
