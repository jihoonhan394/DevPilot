import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/features/lesson/data/lesson_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/lesson_fakes.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-LESSON (docs/02, AC-37 S6). 가르치는 화면이 한 걸음씩 가는지 본다.
///
/// 여기서 지키는 것: 예제를 열기 전에는 예제가 화면에 없고, 힌트는 하나씩 열리고, 모범 답안은 힌트를 다 연 뒤에야 열리고,
/// 마칠 때 보내는 도움 단계가 실제로 연 것과 같다.
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend());

  Future<void> openLesson(WidgetTester tester) async {
    await pumpApp(tester, backend: backend, at: '/lessons/$testLessonKey');
  }

  /// ADR-047: 설명에서 막혔을 때의 출구. 누를 때만 부른다.
  testWidgets('shouldReexplainOnlyWhenTheLearnerAsks', (tester) async {
    backend.meRepository.me = testMe(aiStatus: AiStatus.enabled);
    await openLesson(tester);
    await tapKey(tester, 'lesson.startButton');

    expect(find.byKey(const Key('lesson.reexplainButton')), findsOneWidget);
    expect(backend.lessonRepository.reexplained, isEmpty);

    await tapKey(tester, 'lesson.reexplainButton');
    await tapKey(tester, 'lesson.reexplainReason.whyNotClear');

    expect(backend.lessonRepository.reexplained.single.reason, ConfusionReason.whyNotClear);
    expect(find.byKey(const Key('lesson.reexplainTitle')), findsOneWidget);
    expect(find.byKey(const Key('lesson.reexplainText')), findsOneWidget);
    // 매번 달라진다는 것을 알려 본문을 대체하지 않게 한다.
    expect(find.textContaining('노트 본문이 기준'), findsOneWidget);

    await tapKey(tester, 'lesson.reexplainClose');
    expect(find.byKey(const Key('lesson.reexplainTitle')), findsNothing);
  });

  /// 비유가 없으면 그 자리를 비운다 — 억지 비유를 만들지 않기로 했다(ADR-047).
  testWidgets('shouldShowNoAnalogySectionWhenThereIsNone', (tester) async {
    backend.meRepository.me = testMe(aiStatus: AiStatus.enabled);
    backend.lessonRepository.reexplainResult = const ReexplainResult(
      explanation: '조건이 없으면 무엇이 어긋나는지부터 보면 됩니다.',
    );
    await openLesson(tester);
    await tapKey(tester, 'lesson.startButton');
    await tapKey(tester, 'lesson.reexplainButton');
    await tapKey(tester, 'lesson.reexplainReason.unfamiliarTerms');

    expect(find.byKey(const Key('lesson.reexplainText')), findsOneWidget);
    expect(find.text('비유로 보면'), findsNothing);
  });

  /// AI가 꺼져 있으면 눌러 봐야 부를 수 없다 (docs/02 §6.2).
  testWidgets('shouldHideReexplainWhileAiIsOff', (tester) async {
    backend.meRepository.me = testMe(aiStatus: AiStatus.disabled);
    await openLesson(tester);
    await tapKey(tester, 'lesson.startButton');

    expect(find.byKey(const Key('lesson.reexplainButton')), findsNothing);
  });

  testWidgets('shouldStartWithWhyItMattersAndHideTheExample', (tester) async {
    await openLesson(tester);

    expect(find.byKey(const Key('lesson.whyItMatters')), findsOneWidget);
    expect(find.text('주소 하나를 메서드에 잇기 1'), findsWidgets);
    // 예제는 두 걸음 뒤다. 미리 보이면 답을 먼저 보게 된다.
    expect(find.textContaining('@RestController'), findsNothing);
  });

  testWidgets('shouldWalkOneStepAtATime', (tester) async {
    await openLesson(tester);

    await tapKey(tester, 'lesson.startButton');
    expect(find.byKey(const Key('lesson.explain')), findsOneWidget);
    expect(find.textContaining('@RestController'), findsNothing);

    await tapKey(tester, 'lesson.nextButton');
    expect(find.textContaining('@RestController'), findsOneWidget);

    await tapKey(tester, 'lesson.nextButton');
    expect(find.byKey(const Key('lesson.predictQuestion')), findsOneWidget);
  });

  testWidgets('shouldSkipStraightToTheProblemWhenTheLearnerAlreadyKnows', (tester) async {
    await openLesson(tester);

    await tapKey(tester, 'lesson.startButton');
    await tapKey(tester, 'lesson.skipButton');

    expect(find.byKey(const Key('lesson.problemPrompt')), findsOneWidget);
    expect(find.byKey(const Key('lesson.answerField')), findsOneWidget);
  });

  // ADR-057. 문턱이 3인 트랙은 이미 아는 분야에도 노트를 내므로, 아는 단위는 걸음을 다 거치지 않고 지나간다.
  // helpLevel 은 NONE 이고 견주지 않았으므로 selfChecksMet 은 보내지 않는다.
  testWidgets('shouldFinishTheUnitWithoutWalkingItWhenTheLearnerAlreadyKnowsIt', (tester) async {
    await openLesson(tester);

    await tapKey(tester, 'lesson.startButton');
    await tapKey(tester, 'lesson.alreadyKnowButton');

    final recorded = backend.lessonRepository.finished.single;
    expect(recorded.helpLevel, HelpLevel.none);
    expect(recorded.selfChecksMet, isNull);
    expect(find.byKey(const Key('lesson.nextUnitButton')), findsOneWidget);
  });

  testWidgets('shouldOfferAlreadyKnowOnTheExplainAndExampleStepsOnly', (tester) async {
    await openLesson(tester);

    await tapKey(tester, 'lesson.startButton');
    expect(find.byKey(const Key('lesson.alreadyKnowButton')), findsOneWidget);
    await tapKey(tester, 'lesson.nextButton');
    expect(find.byKey(const Key('lesson.alreadyKnowButton')), findsOneWidget);

    // 예측 걸음부터는 이미 걷기 시작했으므로 건너뛰기를 내밀지 않는다
    await tapKey(tester, 'lesson.nextButton');
    expect(find.byKey(const Key('lesson.predictQuestion')), findsOneWidget);
    expect(find.byKey(const Key('lesson.alreadyKnowButton')), findsNothing);
  });

  testWidgets('shouldGradeThePredictionImmediately', (tester) async {
    backend.lessonRepository.predictCorrect = false;
    await openLesson(tester);

    await tapKey(tester, 'lesson.startButton');
    await tapKey(tester, 'lesson.nextButton');
    await tapKey(tester, 'lesson.nextButton');
    await tapKey(tester, 'lesson.choice.0');
    await tapKey(tester, 'lesson.checkButton');

    expect(backend.lessonRepository.predicted, ['400']);
    expect(find.byKey(const Key('lesson.verdict')), findsOneWidget);
    expect(find.text('다시 보면 좋아요'), findsOneWidget);
    expect(find.textContaining('정답: 400'), findsOneWidget);
  });

  testWidgets('shouldOpenHintsOneByOneBeforeTheModelAnswer', (tester) async {
    await openLesson(tester);

    await tapKey(tester, 'lesson.startButton');
    await tapKey(tester, 'lesson.skipButton');

    expect(find.byKey(const Key('lesson.hint.0')), findsNothing);
    expect(find.byKey(const Key('lesson.revealButton')), findsNothing);

    await tapKey(tester, 'lesson.hintButton');
    expect(find.byKey(const Key('lesson.hint.0')), findsOneWidget);
    expect(find.byKey(const Key('lesson.hint.1')), findsNothing);
    expect(find.byKey(const Key('lesson.revealButton')), findsNothing);

    await tapKey(tester, 'lesson.hintButton');
    expect(find.byKey(const Key('lesson.hint.1')), findsOneWidget);
    expect(find.byKey(const Key('lesson.revealButton')), findsOneWidget);
  });

  testWidgets('shouldSendTheHelpLevelItActuallyCounted', (tester) async {
    await openLesson(tester);

    await tapKey(tester, 'lesson.startButton');
    await tapKey(tester, 'lesson.skipButton');
    await tapKey(tester, 'lesson.hintButton');
    await tapKey(tester, 'lesson.hintButton');
    await tapKey(tester, 'lesson.revealButton');

    expect(find.byKey(const Key('lesson.modelAnswer')), findsOneWidget);
    await tapKey(tester, 'lesson.selfCheck.0');
    await tapKey(tester, 'lesson.finishButton');

    expect(backend.lessonRepository.finished, hasLength(1));
    final recorded = backend.lessonRepository.finished.single;
    expect(recorded.unitKey, testUnitKey(1));
    expect(recorded.helpLevel, HelpLevel.hint);
    expect(recorded.selfChecksMet, 1);
  });

  testWidgets('shouldCountAnswerFirstAsTheHighestHelpLevel', (tester) async {
    backend.lessonRepository.lesson = testLesson(
      units: [
        testUnit(1).copyWith(
          problem: const LessonProblemView(
            prompt: 'GET /orders/health에 ok만 돌려주는 컨트롤러를 작성하세요.',
            deliverables: ['코드'],
            hints: [],
          ),
        ),
      ],
    );
    await openLesson(tester);

    await tapKey(tester, 'lesson.startButton');
    await tapKey(tester, 'lesson.skipButton');
    await tapKey(tester, 'lesson.revealButton');
    await tapKey(tester, 'lesson.finishButton');

    expect(backend.lessonRepository.finished.single.helpLevel, HelpLevel.answer);
  });

  testWidgets('shouldOfferTheNextUnitAfterFinishing', (tester) async {
    await openLesson(tester);

    await tapKey(tester, 'lesson.startButton');
    await tapKey(tester, 'lesson.skipButton');
    await tapKey(tester, 'lesson.hintButton');
    await tapKey(tester, 'lesson.hintButton');
    await tapKey(tester, 'lesson.revealButton');
    await tapKey(tester, 'lesson.finishButton');

    expect(find.byKey(const Key('lesson.inProject')), findsOneWidget);
    expect(find.byKey(const Key('lesson.nextUnitButton')), findsOneWidget);
  });

  /// ADR-069: "이걸 외워야 하나"의 답은 계획에 있다 — 우선순위와 지식 축 목표. 노트가 그걸 보여 준다.
  testWidgets('shouldShowHowFarThisSkillHasToGo', (tester) async {
    backend.lessonRepository.lesson = testLesson(
      requirement: const LessonRequirementView(
        priority: Priority.must,
        targets: AxisLevels(knowledge: 3, implementation: 3, explanation: 2, debugging: 0),
        planningLevels: AxisLevels.zero,
      ),
    );
    await openLesson(tester);

    expect(find.byKey(const Key('lesson.requirement')), findsOneWidget);
    expect(find.textContaining('필수'), findsWidgets);
    expect(find.textContaining('혼자 기본 가능'), findsWidgets);
  });

  /// 목표에 닿았으면 "지금은 모름" 같은 뒤처진 문장을 띄우지 않는다.
  testWidgets('shouldSayTheTargetIsReachedWhenItIs', (tester) async {
    backend.lessonRepository.lesson = testLesson(
      requirement: const LessonRequirementView(
        priority: Priority.should,
        targets: AxisLevels(knowledge: 3, implementation: 3, explanation: 2, debugging: 0),
        planningLevels: AxisLevels(knowledge: 4, implementation: 3, explanation: 2, debugging: 0),
      ),
    );
    await openLesson(tester);

    expect(find.textContaining('이미 닿았어요'), findsOneWidget);
  });

  /// 계획에 없는 기술이면 보여 줄 기준이 없다 — 빈 줄을 만들지 않는다.
  testWidgets('shouldHideTheRequirementLineWithoutAPlan', (tester) async {
    await openLesson(tester);

    expect(find.byKey(const Key('lesson.requirement')), findsNothing);
  });

  testWidgets('shouldShowNotFoundForAKeyThatIsNotALessonKey', (tester) async {
    await pumpApp(tester, backend: backend, at: '/lessons/not-a-key');

    expect(find.byKey(const Key('lesson.whyItMatters')), findsNothing);
  });
}
