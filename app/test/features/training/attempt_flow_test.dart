import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:devpilot_app/core/widgets/ai_provider_notice.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/training/data/attempt_models.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/test_app.dart';
import '../../support/training_fixtures.dart';
import '../../support/widget_actions.dart';

/// SCR-TRAINING-ATTEMPT: self-explanation, Hint Ladder, submission, evaluation poll and result
/// (BL-CLI-21, docs/02 §4.5, AC-04, AC-16).
void main() {
  late FakeBackend backend;
  late KeyValueStore store;

  setUp(() {
    backend = FakeBackend(me: testMe(aiStatus: AiStatus.enabled));
    backend.trainingRepository.attempts[attemptId] = testAttempt();
    backend.trainingRepository.challengeViews[challengeId] = testChallenge(
      activeAttemptId: attemptId,
    );
    store = MemoryKeyValueStore({aiProviderNoticeStorageKey: 'true'});
  });

  Future<void> openAttempt(WidgetTester tester, {String? taskId}) => pumpApp(
    tester,
    backend: backend,
    keyValueStore: store,
    at: AppRoutes.attempt(attemptId, taskId: taskId),
  );

  Future<void> explain(WidgetTester tester) async {
    await enterTextByKey(tester, 'attempt.explanationField', 'IOException을 도메인 예외로 바꿔 던진다.');
    await tapKey(tester, 'attempt.explanationSaveButton');
  }

  testWidgets('shouldLockHintsAndSubmissionUntilTheSelfExplanation', (tester) async {
    await openAttempt(tester);

    expect(find.text('설명 후에 열려요'), findsNWidgets(2));
    expect(find.byKey(const Key('attempt.hintButton')), findsNothing);
    await explain(tester);

    expect(backend.trainingRepository.explanations.single.toJson(), {
      'text': 'IOException을 도메인 예외로 바꿔 던진다.',
      'skipped': false,
    });
    expect(find.text('1 질문 보기'), findsOneWidget);
    expect(find.byKey(const Key('attempt.submissionForm')), findsOneWidget);
  });

  testWidgets('shouldSkipTheExplanationAfterConfirming', (tester) async {
    await openAttempt(tester);

    await tapKey(tester, 'attempt.explanationSkipButton');
    await tapKey(tester, 'attempt.skipConfirmButton');

    expect(backend.trainingRepository.explanations.single.toJson(), {
      'text': null,
      'skipped': true,
    });
    expect(find.byKey(const Key('attempt.explanationDone')), findsOneWidget);
  });

  testWidgets('shouldClimbTheLadderOneRungAtATime', (tester) async {
    await openAttempt(tester);
    await explain(tester);

    await tapKey(tester, 'attempt.hintButton');
    expect(backend.trainingRepository.hints.single.request.toJson(), {
      'requestedLevel': 'QUESTION_ONLY',
      'acknowledgeEvidenceImpact': false,
      'giveUp': false,
    });
    expect(find.text('1단계 힌트 내용'), findsOneWidget);
    expect(find.text('2 개념 힌트 보기'), findsOneWidget);
    expect(find.text("맞히면 '스스로 해결'로 인정돼요"), findsOneWidget);

    await tapKey(tester, 'attempt.hintButton');
    expect(find.text('지금까지: 개념 힌트'), findsOneWidget);
    expect(find.text("맞히면 '힌트로 해결'로 기록돼요"), findsOneWidget);
  });

  testWidgets('shouldConfirmBeforeThePseudocodeHint', (tester) async {
    backend.trainingRepository.attempts[attemptId] = testAttempt(
      selfExplanation: '설명',
      maxHintLevel: HintLevel.direction,
    );
    await openAttempt(tester);
    expect(find.text('4 의사코드 보기'), findsOneWidget);

    await tapKey(tester, 'attempt.hintButton');
    expect(find.text('의사코드 힌트를 볼까요?'), findsOneWidget);
    await tapKey(tester, 'common.dialogCancelButton');
    expect(backend.trainingRepository.hints, isEmpty);

    await tapKey(tester, 'attempt.hintButton');
    await tapKey(tester, 'attempt.hintConfirmButton');
    expect(backend.trainingRepository.hints.single.request.acknowledgeEvidenceImpact, isTrue);
    expect(find.bySemanticsLabel('AI가 만든 내용'), findsOneWidget);
    expect(find.text("'힌트로 해결'로 기록되고 복습 카드로 다시 나와요"), findsOneWidget);
  });

  testWidgets('shouldAskToGiveUpForTheFullExampleBeforeAnySubmission', (tester) async {
    backend.trainingRepository.attempts[attemptId] = testAttempt(
      selfExplanation: '설명',
      maxHintLevel: HintLevel.partialCode,
    );
    await openAttempt(tester);

    await tapKey(tester, 'attempt.hintButton');
    expect(find.text('포기하고 전체 예시를 볼까요?'), findsOneWidget);
    await tapKey(tester, 'attempt.hintConfirmButton');

    final request = backend.trainingRepository.hints.single.request;
    expect(request.giveUp, isTrue);
    expect(request.acknowledgeEvidenceImpact, isTrue);
  });

  testWidgets('shouldBlockAiRungsButNotSeedRungsWhileAiIsOff', (tester) async {
    backend.meRepository.me = testMe(aiStatus: AiStatus.balanceExhausted);
    backend.trainingRepository.attempts[attemptId] = testAttempt(
      selfExplanation: '설명',
      maxHintLevel: HintLevel.direction,
    );
    await openAttempt(tester);

    expect(isButtonEnabled(tester, 'attempt.hintButton'), isFalse);
    expect(find.text('AI를 쓸 수 없어 이 단계는 잠시 막혀 있어요.'), findsOneWidget);
    expect(isButtonEnabled(tester, 'attempt.submitButton'), isFalse);
    expect(find.byKey(const Key('attempt.aiSubmitNote')), findsOneWidget);
  });

  testWidgets('shouldShowAiRefusedUnderTheLadder', (tester) async {
    backend.trainingRepository.attempts[attemptId] = testAttempt(
      selfExplanation: '설명',
      maxHintLevel: HintLevel.direction,
    );
    backend.trainingRepository.hintFailures.add(
      const ApiException(code: ApiErrorCode.aiRefused, status: 502),
    );
    await openAttempt(tester);

    await tapKey(tester, 'attempt.hintButton');
    await tapKey(tester, 'attempt.hintConfirmButton');

    expect(find.text('이 요청은 AI가 처리할 수 없어요. 내용을 바꿔 다시 시도해 주세요.'), findsOneWidget);
  });

  testWidgets('shouldSubmitPollAndShowTheRubricResult', (tester) async {
    backend.trainingRepository.attempts[attemptId] = testAttempt(selfExplanation: '설명');
    await openAttempt(tester, taskId: 'e1000000-0000-4000-8000-000000000001');

    await enterTextByKey(tester, 'attempt.answerField', '원인 예외를 보존했다.');
    await tapKeyWithoutSettling(tester, 'attempt.submitButton');
    expect(backend.trainingRepository.submissions.single.request.toJson(), {
      'answerText': '원인 예외를 보존했다.',
      'code': null,
      'language': null,
    });
    expect(find.text('AI가 답을 평가하고 있어요. 보통 1분 안팎 걸려요.'), findsOneWidget);

    backend.trainingRepository.completeEvaluation(
      attemptId,
      reviewScheduled: const [
        ScheduledReviewView(reviewItemId: 'r9', skillCode: 'JAVA.EXCEPTION', dueDate: '2026-09-20'),
      ],
    );
    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();

    expect(find.text('제출 1 결과'), findsOneWidget);
    expect(find.text('핵심 기준 3개 중 2개 충족'), findsOneWidget);
    expect(find.text('미충족'), findsOneWidget);
    expect(find.bySemanticsLabel('평가: 일부 충족'), findsOneWidget);
    expect(find.text('이 문제는 9월 20일 (일)에 복습 카드로 나와요.'), findsOneWidget);
    expect(find.text('수정해서 다시 제출 (2/5)'), findsOneWidget);

    await tapKey(tester, 'attempt.toTodayButton');
    expect(locationOf(tester), AppRoutes.todayComplete('e1000000-0000-4000-8000-000000000001'));
  });

  testWidgets('shouldRetryAFailedEvaluation', (tester) async {
    backend.trainingRepository.attempts[attemptId] = testAttempt(
      selfExplanation: '설명',
      status: AttemptStatus.submitted,
      submissions: [
        testSubmission(
          status: AsyncJobStatus.failed,
          failureCode: AsyncFailureCode.aiTimeout,
          retryable: true,
        ),
      ],
    );
    await openAttempt(tester);

    expect(find.text('AI 응답이 늦어 완료하지 못했어요.'), findsOneWidget);
    expect(find.byKey(const Key('attempt.submissionForm')), findsNothing);
    await tapKeyWithoutSettling(tester, 'attempt.retryEvaluationButton');

    expect(backend.trainingRepository.retries, [1]);
    expect(find.text('AI가 답을 평가하고 있어요. 보통 1분 안팎 걸려요.'), findsOneWidget);
    backend.trainingRepository.completeEvaluation(attemptId);
    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();
    expect(find.text('제출 1 결과'), findsOneWidget);
  });

  testWidgets('shouldHideRetryForARefusedEvaluation', (tester) async {
    backend.trainingRepository.attempts[attemptId] = testAttempt(
      selfExplanation: '설명',
      status: AttemptStatus.submitted,
      submissions: [
        testSubmission(status: AsyncJobStatus.failed, failureCode: AsyncFailureCode.aiRefused),
      ],
    );
    await openAttempt(tester);

    expect(find.text('AI가 이 내용을 처리하지 않았어요. 내용을 바꿔 새로 요청해 주세요.'), findsOneWidget);
    expect(find.byKey(const Key('attempt.retryEvaluationButton')), findsNothing);
  });

  testWidgets('shouldAbandonAndGoBackToTheList', (tester) async {
    await openAttempt(tester);

    await tapKey(tester, 'attempt.menuButton');
    await tapKey(tester, 'attempt.abandonMenuItem');
    await tapKey(tester, 'attempt.abandonConfirmButton');

    expect(backend.trainingRepository.abandons, [attemptId]);
    expect(locationOf(tester), AppRoutes.training);
  });

  testWidgets('shouldKeepTheAnswerDraftPerAttempt', (tester) async {
    backend.trainingRepository.attempts[attemptId] = testAttempt(selfExplanation: '설명');
    await openAttempt(tester);

    await enterTextByKey(tester, 'attempt.answerField', '초안 답');
    await tester.pump(const Duration(seconds: 1));

    expect(store.read('devpilot.attempt.draft.$attemptId'), contains('초안 답'));
  });

  testWidgets('shouldReportADiagnosticPass', (tester) async {
    backend.trainingRepository.attempts[attemptId] = testAttempt(
      selfExplanation: '설명',
      purpose: ChallengePurpose.diagnostic,
      status: AttemptStatus.evaluated,
      evaluatedOutcome: EvaluatedOutcome.correct,
      outcome: AttemptOutcome.solvedIndependently,
      submissions: [
        testSubmission(
          status: AsyncJobStatus.completed,
          evaluation: testEvaluation(outcome: EvaluatedOutcome.correct),
        ),
      ],
    );
    await openAttempt(tester);

    expect(find.text('확인됐어요. 이 분야는 기초 과제를 건너뛰어요.'), findsOneWidget);
    await tapKey(tester, 'attempt.toDiagnosticsButton');
    expect(locationOf(tester), AppRoutes.diagnostics);
  });
}
