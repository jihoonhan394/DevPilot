import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/history/recent_store.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-RECENT (docs/02 §3.19). 서버에 "내가 한 일" 목록이 없어서 생긴 자리다
/// (DevPilot-ops/reachability-audit-2026-09-29.md).
void main() {
  late FakeBackend backend;

  setUp(() {
    backend = FakeBackend(me: testMe());
  });

  group('RecentStore', () {
    RecentStore storeFor(KeyValueStore values) => RecentStore(values, 'user-1');

    RecentEntry entry(String route, {String title = '제목'}) => RecentEntry(
      kind: RecentKind.rubberDuck,
      route: route,
      title: title,
      visitedAt: DateTime.utc(2026, 9, 29),
    );

    test('shouldPutTheNewestFirstAndDropAnOlderVisitToTheSameRoute', () {
      final store = storeFor(MemoryKeyValueStore());
      store.add(entry('/rubber-duck/a', title: '첫 방문'));
      store.add(entry('/rubber-duck/b'));
      final kept = store.add(entry('/rubber-duck/a', title: '다시 방문'));

      expect(kept.map((item) => item.route), ['/rubber-duck/a', '/rubber-duck/b']);
      expect(kept.first.title, '다시 방문');
    });

    test('shouldKeepAtMostTwentyEntries', () {
      final store = storeFor(MemoryKeyValueStore());
      for (var index = 0; index < 25; index++) {
        store.add(entry('/rubber-duck/$index'));
      }

      final kept = store.read();
      expect(kept, hasLength(RecentStore.limit));
      expect(kept.first.route, '/rubber-duck/24');
      expect(kept.last.route, '/rubber-duck/5');
    });

    test('shouldTreatABrokenOrMissingValueAsEmpty', () {
      final values = MemoryKeyValueStore({'${RecentStore.prefix}user-1': '{not json'});
      expect(storeFor(values).read(), isEmpty);
      expect(storeFor(MemoryKeyValueStore()).read(), isEmpty);
    });

    test('shouldRememberNothingWithoutASignedInUser', () {
      final store = RecentStore(MemoryKeyValueStore(), null);
      expect(store.add(entry('/rubber-duck/a')), isEmpty);
      expect(store.read(), isEmpty);
    });
  });

  testWidgets('shouldSayTheListIsEmptyBeforeAnythingIsOpened', (tester) async {
    usePhoneScreen(tester);
    addTearDown(() => resetScreenSize(tester));
    await pumpApp(tester, backend: backend, at: AppRoutes.recent);

    expect(find.byKey(const Key('recent.empty')), findsOneWidget);
  });

  testWidgets('shouldReachRecentFromMoreAndGoBackToAVisitedScreen', (tester) async {
    usePhoneScreen(tester);
    addTearDown(() => resetScreenSize(tester));
    await pumpApp(
      tester,
      backend: backend,
      at: AppRoutes.more,
      accessToken: tokenWithSubject('user-1'),
      keyValueStore: MemoryKeyValueStore({
        '${RecentStore.prefix}user-1':
            '[{"kind":"rubberDuck","route":"/rubber-duck/s1","title":"트랜잭션 설명",'
            '"visitedAt":"2026-09-29T00:00:00Z"}]',
      }),
    );

    await tapKeyInList(tester, 'more.recent');
    expect(locationOf(tester), AppRoutes.recent);
    expect(find.text('트랜잭션 설명'), findsOneWidget);

    await tapKey(tester, 'recent.item./rubber-duck/s1');
    expect(locationOf(tester), '/rubber-duck/s1');
  });
}
