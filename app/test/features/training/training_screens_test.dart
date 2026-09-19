import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/features/training/data/challenge_models.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/test_app.dart';
import '../../support/training_fixtures.dart';
import '../../support/widget_actions.dart';

/// SCR-TRAINING-LIST and SCR-CHALLENGE-DETAIL (BL-CLI-21, docs/02 §3.7, AC-04).
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend(me: testMe(aiStatus: AiStatus.enabled)));

  testWidgets('shouldListPracticeChallengesAndOpenTheDetail', (tester) async {
    backend.trainingRepository.challenges = [
      testChallengeSummary(),
      testChallengeSummary(
        id: otherChallengeId,
        title: '트랜잭션 전파 수정하기',
        origin: ContentOrigin.aiGenerated,
        lastAttempt: LastAttemptView(
          attemptId: attemptId,
          status: AttemptStatus.started,
          startedAt: DateTime.utc(2026, 9, 18),
        ),
      ),
    ];
    await pumpApp(tester, backend: backend, at: AppRoutes.training);

    expect(backend.trainingRepository.listQueries.single.purpose, ChallengePurpose.practice);
    expect(find.text('설정 로더 예외 처리 개선'), findsOneWidget);
    expect(find.text('L2 작은 변형 · 약 15분'), findsNWidgets(2));
    expect(find.text('풀이 중'), findsOneWidget);
    expect(find.bySemanticsLabel('AI가 만든 내용'), findsOneWidget);

    await tapKey(tester, 'training.challenge.$challengeId');
    expect(locationOf(tester), AppRoutes.challengeDetail(challengeId));
    expect(find.text('파일에서 설정값을 읽는 유틸리티가 모든 예외를 삼키고 null을 반환한다.'), findsOneWidget);
    expect(find.text('• JDK만 사용'), findsOneWidget);
  });

  testWidgets('shouldShowTheEmptyStateWithTodayButton', (tester) async {
    backend.trainingRepository.challenges = [];
    await pumpApp(tester, backend: backend, at: AppRoutes.training);

    expect(find.text('이 기술로 풀 수 있는 문제가 아직 없어요.'), findsOneWidget);
    await tapKey(tester, 'training.list.todayButton');
    expect(locationOf(tester), AppRoutes.today);
  });

  testWidgets('shouldFilterBySkillFromTheQuery', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.trainingFor(javaExceptionSkill.id));

    expect(backend.trainingRepository.listQueries.last.skillId, javaExceptionSkill.id);
  });

  testWidgets('shouldStartAnAttemptAndCarryTheTaskId', (tester) async {
    await pumpApp(
      tester,
      backend: backend,
      at: AppRoutes.challengeDetail(challengeId, taskId: mainTaskIdForTraining),
    );

    await tapKey(tester, 'challenge.startButton');

    final attempt = backend.trainingRepository.attempts.values.single;
    expect(backend.trainingRepository.starts, hasLength(1));
    expect(locationOf(tester), AppRoutes.attempt(attempt.id, taskId: mainTaskIdForTraining));
    expect(find.text('① 먼저 어떻게 풀지 설명해 주세요'), findsOneWidget);
  });

  testWidgets('shouldContinueTheRunningAttempt', (tester) async {
    backend.trainingRepository.attempts[attemptId] = testAttempt();
    backend.trainingRepository.challengeViews[challengeId] = testChallenge(
      activeAttemptId: attemptId,
    );
    await pumpApp(tester, backend: backend, at: AppRoutes.challengeDetail(challengeId));

    expect(find.byKey(const Key('challenge.startButton')), findsNothing);
    await tapKey(tester, 'challenge.continueButton');

    expect(locationOf(tester), AppRoutes.attempt(attemptId));
    expect(backend.trainingRepository.starts, isEmpty);
  });

  testWidgets('shouldOfferNewAttemptAfterAnEvaluatedOne', (tester) async {
    backend.trainingRepository.attempts[attemptId] = testAttempt(
      status: AttemptStatus.evaluated,
      selfExplanation: '설명',
    );
    backend.trainingRepository.challengeViews[challengeId] = testChallenge(
      activeAttemptId: attemptId,
    );
    await pumpApp(tester, backend: backend, at: AppRoutes.challengeDetail(challengeId));

    expect(find.byKey(const Key('challenge.resultButton')), findsOneWidget);
    await tapKey(tester, 'challenge.newButton');

    expect(backend.trainingRepository.abandons, [attemptId]);
    expect(backend.trainingRepository.starts, hasLength(1));
    expect(locationOf(tester), startsWith(AppRoutes.trainingAttempts));
  });

  testWidgets('shouldKeepStartingPossibleButWarnWhileAiIsOff', (tester) async {
    backend.meRepository.me = testMe(aiStatus: AiStatus.disabled);
    await pumpApp(tester, backend: backend, at: AppRoutes.challengeDetail(challengeId));

    expect(find.byKey(const Key('ai.unavailableBanner')), findsOneWidget);
    expect(find.byKey(const Key('challenge.aiSubmitNote')), findsOneWidget);
    expect(isButtonEnabled(tester, 'challenge.startButton'), isTrue);
  });

  testWidgets('shouldDisableStartForARetiredChallenge', (tester) async {
    backend.trainingRepository.challengeViews[challengeId] = testChallenge(
      status: ChallengeStatus.retired,
    );
    await pumpApp(tester, backend: backend, at: AppRoutes.challengeDetail(challengeId));

    expect(isButtonEnabled(tester, 'challenge.startButton'), isFalse);
    expect(find.text('더 이상 제공하지 않는 문제예요.'), findsOneWidget);
  });

  testWidgets('shouldShowNotFoundForAnUnknownChallenge', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.challengeDetail(otherChallengeId));

    expect(find.text('페이지를 찾을 수 없어요'), findsOneWidget);
  });
}

const mainTaskIdForTraining = 'e1000000-0000-4000-8000-000000000009';
