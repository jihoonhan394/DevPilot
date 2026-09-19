import 'package:devpilot_app/core/time/clock.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Global `rateLimitedUntil` of docs/02 §6.6: after `429 RATE_LIMITED` every poller waits until
/// the `Retry-After` time has passed, then goes on. Null when nothing is paused.
final class RateLimitPause extends Notifier<DateTime?> {
  /// Used when the 429 has no `Retry-After` header.
  static const defaultPause = Duration(seconds: 5);

  @override
  DateTime? build() => null;

  /// Pauses polling for [retryAfter] from now. A later end replaces an earlier one.
  void pauseFor(Duration? retryAfter) {
    final until = ref.read(clockProvider)().toUtc().add(retryAfter ?? defaultPause);
    final current = state;
    if (current == null || until.isAfter(current)) {
      state = until;
    }
  }
}

final rateLimitPauseProvider = NotifierProvider<RateLimitPause, DateTime?>(RateLimitPause.new);
