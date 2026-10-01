import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/async/async_poller.dart';
import 'package:flutter_test/flutter_test.dart';

/// `AsyncPoller` rules of docs/02 §6.3: 2 s interval, 90 checks, five failures in a row, pause
/// and the rate-limit wait.
void main() {
  late DateTime now;
  late List<AsyncPollPhase> phases;
  late int checks;

  setUp(() {
    now = DateTime.utc(2026, 9, 19, 3);
    phases = [];
    checks = 0;
  });

  AsyncPoller poller(Future<bool> Function() check, {DateTime? Function()? pausedUntil}) =>
      AsyncPoller(
        check: () {
          checks++;
          return check();
        },
        onPhase: phases.add,
        now: () => now,
        pausedUntil: pausedUntil,
      );

  testWidgets('shouldStopWhenTheResourceFinishes', (tester) async {
    final subject = poller(() async => checks == 3);

    subject.start();
    await tester.pump(const Duration(seconds: 2));
    await tester.pump(const Duration(seconds: 2));
    expect(checks, 2);
    await tester.pump(const Duration(seconds: 2));

    expect(subject.phase, AsyncPollPhase.finished);
    await tester.pump(const Duration(seconds: 10));
    expect(checks, 3);
  });

  testWidgets('shouldAskToCheckLaterAfterNinetyChecksAndRestartOnCheckAgain', (tester) async {
    final subject = poller(() async => false);

    subject.start();
    for (var index = 0; index < 90; index++) {
      await tester.pump(const Duration(seconds: 2));
    }
    expect(checks, 90);
    expect(subject.phase, AsyncPollPhase.checkLater);
    await tester.pump(const Duration(seconds: 10));
    expect(checks, 90);

    subject.start(immediately: true);
    await tester.pump();
    expect(checks, 91);
    expect(subject.phase, AsyncPollPhase.polling);
    subject.dispose();
  });

  testWidgets('shouldStopAfterFiveFailuresInARow', (tester) async {
    final subject = poller(
      () async => throw const ApiException(code: ApiErrorCode.networkError),
    );

    subject.start();
    for (var index = 0; index < 5; index++) {
      await tester.pump(const Duration(seconds: 2));
    }

    expect(subject.phase, AsyncPollPhase.failed);
    expect(checks, 5);
  });

  testWidgets('shouldKeepRemainingChecksWhilePaused', (tester) async {
    final subject = poller(() async => false);

    subject.start();
    await tester.pump(const Duration(seconds: 2));
    subject.pause();
    await tester.pump(const Duration(seconds: 30));
    expect(checks, 1);

    subject.unpause();
    await tester.pump(const Duration(seconds: 2));
    expect(checks, 2);
    subject.dispose();
  });

  testWidgets('shouldWaitOutTheRateLimitWithoutUsingChecks', (tester) async {
    DateTime? until = now.add(const Duration(seconds: 7));
    final subject = poller(() async => false, pausedUntil: () => until);

    subject.start();
    await tester.pump(const Duration(seconds: 2));
    expect(checks, 0);

    now = now.add(const Duration(seconds: 7));
    until = null;
    await tester.pump(const Duration(seconds: 7));
    expect(checks, 1);
    subject.dispose();
  });
}
