import 'dart:async';

import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/auth/token_store.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/learning_fixtures.dart';
import '../../support/lesson_fakes.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// Holds `GET /today` open until [release] is called.
final class _SlowTodayRepository extends FakeTodayRepository {
  final _gate = Completer<void>();

  void release() => _gate.complete();

  @override
  Future<TodayView> fetchToday() async {
    await _gate.future;
    return super.fetchToday();
  }
}

/// SCR-TODAY states (BL-CLI-12, docs/09 §12 "Widget: SCR-TODAY", AC-02, AC-17 S7).
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend());

  testWidgets('shouldShowSkeletonWhileTodayLoads', (tester) async {
    final slow = _SlowTodayRepository();
    await tester.pumpWidget(
      buildTestApp(
        tokenStore: MemoryTokenStore(storedToken),
        backend: FakeBackend(todayRepository: slow),
      ),
    );
    await tester.pump();
    await tester.pump();

    expect(find.byKey(const Key('common.loading')), findsOneWidget);
    expect(find.byKey(const Key('today.generateButton')), findsNothing);

    slow.release();
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('common.loading')), findsNothing);
    expect(find.byKey(const Key('today.generateButton')), findsOneWidget);
  });

  testWidgets('shouldAskMinutesAndEnergyBeforeGenerationWithDefaults', (tester) async {
    await pumpApp(tester, backend: backend);

    // The header shows the server plan-day (docs/02 SCR-TODAY, AC-17 S7).
    expect(find.text('9월 19일 (토)'), findsOneWidget);
    expect(find.text('오늘 얼마나 할 수 있나요?'), findsOneWidget);
    // Saturday uses the weekend study time (240), which has no chip of its own.
    expect(find.text('직접 입력 · 4시간'), findsOneWidget);
    final normal = tester.widget<SegmentedButton<EnergyLevel>>(
      find.byKey(const Key('today.energy')),
    );
    expect(normal.selected, {EnergyLevel.normal});

    await tapKey(tester, 'today.generateButton');
    final request = backend.todayRepository.generates.single.request;
    expect(request.toJson(), {'availableMinutes': 240, 'energyLevel': 'NORMAL', 'force': false});
  });

  testWidgets('shouldShowMainTaskReasonsRiskAndReviewRowWhenGenerated', (tester) async {
    backend.todayRepository.today = testTodayView();
    final semantics = tester.ensureSemantics();
    await pumpApp(tester, backend: backend);

    expect(find.text('Spring Transaction 내 말로 설명하기'), findsOneWidget);
    expect(find.text('설명하기'), findsOneWidget);
    expect(find.text('약 25분'), findsOneWidget);
    expect(find.text('왜 오늘?'), findsOneWidget);
    expect(find.text('기반 다지기 milestone 핵심 항목'), findsOneWidget);
    expect(find.text('실무에서 중요도가 높은 기술'), findsOneWidget);
    expect(find.text('목표 수준과 차이가 큼 (구현 1/4)'), findsOneWidget);
    expect(find.text('30분 · 보통'), findsOneWidget);
    expect(find.bySemanticsLabel('마감 위험: 보통'), findsOneWidget);
    expect(find.text('복습 3장 · 약 5분'), findsOneWidget);
    expect(find.text('다시 시작해도 괜찮아요. 오늘은 가볍게 시작해요.'), findsNothing);
    semantics.dispose();
  });

  /// 문제부터 나오지 않게 노트로 가는 길이 과제 옆에 있어야 한다 (docs/01 §4 Teach before test).
  testWidgets('shouldOfferTheLessonBesideTheTaskWhenTheSkillHasANote', (tester) async {
    backend.lessonRepository.lessonsBySkill[springTransactionRef.id] = testLesson();
    backend.todayRepository.today = testTodayView();
    await pumpApp(tester, backend: backend);

    await tapKey(tester, 'today.lessonButton');

    expect(locationOf(tester), AppRoutes.lesson(testLessonKey));
  });

  testWidgets('shouldHideTheLessonEntryWhenTheSkillHasNoNote', (tester) async {
    backend.todayRepository.today = testTodayView();
    await pumpApp(tester, backend: backend);

    expect(find.byKey(const Key('today.lessonButton')), findsNothing);
  });

  /// 마친 과제에 필요한 것은 노트가 아니라 복습이다.
  testWidgets('shouldHideTheLessonEntryOnACompletedTask', (tester) async {
    backend.lessonRepository.lessonsBySkill[springTransactionRef.id] = testLesson();
    backend.todayRepository.today = testTodayView(mainTask: testMainTask(status: TaskStatus.completed));
    await pumpApp(tester, backend: backend);

    expect(find.byKey(const Key('today.lessonButton')), findsNothing);
  });

  testWidgets('shouldShowComebackBannerAndHideMissingReviewRow', (tester) async {
    backend.todayRepository.today = testTodayView(
      comebackMode: true,
      noReviewTask: true,
      deadlineRisk: null,
    );
    await pumpApp(tester, backend: backend);

    expect(find.text('다시 시작해도 괜찮아요. 오늘은 가볍게 시작해요.'), findsOneWidget);
    expect(find.byKey(const Key('today.reviewTile')), findsNothing);
    expect(find.text('마감 위험'), findsNothing);
  });

  testWidgets('shouldOfferReplanWhenNoCandidateIsLeft', (tester) async {
    backend.todayRepository.today = testTodayView(noMainTask: true);
    await pumpApp(tester, backend: backend);

    expect(find.text('목표를 모두 달성했어요. 계획을 조정해 새 목표를 잡아 보세요.'), findsOneWidget);
    expect(find.byKey(const Key('today.reviewTile')), findsOneWidget);
    await tapKey(tester, 'today.noCandidateButton');
    expect(locationOf(tester), '/plan/replan');
  });

  testWidgets('shouldShowErrorWithRetryWhenTodayFailsToLoad', (tester) async {
    backend.todayRepository.fetchFailures.add(internalError());
    await pumpApp(tester, backend: backend);

    expect(find.text('문제가 생겼어요. 잠시 후 다시 시도해 주세요.'), findsOneWidget);
    await tapKey(tester, 'common.reportInfo');
    expect(find.text(testTraceId), findsOneWidget);

    await tapKey(tester, 'common.retryButton');
    expect(find.byKey(const Key('today.generateButton')), findsOneWidget);
  });

  testWidgets('shouldCreatePlanAndGenerateAgainWhenNoActivePlan', (tester) async {
    backend.todayRepository.planMissing = true;
    await pumpApp(tester, backend: backend);
    await tapKey(tester, 'today.minutes.30');

    await tapKey(tester, 'today.generateButton');
    expect(find.text('활성 계획이 없어요. 계획을 먼저 만들어 주세요.'), findsOneWidget);

    backend.todayRepository.planMissing = false;
    await tapKey(tester, 'today.noPlanButton');

    expect(backend.todayRepository.generates, hasLength(2));
    expect(backend.todayRepository.generates.last.request.availableMinutes, 30);
    expect(find.text('새 과제 1'), findsOneWidget);
  });

  testWidgets('shouldKeepAiBannerAwayFromTodayWhenAiIsDisabled', (tester) async {
    backend.todayRepository.today = testTodayView();
    await pumpApp(tester, backend: backend);

    // S2 runs without AI (aiStatus DISABLED); Today works and shows no AI banner (docs/02 §6.5).
    expect(find.textContaining('AI'), findsNothing);
    expect(isButtonEnabled(tester, 'today.startButton'), isTrue);
  });

  testWidgets('shouldFitTodayIn360PixelWidthWithLargeEnoughStartButton', (tester) async {
    usePhoneScreen(tester);
    addTearDown(() => resetScreenSize(tester));
    backend.todayRepository.today = testTodayView(
      earlierMainTasks: [testMainTask(id: 'e3', title: '앞서 한 과제', status: TaskStatus.completed)],
    );
    await pumpApp(tester, backend: backend);

    expect(tester.takeException(), isNull);
    final start = tester.getSize(find.byKey(const Key('today.startButton')));
    expect(start.height, greaterThanOrEqualTo(44));
    expect(find.text('오늘 앞서 한 과제 1개'), findsOneWidget);
  });

  testWidgets('shouldLinkToDashboardAndReviewSession', (tester) async {
    backend.todayRepository.today = testTodayView();
    await pumpApp(tester, backend: backend);

    await tapKey(tester, 'today.reviewButton');
    expect(locationOf(tester), '/review/session?taskId=$reviewTaskId');

    await goTo(tester, '/today');
    await tapKey(tester, 'today.dashboardLink');
    expect(locationOf(tester), '/dashboard');
  });

  testWidgets('shouldShowRiskLabelOnlyAsText', (tester) async {
    backend.todayRepository.today = testTodayView(deadlineRisk: RiskLevel.critical);
    await pumpApp(tester, backend: backend);

    expect(find.text('매우 빠듯함'), findsOneWidget);
  });

  testWidgets('shouldShowStartFailureAsToastAndKeepCard', (tester) async {
    backend.todayRepository.today = testTodayView();
    backend.todayRepository.patchFailures.add(
      const ApiException(code: ApiErrorCode.networkError),
    );
    await pumpApp(tester, backend: backend);

    await tapKey(tester, 'today.startButton');

    expect(find.text('연결이 불안정해요. 다시 시도해 주세요.'), findsOneWidget);
    expect(find.byKey(const Key('today.startButton')), findsOneWidget);
    expect(backend.sessionRepository.starts, isEmpty);
  });
}
