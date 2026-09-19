import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-ONBOARDING (BL-CLI-07, AC-11, AC-27 S6).
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend(me: testMe(onboardingCompleted: false)));

  Future<void> completeGoalStep(WidgetTester tester) async {
    expect(find.text('1 / 5'), findsOneWidget);
    expect(isButtonEnabled(tester, 'onboarding.nextButton'), isFalse);
    await tapKey(tester, 'onboarding.profile.workingDeveloper');
    await tapKey(tester, 'onboarding.completion.quick6m');
    expect(find.text('2027년 3월 19일'), findsOneWidget);
    expect(isButtonEnabled(tester, 'onboarding.nextButton'), isTrue);
    await tapKey(tester, 'onboarding.nextButton');
  }

  testWidgets('shouldSubmitDiagnosticModeAndDefaultProjectWhenDefaultsAreKept', (tester) async {
    await pumpApp(tester, backend: backend);
    expect(locationOf(tester), '/onboarding/goal');

    await completeGoalStep(tester);
    expect(locationOf(tester), '/onboarding/time');
    await tapKey(tester, 'onboarding.weekday.60');
    expect(find.text('일주일에 약 13시간'), findsOneWidget);
    await tapKey(tester, 'onboarding.nextButton');

    expect(locationOf(tester), '/onboarding/level');
    await tapKey(tester, 'onboarding.nextButton');

    expect(locationOf(tester), '/onboarding/project');
    expect(find.widgetWithText(TextField, '주문 시스템'), findsOneWidget);
    await tapKey(tester, 'onboarding.submitButton');

    final request = backend.onboardingRepository.requests.single;
    expect(request.displayName, 'MT');
    expect(request.timezone, 'Asia/Seoul');
    expect(request.dayStartHour, 4);
    expect(request.weekdayStudyMinutes, 60);
    expect(request.weekendStudyMinutes, 240);
    expect(request.experienceProfile, ExperienceProfile.workingDeveloper);
    expect(request.learningGoal.targetRole, TargetRole.javaBackend);
    expect(request.learningGoal.targetCompletionDate, '2027-03-19');
    expect(request.learningGoal.checkpointDate, isNull);
    expect(request.runDiagnostic, isTrue);
    expect(request.selfAssessments, isEmpty);
    expect(request.sideProject?.name, '주문 시스템');
    expect(request.sideProject?.description, '회원가입 · 상품 · 주문 · 취소까지 직접 만드는 학습용 백엔드');
    expect(request.sideProject?.repoUrl, isNull);
    expect(request.useTemplate, isTrue);
    expect(request.toJson()['sideProject'], containsPair('stack', null));

    expect(locationOf(tester), '/onboarding/plan');
    expect(find.text('계획을 만들었어요'), findsOneWidget);
    expect(find.text('Java 백엔드 성장 계획 · v1'), findsOneWidget);
    expect(find.text('사이드 프로젝트: 주문 시스템'), findsOneWidget);
    expect(find.text('지금 풀 수 있는 진단 문제가 없어요. 모든 분야를 처음부터 시작해요.'), findsOneWidget);

    await tapKey(tester, 'onboarding.plan.startButton');
    expect(locationOf(tester), '/today');
  });

  testWidgets('shouldSendThirteenSelfAssessmentsAndNoProjectWhenDiagnosticAndProjectAreSkipped', (
    tester,
  ) async {
    await pumpApp(tester, backend: backend);
    await completeGoalStep(tester);
    await tapKey(tester, 'onboarding.nextButton');

    await tapKey(tester, 'onboarding.level.self');
    await tapKey(tester, 'onboarding.level.java.3');
    await tapKey(tester, 'onboarding.level.spring.2');
    expect(find.byKey(const Key('onboarding.level.diagnosticNote')), findsOneWidget);
    await tapKey(tester, 'onboarding.nextButton');

    await tapKey(tester, 'onboarding.skipButton');
    expect(find.text('프로젝트 없이 시작할까요?'), findsOneWidget);
    await tapKey(tester, 'onboarding.skipConfirmButton');

    final request = backend.onboardingRepository.requests.single;
    expect(request.runDiagnostic, isFalse);
    expect(request.selfAssessments, hasLength(13));
    expect(
      {for (final input in request.selfAssessments) input.category: input.level},
      containsPair(SkillCategory.java, 3),
    );
    expect(
      request.selfAssessments.firstWhere((input) => input.category == SkillCategory.spring).level,
      2,
    );
    expect(
      request.selfAssessments
          .firstWhere((input) => input.category == SkillCategory.explanation)
          .level,
      0,
    );
    expect(request.sideProject, isNull);
    expect(request.toJson(), containsPair('sideProject', null));

    expect(locationOf(tester), '/onboarding/plan');
    expect(find.text('사이드 프로젝트 없이 시작해요. 프로젝트 과제는 제안되지 않아요.'), findsOneWidget);
    expect(find.byKey(const Key('onboarding.plan.diagnosticCard')), findsNothing);
    expect(find.byKey(const Key('onboarding.plan.startButton')), findsOneWidget);
  });

  testWidgets('shouldKeepFocusSkillsPickedOnLevelStep', (tester) async {
    await pumpApp(tester, backend: backend);
    await completeGoalStep(tester);
    await tapKey(tester, 'onboarding.nextButton');

    await tapKey(tester, 'onboarding.focusToggle');
    await tapKey(tester, 'onboarding.focusPickButton');
    await tapKey(tester, 'skillPicker.option.SPRING.TRANSACTION');
    await tapKey(tester, 'skillPicker.doneButton');
    expect(find.widgetWithText(InputChip, 'Spring Transaction'), findsOneWidget);

    await tapKey(tester, 'onboarding.nextButton');
    await tapKey(tester, 'onboarding.submitButton');

    expect(backend.onboardingRepository.requests.single.learningGoal.focusSkillCodes, [
      'SPRING.TRANSACTION',
    ]);
  });

  testWidgets('shouldReturnToGoalStepWithServerMessageWhenGoalFieldIsRejected', (tester) async {
    backend.onboardingRepository.failures.add(
      const ApiException(
        code: ApiErrorCode.validationFailed,
        status: 400,
        fieldErrors: [
          ApiFieldError(
            field: 'learningGoal.targetCompletionDate',
            code: 'DATE_OUT_OF_RANGE',
            message: '허용 범위를 벗어난 날짜입니다.',
          ),
        ],
      ),
    );
    await pumpApp(tester, backend: backend);
    await completeGoalStep(tester);
    await tapKey(tester, 'onboarding.nextButton');
    await tapKey(tester, 'onboarding.nextButton');
    await tapKey(tester, 'onboarding.submitButton');

    expect(locationOf(tester), '/onboarding/goal');
    expect(find.text('허용 범위를 벗어난 날짜입니다.'), findsOneWidget);
  });

  testWidgets('shouldShowSecretBlockedUnderProjectName', (tester) async {
    backend.onboardingRepository.failures.add(
      const ApiException(code: ApiErrorCode.secretDetectedBlocked, status: 422),
    );
    await pumpApp(tester, backend: backend);
    await completeGoalStep(tester);
    await tapKey(tester, 'onboarding.nextButton');
    await tapKey(tester, 'onboarding.nextButton');
    await tapKey(tester, 'onboarding.submitButton');

    expect(locationOf(tester), '/onboarding/project');
    expect(
      find.text('개인 키(private key)가 포함되어 있어 보낼 수 없어요. 해당 부분을 지워 주세요.'),
      findsOneWidget,
    );
  });

  testWidgets('shouldResendWithSameIdempotencyKeyAfterNetworkFailure', (tester) async {
    backend.onboardingRepository.failures.add(
      const ApiException(code: ApiErrorCode.networkError),
    );
    await pumpApp(tester, backend: backend);
    await completeGoalStep(tester);
    await tapKey(tester, 'onboarding.nextButton');
    await tapKey(tester, 'onboarding.nextButton');

    await tapKey(tester, 'onboarding.submitButton');
    expect(find.text('연결이 불안정해요. 다시 시도해 주세요.'), findsOneWidget);
    expect(locationOf(tester), '/onboarding/project');
    // The toast covers the bottom buttons for 4 seconds.
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();

    await tapKey(tester, 'onboarding.submitButton');

    final keys = backend.onboardingRepository.keys;
    expect(keys, hasLength(2));
    expect(keys[1], keys[0]);
    expect(locationOf(tester), '/onboarding/plan');
  });

  testWidgets('shouldGoToStartPageWhenAnotherTabFinishedOnboarding', (tester) async {
    backend.onboardingRepository.failures.add(
      const ApiException(code: ApiErrorCode.onboardingAlreadyCompleted, status: 409),
    );
    await pumpApp(tester, backend: backend);
    await completeGoalStep(tester);
    await tapKey(tester, 'onboarding.nextButton');
    await tapKey(tester, 'onboarding.nextButton');
    backend.meRepository.me = testMe();

    await tapKey(tester, 'onboarding.submitButton');

    expect(locationOf(tester), '/today');
    expect(find.text('이미 시작 설정을 마쳤어요.'), findsOneWidget);
  });

  testWidgets('shouldFitStepsIn360PixelWidth', (tester) async {
    usePhoneScreen(tester);
    addTearDown(() => resetScreenSize(tester));
    await pumpApp(tester, backend: backend);

    await completeGoalStep(tester);
    await tapKey(tester, 'onboarding.nextButton');
    await tapKey(tester, 'onboarding.level.self');
    await tapKey(tester, 'onboarding.nextButton');

    expect(locationOf(tester), '/onboarding/project');
    expect(tester.takeException(), isNull);
  });
}
