import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';

/// SCR-TODAY 목표 가이드 (docs/02 SCR-TODAY, ADR-062).
///
/// 첫 화면에 "목표일 못 지킴" 빨간 배지가 뜨면 시작할 마음이 사라진다는 2026-10-01 지적에서 나왔다. 그 자리가 할 말은
/// 판정이 아니라 **방향·지금 닿는 곳·언제면 되는지** 셋이다.
void main() {
  late FakeBackend backend;

  setUp(() {
    backend = FakeBackend();
    // 가이드는 계획을 만든 뒤 화면에만 있다 — 생성 전 패널에는 아직 범위가 없다
    backend.todayRepository.today = testTodayView(mainTask: testMainTask());
  });

  /// 목표일을 넘기는 범위 — 그래도 빨간 판정이 아니라 날짜와 두 가지 선택을 준다.
  testWidgets('shouldAnswerWithADateInsteadOfAVerdictWhenTheScopeIsTooBig', (tester) async {
    await pumpApp(tester, backend: backend);

    expect(find.byKey(const Key('today.goalGuide')), findsOneWidget);
    expect(find.textContaining('방향은 맞아요'), findsOneWidget);
    // 지금 근거만으로 닿는 가장 먼 단계
    expect(find.textContaining('기반 다지기'), findsWidgets);
    expect(find.textContaining('2027년 7월'), findsOneWidget);
    expect(find.byKey(const Key('today.guideShrink')), findsOneWidget);
    expect(find.byKey(const Key('today.guideChangeDate')), findsOneWidget);
  });

  /// 꾸준함이 날짜를 당긴다는 말은 격려가 아니라 계산이다 (docs/06 §3.3).
  testWidgets('shouldSayThatKeepingAtItMovesTheDateCloser', (tester) async {
    await pumpApp(tester, backend: backend);

    expect(find.byKey(const Key('today.guidePull')), findsOneWidget);
    expect(find.textContaining('당겨져요'), findsOneWidget);
  });

  /// 아직 쌓을 방법이 없는 축의 몫은 위험도 밖이지만 사라지지 않는다 (ADR-061).
  testWidgets('shouldShowTheHoursChargedToAxesThatCannotBeEarnedYet', (tester) async {
    await pumpApp(tester, backend: backend);

    // 2400분 → 40시간
    expect(find.textContaining('약 40시간'), findsOneWidget);
  });

  /// 목표일 안에 들어오면 날짜 이야기를 꺼내지 않고 고를 것도 주지 않는다.
  testWidgets('shouldSayNothingAboutDatesWhenTheScopeFitsTheGoal', (tester) async {
    backend.planRepository.budget = testBudget().copyWith(
      feasibleCompletionDate: '2027-01-05',
    );
    await pumpApp(tester, backend: backend);

    expect(find.textContaining('목표일 안에 들어와요'), findsOneWidget);
    expect(find.byKey(const Key('today.guidePull')), findsNothing);
    expect(find.byKey(const Key('today.guideShrink')), findsNothing);
  });

  /// 어떤 날짜로도 닿지 않으면 날짜가 아니라 범위를 줄이라고 말한다 (docs/06 §3.4).
  testWidgets('shouldAskToShrinkTheScopeWhenNoDateCarriesIt', (tester) async {
    backend.planRepository.budget = testBudget().copyWith(feasibleCompletionDate: null);
    await pumpApp(tester, backend: backend);

    expect(find.textContaining('날짜를 늘려서 되는 게 아니에요'), findsOneWidget);
    expect(find.byKey(const Key('today.guideShrink')), findsOneWidget);
  });

  /// 날짜가 자기평가 위에 서 있으면 그렇다고 말한다. 반대 방향 거짓말도 거짓말이다.
  testWidgets('shouldAdmitTheDateRestsOnTheSelfAssessmentUntilThereIsHistory', (tester) async {
    backend.planRepository.budget = testBudget().copyWith(completionRateEstimated: true);
    await pumpApp(tester, backend: backend);

    expect(find.byKey(const Key('today.guideEstimated')), findsOneWidget);
    expect(find.textContaining('자기평가 기준이에요'), findsOneWidget);
  });

  /// 기록이 쌓이면 그 말은 사라진다.
  testWidgets('shouldDropTheCaveatOnceThereIsRealHistory', (tester) async {
    await pumpApp(tester, backend: backend);

    expect(find.byKey(const Key('today.guideEstimated')), findsNothing);
  });

  /// 버튼을 누를지 정하는 자리가 "아무것도 안 하는 것보다"가 걸리는 순간이다 (ADR-062).
  testWidgets('shouldGuideBeforeTheDayIsEvenGenerated', (tester) async {
    backend.todayRepository.today = null;
    await pumpApp(tester, backend: backend);

    expect(find.byKey(const Key('today.generateButton')), findsOneWidget);
    expect(find.byKey(const Key('today.goalGuide')), findsOneWidget);
    expect(find.textContaining('방향은 맞아요'), findsOneWidget);
  });

  /// 예산을 못 읽으면 아무것도 그리지 않는다 — 계획 없는 사람에게 오류를 보여 줄 자리가 아니다.
  testWidgets('shouldStayHiddenWhenTheBudgetCannotBeRead', (tester) async {
    backend.planRepository.budgetFailures.add(
      const ApiException(code: ApiErrorCode.planNotFound, status: 404),
    );
    await pumpApp(tester, backend: backend);

    expect(find.byKey(const Key('today.goalGuide')), findsNothing);
  });

  /// 첫 화면에서 마감 위험 배지는 사라졌다 (ADR-062).
  testWidgets('shouldNotPaintADeadlineVerdictOnTheFirstScreen', (tester) async {
    await pumpApp(tester, backend: backend);

    expect(find.text('마감 위험'), findsNothing);
  });
}
