import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_item_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-REVIEW-ITEMS and SCR-REVIEW-ITEM-EDIT (BL-CLI-14, docs/02 §3.6, §4.4, AC-05).
void main() {
  late FakeBackend backend;

  setUp(() => backend = FakeBackend(me: testMe(aiStatus: AiStatus.enabled)));

  Future<void> openMenu(WidgetTester tester, String itemId, String action) async {
    await tapKey(tester, 'reviewItems.menuButton.$itemId');
    await tapKey(tester, 'reviewItems.menu.$action');
  }

  testWidgets('shouldListActiveCardsWithMetaFromReviewHome', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.review);

    await tapKey(tester, 'review.home.manageLink');

    expect(locationOf(tester), AppRoutes.reviewItems);
    expect(backend.reviewItemRepository.queries.last.status, ReviewItemStatus.active);
    expect(find.text('트랜잭션 전파 REQUIRES_NEW는 언제 쓰나요?'), findsOneWidget);
    expect(find.text('Spring Transaction · 설명 · 러버덕'), findsOneWidget);
    expect(find.text('다음 복습 9월 20일 (일)'), findsOneWidget);
    expect(find.text('마지막 결과: 어려움'), findsOneWidget);
    expect(find.text('읽기 전용 트랜잭션의 장점은?'), findsNothing);
  });

  testWidgets('shouldFilterByStatusThroughTheRoute', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.reviewItems);

    await tapVisible(tester, find.text('일시중지').first);

    expect(locationOf(tester), AppRoutes.reviewItemsFor(status: 'SUSPENDED'));
    expect(find.text('읽기 전용 트랜잭션의 장점은?'), findsOneWidget);
  });

  testWidgets('shouldSuspendAndReactivateCards', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.reviewItems);

    await openMenu(tester, reviewItemId, 'suspend');
    expect(backend.reviewItemRepository.updates.single.request.toJson(), {
      'status': 'SUSPENDED',
      'version': 3,
    });
    expect(find.text('트랜잭션 전파 REQUIRES_NEW는 언제 쓰나요?'), findsNothing);

    await goTo(tester, AppRoutes.reviewItemsFor(status: 'SUSPENDED'));
    await openMenu(tester, otherReviewItemId, 'reactivate');
    expect(backend.reviewItemRepository.updates.last.request.status, ReviewItemStatus.active);
    expect(find.text('내일부터 다시 출제돼요.'), findsOneWidget);
  });

  testWidgets('shouldArchiveOnlyAfterConfirming', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.reviewItems);

    await openMenu(tester, reviewItemId, 'archive');
    expect(find.text('보관한 카드는 다시 출제되지 않고 되돌릴 수 없어요. 보관할까요?'), findsOneWidget);
    await tapKey(tester, 'reviewItems.archiveConfirmButton');

    expect(backend.reviewItemRepository.updates.single.request.status, ReviewItemStatus.archived);
  });

  testWidgets('shouldReloadWhenTheCardChangedElsewhere', (tester) async {
    backend.reviewItemRepository.updateFailures.add(
      const ApiException(code: ApiErrorCode.concurrentModification, status: 409),
    );
    await pumpApp(tester, backend: backend, at: AppRoutes.reviewItems);

    await openMenu(tester, reviewItemId, 'suspend');

    expect(find.text('다른 곳에서 먼저 바뀌었어요. 최신 내용으로 다시 불러왔어요.'), findsOneWidget);
    expect(backend.reviewItemRepository.queries, hasLength(2));
  });

  testWidgets('shouldCreateAManualCard', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.reviewItemsNew);

    expect(isButtonEnabled(tester, 'reviewEdit.saveButton'), isFalse);
    await selectDropdown(tester, 'reviewEdit.skillDropdown', 'Spring Transaction');
    await tapKey(tester, 'reviewEdit.type.explain');
    await enterTextByKey(tester, 'reviewEdit.promptField', '프록시 내부 호출 문제는?');
    await enterTextByKey(tester, 'reviewEdit.expectedField', '프록시를 거치지 않는다.');
    await enterTextByKey(tester, 'reviewEdit.rubric.1', '프록시 언급');
    await tapKey(tester, 'reviewEdit.rubricAddButton');
    await enterTextByKey(tester, 'reviewEdit.rubric.2', '해결 방법');
    await tapKey(tester, 'reviewEdit.saveButton');

    final request = backend.reviewItemRepository.creates.single.request;
    expect(request.skillCode, 'SPRING.TRANSACTION');
    expect(request.reviewType, ReviewType.explain);
    expect(request.rubric, ['프록시 언급', '해결 방법']);
    expect(request.conceptKey, matches(RegExp(r'^MANUAL:[0-9A-F-]{36}$')));
    expect(find.text('카드를 추가했어요. 내일부터 복습에 나와요.'), findsOneWidget);
    expect(locationOf(tester), AppRoutes.reviewItems);
  });

  testWidgets('shouldSayWhenTheSameConceptAlreadyExisted', (tester) async {
    backend.reviewItemRepository.answerExisting = true;
    await pumpApp(tester, backend: backend, at: AppRoutes.reviewItemsNew);

    await selectDropdown(tester, 'reviewEdit.skillDropdown', 'Collection');
    await enterTextByKey(tester, 'reviewEdit.promptField', '질문');
    await enterTextByKey(tester, 'reviewEdit.expectedField', '정답');
    await enterTextByKey(tester, 'reviewEdit.rubric.1', '포인트');
    await tapKey(tester, 'reviewEdit.saveButton');

    expect(find.text('같은 개념의 카드가 이미 있어 그 카드를 다시 복습에 넣었어요.'), findsOneWidget);
  });

  testWidgets('shouldEditOnlyThePromptAndExpectedAnswer', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.reviewItems);

    await openMenu(tester, reviewItemId, 'edit');
    expect(locationOf(tester), AppRoutes.reviewItem(reviewItemId));
    expect(find.byKey(const Key('reviewEdit.skillDropdown')), findsNothing);
    expect(isButtonEnabled(tester, 'reviewEdit.saveButton'), isFalse);
    await enterTextByKey(tester, 'reviewEdit.promptField', 'REQUIRES_NEW는 언제 필요한가요?');
    await tapKey(tester, 'reviewEdit.saveButton');

    expect(backend.reviewItemRepository.updates.single.request.toJson(), {
      'prompt': 'REQUIRES_NEW는 언제 필요한가요?',
      'version': 3,
    });
    expect(find.text('저장했어요.'), findsOneWidget);
  });

  testWidgets('shouldAskBeforeLeavingAnUnsavedCard', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.reviewItemsNew);
    await enterTextByKey(tester, 'reviewEdit.promptField', '작성 중');

    routerOf(tester).go(AppRoutes.review);
    await tester.pumpAndSettle();

    expect(find.text('저장하지 않은 내용이 있어요'), findsOneWidget);
    await tapKey(tester, 'common.leaveButton');
    expect(locationOf(tester), AppRoutes.review);
  });

  testWidgets('shouldReopenTheListWithoutTheCard', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.reviewItem(reviewItemId));

    expect(locationOf(tester), AppRoutes.reviewItems);
    expect(find.text('카드 목록에서 다시 선택해 주세요.'), findsOneWidget);
  });

  testWidgets('shouldExplainACardWithTheRubberDuck', (tester) async {
    await pumpApp(tester, backend: backend, at: AppRoutes.reviewItems);

    await openMenu(tester, reviewItemId, 'explain');

    expect(locationOf(tester), startsWith('/rubber-duck/new?targetType=REVIEW_ITEM'));
    expect(locationOf(tester), contains(reviewItemId));
  });

  testWidgets('shouldHideTheRubberDuckMenuItemWhileAiIsOff', (tester) async {
    backend.meRepository.me = testMe(aiStatus: AiStatus.disabled);
    await pumpApp(tester, backend: backend, at: AppRoutes.reviewItems);

    await tapKey(tester, 'reviewItems.menuButton.$reviewItemId');

    expect(find.byKey(const Key('reviewItems.menu.explain')), findsNothing);
    expect(find.byKey(const Key('reviewItems.menu.suspend')), findsOneWidget);
  });

  test('shouldSendOnlyChangedFieldsInThePatch', () {
    const request = ReviewItemPatchRequest(status: ReviewItemStatus.suspended, version: 2);
    expect(request.toJson(), {'status': 'SUSPENDED', 'version': 2});
  });
}
