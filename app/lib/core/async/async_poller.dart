import 'dart:async';

/// What the poller is doing, shown by `AsyncStatusIndicator` (docs/02 §6.3).
enum AsyncPollPhase {
  /// Not started, or stopped by the screen.
  idle,

  /// Waiting for the next 2-second check.
  polling,

  /// The resource reached its end state.
  finished,

  /// 90 checks (3 minutes) passed without an end state: "나중에 다시 확인" + "다시 확인".
  checkLater,

  /// Five checks in a row failed: the screen shows the error with "다시 확인".
  failed,
}

/// Polls an asynchronous AI resource (docs/02 §6.3, docs/05 §1.8).
///
/// Every [interval] (2 s) it calls [check], at most [maxChecks] (90) times per window. A failed
/// check still counts; [maxConsecutiveFailures] (5) in a row stop it. [pause] (hidden browser tab)
/// keeps the remaining checks for [unpause]. While [pausedUntil] is in the future (`429
/// RATE_LIMITED`, docs/02 §6.6) the checks wait without using up the window.
final class AsyncPoller {
  AsyncPoller({
    required this._check,
    required this._onPhase,
    required this._now,
    DateTime? Function()? pausedUntil,
    this.interval = const Duration(seconds: 2),
    this.maxChecks = 90,
    this.maxConsecutiveFailures = 5,
  }) : _pausedUntil = pausedUntil ?? _never;

  final Duration interval;
  final int maxChecks;
  final int maxConsecutiveFailures;
  final Future<bool> Function() _check;
  final void Function(AsyncPollPhase phase) _onPhase;
  final DateTime Function() _now;
  final DateTime? Function() _pausedUntil;

  Timer? _timer;
  var _checks = 0;
  var _failures = 0;
  var _paused = false;
  var _running = false;
  var _disposed = false;
  var _phase = AsyncPollPhase.idle;

  AsyncPollPhase get phase => _phase;

  /// Starts a new 90-check window. With [immediately] the first check runs now ("다시 확인").
  void start({bool immediately = false}) {
    if (_disposed) {
      return;
    }
    _timer?.cancel();
    _checks = 0;
    _failures = 0;
    _setPhase(AsyncPollPhase.polling);
    if (immediately) {
      unawaited(_tick());
    } else {
      _schedule(interval);
    }
  }

  /// Stops without an end state (the screen no longer needs results).
  void stop() {
    _timer?.cancel();
    _timer = null;
    if (_phase == AsyncPollPhase.polling) {
      _setPhase(AsyncPollPhase.idle);
    }
  }

  /// Browser tab hidden: no checks until [unpause].
  void pause() {
    _paused = true;
    _timer?.cancel();
  }

  /// Browser tab visible again: continues with the checks left in the window.
  void unpause() {
    if (!_paused) {
      return;
    }
    _paused = false;
    if (_phase == AsyncPollPhase.polling && !_running) {
      _schedule(interval);
    }
  }

  void dispose() {
    _disposed = true;
    _timer?.cancel();
  }

  void _schedule(Duration delay) {
    _timer?.cancel();
    if (_paused || _disposed) {
      return;
    }
    _timer = Timer(delay, () => unawaited(_tick()));
  }

  Future<void> _tick() async {
    if (_paused || _disposed || _phase != AsyncPollPhase.polling) {
      return;
    }
    final until = _pausedUntil();
    final now = _now();
    if (until != null && until.isAfter(now)) {
      _schedule(until.difference(now));
      return;
    }
    _checks++;
    _running = true;
    bool done;
    try {
      done = await _check();
      _failures = 0;
    } on Exception {
      // A failed check (network or server) counts toward the window and the failure streak.
      done = false;
      _failures++;
    }
    _running = false;
    if (_disposed || _phase != AsyncPollPhase.polling) {
      return;
    }
    if (done) {
      _setPhase(AsyncPollPhase.finished);
    } else if (_failures >= maxConsecutiveFailures) {
      _setPhase(AsyncPollPhase.failed);
    } else if (_checks >= maxChecks) {
      _setPhase(AsyncPollPhase.checkLater);
    } else {
      _schedule(interval);
    }
  }

  void _setPhase(AsyncPollPhase phase) {
    _phase = phase;
    if (!_disposed) {
      _onPhase(phase);
    }
  }

  static DateTime? _never() => null;
}
