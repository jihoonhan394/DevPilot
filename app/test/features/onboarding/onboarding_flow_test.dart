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
    expect(find.text('무엇을, 언제까지 공부할지 정해요'), findsOneWidget);
    for (final chip in ['3개월 후', '6개월 후', '1년 후', '직접 선택']) {
      expect(find.text(chip), findsOneWidget, reason: chip);
    }
    expect(find.text('목표일까지 남은 시간으로 무엇을 먼저 할지 정해요.'), findsOneWidget);
    expect(isButtonEnabled(tester, 'onboarding.nextButton'), isFalse);
    await tapKey(tester, 'onboarding.completion.quick6m');
    expect(find.text('2027년 3월 19일'), findsOneWidget);
    expect(isButtonEnabled(tester, 'onboarding.nextButton'), isTrue);
    await tapKey(tester, 'onboarding.nextButton');
  }

  // BL-CLI-36. 트랙은 온보딩에서만 고를 수 있다 — PUT /learning-goal은 변경을 400으로 막는다
  // (docs/02 §3.4, docs/05 §5.2).
  testWidgets('shouldOfferTheThreeTracksWithHowManySkillsEachOneRequires', (tester) async {
    await pumpApp(tester, backend: backend);

    for (final role in TargetRole.known) {
      expect(
        find.byKey(Key('onboarding.goal.track.${role.name}')),
        findsOneWidget,
        reason: role.name,
      );
    }
    expect(find.text('트랙은 나중에 바꿀 수 없어요.'), findsOneWidget);
    // 필수 개수는 트랙마다 GET /skills/tree?role= 로 읽는다. testSkillTree의 MUST는 2개다.
    expect(backend.skillRepository.treeRoles, containsAll(TargetRole.known));
    expect(find.textContaining('필수 2개'), findsNWidgets(TargetRole.known.length));
  });

  testWidgets('shouldSendThePickedTrack', (tester) async {
    await pumpApp(tester, backend: backend);

    await tapKey(tester, 'onboarding.goal.track.integrationEngineer');
    await completeGoalStep(tester);
    await tapKey(tester, 'onboarding.nextButton');
    await tapKey(tester, 'onboarding.level.java.3');
    await tapKey(tester, 'onboarding.nextButton');
    await tapKey(tester, 'onboarding.submitButton');

    final request = backend.onboardingRepository.requests.single;
    expect(request.learningGoal.targetRole, TargetRole.integrationEngineer);
  });

  // 트랙이 바뀌면 3단계가 묻는 카테고리와 계획 미리보기가 달라진다 (docs/02 §3.4).
  testWidgets('shouldDropTheLevelInputsWhenTheTrackChanges', (tester) async {
    await pumpApp(tester, backend: backend);
    await completeGoalStep(tester);
    await tapKey(tester, 'onboarding.nextButton');

    await tapKey(tester, 'onboarding.level.java.1');
    expect(
      find.text('13개 분야를 아직 고르지 않았어요. 고르지 않으면 "모름"으로 시작해요.'),
      findsOneWidget,
    );

    await tapKey(tester, 'onboarding.backButton');
    await tapKey(tester, 'onboarding.backButton');
    expect(locationOf(tester), '/onboarding/goal');
    await tapKey(tester, 'onboarding.goal.track.javaBackendStarter');
    expect(find.text('트랙을 바꿔서 수준 입력을 다시 받아요.'), findsOneWidget);

    await tapKey(tester, 'onboarding.nextButton');
    await tapKey(tester, 'onboarding.nextButton');
    expect(locationOf(tester), '/onboarding/level');
    expect(
      find.text('14개 분야를 아직 고르지 않았어요. 고르지 않으면 "모름"으로 시작해요.'),
      findsOneWidget,
    );
  });

  testWidgets('shouldSubmitDiagnosticModeAndDefaultProjectWhenTheDiagnosticIsChosen', (
    tester,
  ) async {
    await pumpApp(tester, backend: backend);
    expect(locationOf(tester), '/onboarding/goal');

    await completeGoalStep(tester);
    expect(locationOf(tester), '/onboarding/time');
    await tapKey(tester, 'onboarding.weekday.60');
    expect(find.text('일주일에 약 13시간'), findsOneWidget);
    await tapKey(tester, 'onboarding.nextButton');

    expect(locationOf(tester), '/onboarding/level');
    // 자기평가가 기본이므로 진단 경로는 골라야 한다.
    await tapKey(tester, 'onboarding.level.diagnostic');
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
    expect(request.learningGoal.targetRole, TargetRole.javaBackend);
    expect(request.learningGoal.targetCompletionDate, '2027-03-19');
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
    expect(find.text('주문 시스템으로 시작할까요?'), findsOneWidget);
    await tapKey(tester, 'onboarding.skipConfirmButton');

    final request = backend.onboardingRepository.requests.single;
    expect(request.runDiagnostic, isFalse);
    expect(request.selfAssessments, hasLength(14));
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
    // ADR-050: 정하지 않아도 서버가 기본 프로젝트를 만든다
    expect(
      find.text("'주문 시스템'으로 시작해요. '사이드 프로젝트'에서 이름과 설명을 고칠 수 있어요."),
      findsOneWidget,
    );
    expect(find.byKey(const Key('onboarding.plan.diagnosticCard')), findsNothing);
    expect(find.byKey(const Key('onboarding.plan.startButton')), findsOneWidget);
  });

  testWidgets('shouldStartOnSelfAssessmentAndCountTheCategoriesStillUnset', (tester) async {
    await pumpApp(tester, backend: backend);
    await completeGoalStep(tester);
    await tapKey(tester, 'onboarding.nextButton');

    // 기본 선택이 자기평가라 칩이 바로 보인다 — 진단 카드를 누르지 않았다.
    expect(locationOf(tester), '/onboarding/level');
    expect(find.byKey(const Key('onboarding.level.java.0')), findsOneWidget);

    // 14개 모두 손대지 않은 상태
    expect(
      find.text('14개 분야를 아직 고르지 않았어요. 고르지 않으면 "모름"으로 시작해요.'),
      findsOneWidget,
    );

    await tapKey(tester, 'onboarding.level.java.1');
    expect(
      find.text('13개 분야를 아직 고르지 않았어요. 고르지 않으면 "모름"으로 시작해요.'),
      findsOneWidget,
    );

    // 경고는 막지 않는다 — 정말 모르는 분야는 0이 맞는 답이다
    await tapKey(tester, 'onboarding.nextButton');
    expect(locationOf(tester), '/onboarding/project');
    await tapKey(tester, 'onboarding.submitButton');

    final request = backend.onboardingRepository.requests.single;
    expect(request.runDiagnostic, isFalse);
    expect(request.selfAssessments, hasLength(14));
    expect(
      request.selfAssessments.firstWhere((input) => input.category == SkillCategory.java).level,
      1,
    );
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
