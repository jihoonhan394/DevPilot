import 'dart:convert';

import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:uuid/uuid.dart';

/// Value of the `Idempotency-Key` header (docs/05 §1.7). POST repository methods take it as a
/// required argument so a missing key is a compile error, not a 400 (docs/02 §6.6).
final class IdempotencyKey {
  const IdempotencyKey(this.value);

  /// A new UUID v4: one per user action.
  factory IdempotencyKey.generate() => IdempotencyKey(_uuid.v4());

  static const _uuid = Uuid();

  final String value;

  @override
  bool operator ==(Object other) => other is IdempotencyKey && other.value == value;

  @override
  int get hashCode => value.hashCode;

  @override
  String toString() => 'IdempotencyKey($value)';
}

/// Holds the key of one user action between attempts (docs/02 §6.6, docs/08 §8.4).
///
/// The same action with the same body reuses the key until it succeeds or fails for good, so a
/// retry after a lost response replays the first result instead of creating a second resource.
/// A different body gets a new key; reusing a key with another body is a 422 on the server.
final class IdempotencyKeyCache {
  IdempotencyKeyCache({IdempotencyKey Function()? generate})
    : _generate = generate ?? IdempotencyKey.generate;

  final IdempotencyKey Function() _generate;
  IdempotencyKey? _key;
  String? _bodyFingerprint;

  /// The key for sending [body] now.
  IdempotencyKey keyFor(Map<String, Object?> body) {
    final fingerprint = canonicalJson(body);
    final current = _key;
    if (current != null && fingerprint == _bodyFingerprint) {
      return current;
    }
    _bodyFingerprint = fingerprint;
    return _key = _generate();
  }

  /// Records how the attempt ended. [error] is null on success.
  ///
  /// The key survives only failures after which the same request may be sent again: no response,
  /// a 5xx, or `IDEMPOTENCY_IN_PROGRESS`. Any other 4xx ends the action.
  void settle(Object? error) {
    if (error is ApiException && _canRetryWithSameKey(error)) {
      return;
    }
    _key = null;
    _bodyFingerprint = null;
  }

  static bool _canRetryWithSameKey(ApiException error) {
    final status = error.status;
    return error.isNetworkFailure ||
        error.code == ApiErrorCode.idempotencyInProgress ||
        (status != null && status >= 500);
  }

  /// Key-sorted JSON without whitespace, the same shape the server hashes (docs/05 §1.7).
  static String canonicalJson(Object? value) => jsonEncode(_sorted(value));

  static Object? _sorted(Object? value) {
    if (value is Map<String, Object?>) {
      final keys = value.keys.toList()..sort();
      return {for (final key in keys) key: _sorted(value[key])};
    }
    if (value is List<Object?>) {
      return [for (final item in value) _sorted(item)];
    }
    return value;
  }
}
