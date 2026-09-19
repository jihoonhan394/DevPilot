import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:devpilot_app/core/widgets/ai_provider_notice.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-READ-CODE and the READ_CODE task on Today (BL-CLI-34, docs/02 §3.16, §4.15, AC-28 S8).
void main() {
  late FakeBackend backend;
  late KeyValueStore store;

  MainTaskView readingTask({TaskStatus status = TaskStatus.planned}) => testMainTask(
    taskType: TaskType.readCode,
    title: 'Spring PetClinic 읽기 — OwnerController.java 48~122줄',
    status: status,
  ).copyWith(readingKey: petclinicReadingKey);

  final readLocation = AppRoutes.readCode(petclinicReadingKey, taskId: mainTaskId);

  setUp(() {
    backend = FakeBackend(me: testMe(aiStatus: AiStatus.enabled));
    store = MemoryKeyValueStore({aiProviderNoticeStorageKey: 'true'});
  });

  Future<void> open(WidgetTester tester, String location) => pumpApp(
    tester,
    backend: backend,
    keyValueStore: store,
    accessToken: tokenWithSubject('user-1'),
    at: location,
  );

  void runReadingTask() {
    backend.todayRepository.today = testTodayView(
      mainTask: readingTask(status: TaskStatus.inProgress),
    );
    backend.sessionRepository.sessions = [testSession(id: 'a0000000-0000-4000-8000-000000000009')];
  }

  testWidgets('shouldOpenTheReadingGuideFromTodayStart', (tester) async {
    backend.todayRepository.today = testTodayView(mainTask: readingTask());
    await open(tester, AppRoutes.today);
    expect(find.text('코드는 내 컴퓨터에서 읽어요.'), findsOneWidget);

    await tapKey(tester, 'today.startButton');

    expect(locationOf(tester), readLocation);
    expect(backend.readingRepository.fetched, [petclinicReadingKey]);
    expect(find.textContaining('git clone https://github.com/spring-projects'), findsOneWidget);
    expect(find.text('기준 커밋 818c413'), findsOneWidget);
    expect(find.text('48~122줄 · 약 15분'), findsOneWidget);
  });

  testWidgets('shouldPutCloneAndCommitBeforePathAndQuestion', (tester) async {
    runReadingTask();
    await open(tester, readLocation);

    double top(String key) => tester.getTopLeft(find.byKey(Key(key))).dy;
    expect(top('readCode.cloneHint'), lessThan(top('readCode.path')));
    expect(top('readCode.path'), lessThan(top('readCode.question')));
    expect(find.textContaining('public class OwnerController'), findsNothing);
  });

  testWidgets('shouldWarnForARepositoryWithoutLicense', (tester) async {
    backend.readingRepository.readings[petclinicReadingKey] = testReading(
      license: 'UNSPECIFIED',
      subPath: 'server',
    );
    await open(tester, readLocation);

    expect(find.text('읽기만 · 복사 금지'), findsOneWidget);
    expect(find.textContaining('라이선스가 명시되지 않은 저장소예요'), findsOneWidget);
    expect(find.textContaining('server 폴더 기준'), findsOneWidget);
  });

  testWidgets('shouldExplainTheReadingWithTheRubberDuck', (tester) async {
    runReadingTask();
    await open(tester, readLocation);

    await tapKey(tester, 'readCode.explainButton');

    expect(
      locationOf(tester),
      AppRoutes.rubberDuckStart(
        targetType: 'CODE_READING',
        targetId: mainTaskId,
        skillCode: 'SPRING.MVC_REST',
        taskId: mainTaskId,
      ),
    );
    expect(find.text('Spring PetClinic · OwnerController.java 48~122줄'), findsOneWidget);
  });

  testWidgets('shouldBlockExplainingWhileAiIsOffButKeepTheGuide', (tester) async {
    backend.meRepository.me = testMe(aiStatus: AiStatus.disabled);
    runReadingTask();
    await open(tester, readLocation);

    expect(isButtonEnabled(tester, 'readCode.explainButton'), isFalse);
    expect(find.textContaining('설명(러버덕)은 AI가 필요해'), findsOneWidget);
    expect(find.byKey(const Key('readCode.cloneCopyButton')), findsOneWidget);
  });

  testWidgets('shouldHideTaskActionsWithoutATask', (tester) async {
    await open(tester, AppRoutes.readCode(petclinicReadingKey));

    expect(find.text('Today의 코드 읽기 과제에서 열면 설명하고 완료할 수 있어요.'), findsOneWidget);
    expect(find.byKey(const Key('readCode.explainButton')), findsNothing);
  });

  testWidgets('shouldShowNotFoundForBadOrUnknownKeys', (tester) async {
    await open(tester, '/today/read/read.petclinic.x');
    expect(find.text('페이지를 찾을 수 없어요'), findsOneWidget);

    await goTo(tester, '/today/read/READ.NOPE.TOPIC.001');
    expect(find.text('페이지를 찾을 수 없어요'), findsOneWidget);
  });

  testWidgets('shouldRecordThePartialSessionAsDeferred', (tester) async {
    runReadingTask();
    await open(tester, readLocation);

    await tapKey(tester, 'readCode.partialButton');
    expect(find.byKey(const Key('completeSheet.readingFeedback')), findsNothing);
    await tapKey(tester, 'completeSheet.submitButton');

    expect(backend.todayRepository.patches.last.request.toJson(), {
      'status': 'DEFERRED',
      'version': 0,
    });
    expect(locationOf(tester), AppRoutes.today);
  });

  testWidgets('shouldSendBackToTheReadingUntilAnExplanationIsFinished', (tester) async {
    runReadingTask();
    await open(tester, AppRoutes.today);

    expect(find.byKey(const Key('today.completeButton')), findsNothing);
    await tapKey(tester, 'today.readCodeBackButton');
    expect(locationOf(tester), readLocation);
  });

  testWidgets('shouldCompleteWithTheChosenReadingFeedback', (tester) async {
    runReadingTask();
    backend.rubberDuckRepository.sessions[duckSessionId] = testDuckSession(
      targetType: RubberDuckTargetType.codeReading,
      targetId: mainTaskId,
      status: RubberDuckStatus.completed,
    );
    store.write('devpilot.rubberduck.task.$mainTaskId', duckSessionId);
    await open(tester, AppRoutes.todayComplete(mainTaskId));

    expect(find.text('이 코드 읽기는 어땠나요? (선택)'), findsOneWidget);
    await tapKey(tester, 'completeSheet.feedback.tooHard');
    await tapKey(tester, 'completeSheet.submitButton');

    expect(backend.todayRepository.patches.last.request.toJson(), {
      'status': 'COMPLETED',
      'readingFeedback': 'TOO_HARD',
      'version': 0,
    });
  });

  testWidgets('shouldLeaveTheFeedbackOutWhenNoneIsChosen', (tester) async {
    runReadingTask();
    backend.rubberDuckRepository.sessions[duckSessionId] = testDuckSession(
      targetType: RubberDuckTargetType.codeReading,
      targetId: mainTaskId,
      status: RubberDuckStatus.completed,
    );
    store.write('devpilot.rubberduck.task.$mainTaskId', duckSessionId);
    await open(tester, AppRoutes.today);

    await tapKey(tester, 'today.completeButton');
    await tapKey(tester, 'completeSheet.feedback.helpful');
    await tapKey(tester, 'completeSheet.feedback.helpful');
    await tapKey(tester, 'completeSheet.submitButton');

    expect(backend.todayRepository.patches.last.request.toJson(), {
      'status': 'COMPLETED',
      'version': 0,
    });
  });

  testWidgets('shouldExplainThatTheRubberDuckIsTheCompletionCondition', (tester) async {
    runReadingTask();
    backend.rubberDuckRepository.sessions[duckSessionId] = testDuckSession(
      targetType: RubberDuckTargetType.codeReading,
      targetId: mainTaskId,
      status: RubberDuckStatus.completed,
    );
    store.write('devpilot.rubberduck.task.$mainTaskId', duckSessionId);
    backend.todayRepository.patchFailures.add(
      const ApiException(code: ApiErrorCode.invalidStateTransition, status: 409),
    );
    await open(tester, AppRoutes.today);

    await tapKey(tester, 'today.completeButton');
    await tapKey(tester, 'completeSheet.submitButton');

    expect(find.text('러버덕으로 설명을 마쳐야 완료할 수 있어요.'), findsOneWidget);
  });

  testWidgets('shouldNotAskReadingFeedbackForOtherTasks', (tester) async {
    backend.todayRepository.today = testTodayView(
      mainTask: testMainTask(status: TaskStatus.inProgress),
    );
    backend.sessionRepository.sessions = [testSession(id: 'a0000000-0000-4000-8000-000000000009')];
    await open(tester, AppRoutes.today);

    await tapKey(tester, 'today.completeButton');

    expect(find.byKey(const Key('completeSheet.readingFeedback')), findsNothing);
  });

  testWidgets('shouldRememberTheFinishedDuckForTheReadingTask', (tester) async {
    runReadingTask();
    await open(
      tester,
      AppRoutes.rubberDuckStart(
        targetType: 'CODE_READING',
        targetId: mainTaskId,
        taskId: mainTaskId,
      ),
    );
    await enterTextByKey(tester, 'rubberDuck.input', '컨트롤러가 조회만 해서 Service가 없다.');
    await tapKey(tester, 'rubberDuck.sendButton');
    await tapKey(tester, 'rubberDuck.finishButton');

    final session = backend.rubberDuckRepository.sessions.values.single;
    expect(store.read('devpilot.rubberduck.task.$mainTaskId'), session.id);
    expect(store.read('devpilot.rubberduck.active.user-1'), isNull);
    await tapKey(tester, 'rubberDuck.toTodayButton');
    expect(find.text('이 코드 읽기는 어땠나요? (선택)'), findsOneWidget);
  });
}
