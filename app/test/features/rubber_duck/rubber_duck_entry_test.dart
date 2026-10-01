import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:devpilot_app/core/widgets/ai_provider_notice.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/learning_fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// Rubber duck entry points outside SCR-RUBBER-DUCK (docs/02 §2.2, §6.5): Today in-progress
/// tasks, the review summary and SCR-PROJECTS.
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend(me: testMe(aiStatus: AiStatus.enabled)));

  Future<void> open(WidgetTester tester, String location) => pumpApp(
    tester,
    backend: backend,
    keyValueStore: MemoryKeyValueStore({aiProviderNoticeStorageKey: 'true'}),
    at: location,
  );

  void startExplainTask() {
    backend.todayRepository.today = testTodayView(
      mainTask: testMainTask(status: TaskStatus.inProgress),
    );
    backend.sessionRepository.sessions = [testSession(id: 'a0000000-0000-4000-8000-000000000009')];
  }

  testWidgets('shouldExplainAnInProgressExplainTaskAsAConcept', (tester) async {
    startExplainTask();
    await open(tester, AppRoutes.today);

    await tapKey(tester, 'today.explainButton');

    expect(
      locationOf(tester),
      AppRoutes.rubberDuckStart(
        targetType: 'CONCEPT',
        conceptKey: 'SPRING.TRANSACTION',
        skillCode: 'SPRING.TRANSACTION',
        taskId: mainTaskId,
      ),
    );
    expect(find.text('Spring Transaction 내 말로 설명하기'), findsOneWidget);
  });

  testWidgets('shouldBlockTheTodayDuckButtonWithItsReasonWhileAiIsOff', (tester) async {
    backend.meRepository.me = testMe(aiStatus: AiStatus.balanceExhausted);
    startExplainTask();
    await open(tester, AppRoutes.today);

    expect(isButtonEnabled(tester, 'today.explainButton'), isFalse);
    expect(find.text('AI 잔액이 떨어져 잠시 막아 두었어요.'), findsOneWidget);
    expect(find.byKey(const Key('ai.unavailableBanner')), findsNothing);
  });

  testWidgets('shouldOfferToExplainStruggledReviewCards', (tester) async {
    backend.reviewRepository.due = testDueReviews(count: 2);
    await open(tester, AppRoutes.reviewSession);

    await tapKey(tester, 'review.session.revealButton');
    await tapKey(tester, 'review.session.rate.again');
    await tapKey(tester, 'review.session.revealButton');
    await tapKey(tester, 'review.session.rate.good');

    expect(find.text('헷갈린 카드, 말로 설명해 볼까요?'), findsOneWidget);
    expect(find.text('복습 문항 1'), findsOneWidget);
    await tapKey(tester, 'review.summary.explain.1');
    // Leaving the session screen records the answered cards first (docs/02 SCR-REVIEW-SESSION).
    await tapKey(tester, 'completeSheet.submitButton');
    expect(locationOf(tester), startsWith('/rubber-duck/new?targetType=REVIEW_ITEM'));
    expect(locationOf(tester), contains(dueItemId(1)));
  });

  testWidgets('shouldHideTheReviewExplainListWhileAiIsOff', (tester) async {
    backend.meRepository.me = testMe(aiStatus: AiStatus.disabled);
    backend.reviewRepository.due = testDueReviews(count: 1);
    await open(tester, AppRoutes.reviewSession);

    await tapKey(tester, 'review.session.revealButton');
    await tapKey(tester, 'review.session.rate.again');

    expect(find.byKey(const Key('review.summary.explainList')), findsNothing);
  });

  testWidgets('shouldExplainAProjectFromItsCard', (tester) async {
    const projectId = 'b2000000-0000-4000-8000-000000000001';
    backend.sideProjectRepository.projects = [testProject(id: projectId)];
    await open(tester, AppRoutes.projects);

    await tapKey(tester, 'projects.explain.$projectId');

    expect(
      locationOf(tester),
      AppRoutes.rubberDuckStart(targetType: 'PROJECT_WORK', targetId: projectId),
    );
    expect(find.text('주문 시스템'), findsOneWidget);
  });
}
