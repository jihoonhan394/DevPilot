import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/fixtures.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-PROJECTS (BL-CLI-32, AC-27 S1/S3/S4/S10).
void main() {
  const firstId = 'p0000000-0000-4000-8000-000000000001';
  late FakeBackend backend;

  setUp(() => backend = FakeBackend());

  Future<void> openProjects(WidgetTester tester) async {
    await pumpApp(tester, backend: backend);
    await goTo(tester, '/projects');
  }

  Future<void> openMenu(WidgetTester tester, String projectId, String item) async {
    await tapKey(tester, 'projects.menu.$projectId');
    await tester.tap(find.text(item).last);
    await tester.pumpAndSettle();
  }

  testWidgets('shouldListProjectWithTodayTargetAndRepoLinkText', (tester) async {
    await openProjects(tester);

    expect(find.text('주문 시스템'), findsOneWidget);
    expect(find.text('진행 중'), findsWidgets);
    expect(find.byKey(const Key('projects.todayTarget')), findsOneWidget);
    expect(find.text('github.com/example/order-service'), findsOneWidget);
    expect(find.text('9월 18일 수정'), findsOneWidget);
    expect(backend.sideProjectRepository.listStatuses, [null]);
  });

  testWidgets('shouldCreateProjectWithIdempotencyKeyAndValidateUrl', (tester) async {
    await openProjects(tester);

    await tapKey(tester, 'projects.addButton');
    expect(find.text('프로젝트 추가'), findsWidgets);
    expect(isButtonEnabled(tester, 'projects.saveButton'), isFalse);
    await enterTextByKey(tester, 'projects.nameField', '게시판 API');
    await enterTextByKey(tester, 'projects.repoUrlField', 'github.com/example/board');
    expect(find.text('http:// 또는 https:// 로 시작하는 주소를 입력해 주세요'), findsOneWidget);
    expect(isButtonEnabled(tester, 'projects.saveButton'), isFalse);

    await enterTextByKey(tester, 'projects.repoUrlField', 'https://github.com/example/board');
    await tapKey(tester, 'projects.saveButton');

    final create = backend.sideProjectRepository.creates.single;
    expect(create.request.toJson(), {
      'name': '게시판 API',
      'description': null,
      'repoUrl': 'https://github.com/example/board',
      'stack': null,
    });
    expect(create.key.value, isNotEmpty);
    expect(find.text('프로젝트를 추가했어요.'), findsOneWidget);
    expect(find.text('게시판 API'), findsOneWidget);
    // The newest ACTIVE project is now the Today target (SP-3).
    expect(
      find.descendant(
        of: find.byKey(const Key('projects.card.p0000001-0000-4000-8000-00000000000a')),
        matching: find.byKey(const Key('projects.todayTarget')),
      ),
      findsOneWidget,
    );
  });

  testWidgets('shouldPatchOnlyChangedFieldsAndClearWithEmptyString', (tester) async {
    await openProjects(tester);

    await openMenu(tester, firstId, '수정');
    expect(find.text('프로젝트 수정'), findsOneWidget);
    await enterTextByKey(tester, 'projects.stackField', '');
    await tapKey(tester, 'projects.saveButton');

    expect(backend.sideProjectRepository.updates.single.request.toJson(), {
      'stack': '',
      'version': 0,
    });
    expect(find.text('저장했어요.'), findsOneWidget);
    expect(find.text('Spring Boot, PostgreSQL'), findsNothing);
  });

  testWidgets('shouldReloadLatestValuesWhenEditConflicts', (tester) async {
    backend.sideProjectRepository.updateFailures.add(conflict());
    await openProjects(tester);

    await openMenu(tester, firstId, '수정');
    await enterTextByKey(tester, 'projects.nameField', '주문 시스템 v2');
    await tapKey(tester, 'projects.saveButton');

    expect(find.text('다른 곳에서 바뀌어 최신 내용으로 다시 불러왔어요.'), findsOneWidget);
    expect(find.widgetWithText(TextField, '주문 시스템'), findsOneWidget);
  });

  testWidgets('shouldPauseFromMenuAndExplainNoActiveProject', (tester) async {
    await openProjects(tester);

    await openMenu(tester, firstId, '잠시 멈춤');

    expect(backend.sideProjectRepository.updates.single.request.toJson(), {
      'status': 'PAUSED',
      'version': 0,
    });
    expect(find.text('다음 Today 계획부터 반영돼요. 이미 만든 과제는 그대로예요.'), findsOneWidget);
    expect(find.byKey(const Key('projects.noActive')), findsOneWidget);
    expect(find.byKey(const Key('projects.todayTarget')), findsNothing);
  });

  testWidgets('shouldDeleteAfterConfirmationAndOfferDefaultProject', (tester) async {
    await openProjects(tester);

    await openMenu(tester, firstId, '삭제');
    expect(find.text('이 프로젝트를 삭제할까요?'), findsOneWidget);
    expect(find.textContaining('진행 중인 프로젝트가 없으면'), findsOneWidget);
    await tapKey(tester, 'projects.deleteConfirmButton');

    expect(backend.sideProjectRepository.deletes, [firstId]);
    expect(find.text('프로젝트를 삭제했어요.'), findsOneWidget);
    expect(find.byKey(const Key('projects.emptyStartButton')), findsOneWidget);

    await tapKey(tester, 'projects.emptyStartButton');
    expect(find.widgetWithText(TextField, '주문 시스템'), findsOneWidget);
    await tapKey(tester, 'projects.saveButton');
    expect(backend.sideProjectRepository.creates.single.request.name, '주문 시스템');
  });

  testWidgets('shouldFilterByStatus', (tester) async {
    backend.sideProjectRepository.projects = [
      testProject(id: firstId),
      testProject(
        id: 'p0000000-0000-4000-8000-000000000002',
        name: '게시판 API',
        status: SideProjectStatus.done,
      ),
    ];
    await openProjects(tester);
    expect(find.text('게시판 API'), findsOneWidget);

    await tapKey(tester, 'projects.filter.done');

    expect(backend.sideProjectRepository.listStatuses.last, SideProjectStatus.done);
    expect(find.text('게시판 API'), findsOneWidget);
    expect(find.text('주문 시스템'), findsNothing);
  });

  testWidgets('shouldReachProjectsFromMoreTabOnMobile', (tester) async {
    usePhoneScreen(tester);
    addTearDown(() => resetScreenSize(tester));
    await pumpApp(tester, backend: backend);

    await tapKey(tester, 'shell.nav.more');
    expect(locationOf(tester), '/more');
    await tapKey(tester, 'more.projects');

    expect(locationOf(tester), '/projects');
    expect(find.text('주문 시스템'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
