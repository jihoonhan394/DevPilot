import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:devpilot_app/features/today/presentation/today_first_run_card.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// BL-CLI-49 — 처음 쓰는 사람 안내 (docs/02 SCR-TODAY·SCR-MORE·§3.4).
///
/// 이 화면들의 일은 "어디에 무엇이 있는지"가 아니라 <b>무엇부터 하는지</b>를 말하는 것이다.
void main() {
  late FakeBackend backend;
  late MemoryKeyValueStore store;

  setUp(() {
    backend = FakeBackend();
    store = MemoryKeyValueStore();
  });

  Future<void> open(WidgetTester tester, String location) => pumpApp(
    tester,
    backend: backend,
    accessToken: tokenWithSubject('user-1'),
    at: location,
    keyValueStore: store,
  );

  /// 오늘 계획을 만들기 전에, 세 걸음의 순서를 먼저 말한다.
  testWidgets('shouldTellTheOrderOfTheThreeStepsBeforeAnythingElse', (tester) async {
    await open(tester, AppRoutes.today);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('today.firstRun')), findsOneWidget);
    expect(find.text('매일 여는 곳은 여기 하나예요.'), findsOneWidget);
    expect(find.textContaining('1. 시간과 컨디션을 고르면'), findsOneWidget);
    expect(find.textContaining('2. 혼자 해 보고'), findsOneWidget);
    expect(find.textContaining('3. 끝낸 것은'), findsOneWidget);
  });

  /// 한 번 읽으면 끝나는 안내다 — 닫으면 이 브라우저에서 다시 뜨지 않는다.
  testWidgets('shouldNotComeBackOnceDismissed', (tester) async {
    await open(tester, AppRoutes.today);
    await tester.pumpAndSettle();

    await tapKey(tester, 'today.firstRun.dismiss');
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('today.firstRun')), findsNothing);
    expect(store.read('${firstRunDismissedKeyPrefix}user-1'), '1');
  });

  testWidgets('shouldStayHiddenAfterAReload', (tester) async {
    store.write('${firstRunDismissedKeyPrefix}user-1', '1');

    await open(tester, AppRoutes.today);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('today.firstRun')), findsNothing);
  });

  /// 저장이 막힌 브라우저에서도 화면은 그대로 동작한다 (docs/02 §8.3).
  testWidgets('shouldKeepWorkingWhenStorageIsBlocked', (tester) async {
    await pumpApp(
      tester,
      backend: backend,
      accessToken: tokenWithSubject('user-1'),
      at: AppRoutes.today,
      keyValueStore: _BlockedKeyValueStore(),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('today.firstRun')), findsOneWidget);
    await tapKey(tester, 'today.firstRun.dismiss');
    await tester.pumpAndSettle();
    // 저장은 실패해도 이번 화면에서는 닫힌다
    expect(find.byKey(const Key('today.firstRun')), findsNothing);
  });

  /// 메뉴는 기능 이름만 늘어놓지 않는다 — 묶음과 한 줄 설명이 "무엇을 하러 가는지"를 말한다.
  testWidgets('shouldGroupTheMenuAndSayWhatEachPlaceIsFor', (tester) async {
    // SCR-MORE 는 모바일 전용이다 — 600 px 이상이면 rail 이 대신하고 /today 로 보낸다
    usePhoneScreen(tester);
    addTearDown(() => resetScreenSize(tester));

    await open(tester, AppRoutes.more);
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('more.group.learn')), findsOneWidget);
    expect(find.byKey(const Key('more.group.record')), findsOneWidget);
    expect(find.text('말이 헷갈릴 때 찾는 곳'), findsOneWidget);
    // 목록이 길어 아래쪽 줄은 스크롤해야 만들어진다.
    await expectKeyInList(tester, 'more.projects');
    expect(find.text('내가 만든 것과 그때 내린 결정'), findsOneWidget);
  });
}

/// 사생활 보호 모드처럼 저장이 막힌 브라우저.
final class _BlockedKeyValueStore implements KeyValueStore {
  @override
  String? read(String key) => null;

  @override
  void write(String key, String value) {}

  @override
  void remove(String key) {}
}
