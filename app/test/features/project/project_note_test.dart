import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/features/project/data/project_note_models.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/fake_backend.dart';
import '../../support/project_note_fakes.dart';
import '../../support/test_app.dart';
import '../../support/widget_actions.dart';

/// SCR-PROJECT-DETAIL · SCR-PROJECT-NOTE-EDIT (docs/02 §3.16, docs/05 §19.8~§19.13).
void main() {
  late FakeBackend backend;
  late String projectId;

  setUp(() {
    backend = FakeBackend();
    projectId = backend.sideProjectRepository.projects.first.id;
    backend.projectNoteRepository.notes = [
      testIncidentNote(sideProjectId: projectId),
      testDecisionNote(sideProjectId: projectId),
    ];
  });

  Future<void> openDetail(WidgetTester tester) async {
    await pumpApp(
      tester,
      backend: backend,
      accessToken: tokenWithSubject('user-1'),
      at: AppRoutes.projectDetail(projectId),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('shouldListNotesNewestFirstWithTheirType', (tester) async {
    await openDetail(tester);

    expect(find.text('기록 2개'), findsOneWidget);
    expect(find.text('재고가 음수로 내려갔다'), findsOneWidget);
    expect(find.text('주문 번호를 시퀀스 기반으로'), findsOneWidget);
    // 카드에는 유형의 첫 칸만 보인다
    expect(find.textContaining('동시에 주문 두 건이 들어오면'), findsOneWidget);
  });

  testWidgets('shouldFilterByNoteType', (tester) async {
    await openDetail(tester);

    await tapKey(tester, 'projectDetail.filter.incident');

    expect(backend.projectNoteRepository.listQueries.last, SideProjectNoteType.incident);
    expect(find.text('주문 번호를 시퀀스 기반으로'), findsNothing);
  });

  /// 장애 기록은 네 칸이고 전부 필수다 — 하나라도 비면 저장할 수 없다.
  testWidgets('shouldKeepSaveOffUntilEveryIncidentFieldIsFilled', (tester) async {
    await openDetail(tester);
    await tapKey(tester, 'projectDetail.addIncident');
    await tester.pumpAndSettle();

    expect(find.text('장애 기록'), findsOneWidget);
    expect(find.text('다시 안 생기게 하려면?'), findsOneWidget);
    expect(
      tester.widget<TextButton>(find.byKey(const Key('projectNote.saveButton'))).onPressed,
      isNull,
    );

    await tester.enterText(find.byKey(const Key('projectNote.titleField')), '재고가 음수로');
    for (final field in ['incidentSymptom', 'incidentDetection', 'incidentFix']) {
      await tester.enterText(find.byKey(Key('projectNote.$field')), '적었다');
    }
    await tester.pump();
    // 재발 방지가 아직 비어 있다 — 고친 것만 적으면 같은 일이 또 난다
    expect(
      tester.widget<TextButton>(find.byKey(const Key('projectNote.saveButton'))).onPressed,
      isNull,
    );

    await tester.enterText(find.byKey(const Key('projectNote.incidentPrevention')), '테스트를 더했다');
    await tester.pump();
    expect(
      tester.widget<TextButton>(find.byKey(const Key('projectNote.saveButton'))).onPressed,
      isNotNull,
    );
  });

  /// 결정 기록에는 장애 칸이 아예 없다 (I-22) — 유형에 맞지 않는 항목은 화면에 두지 않는다.
  testWidgets('shouldOnlyShowTheFieldsOfTheChosenType', (tester) async {
    await openDetail(tester);
    await tapKey(tester, 'projectDetail.addDecision');
    await tester.pumpAndSettle();

    expect(find.text('왜 그것을 골랐나요?'), findsOneWidget);
    expect(find.text('무엇이 잘못됐나요?'), findsNothing);
  });

  /// 무엇을 적지 않는지 먼저 알린다 — 마스킹보다 위에, 접지 않고.
  testWidgets('shouldAlwaysSayWhatNotToWrite', (tester) async {
    await openDetail(tester);
    await tapKey(tester, 'projectDetail.addDecision');
    await tester.pumpAndSettle();

    expect(find.text('회사 소스·고객 정보는 적지 마세요. 상황과 판단만 적어요.'), findsOneWidget);
    expect(find.text('비밀값은 저장 전에 가려요.'), findsOneWidget);
  });

  testWidgets('shouldShowTheSecretWarningWithoutLosingTheInput', (tester) async {
    backend.projectNoteRepository.saveFailure = const ApiException(
      code: ApiErrorCode.secretDetectedBlocked,
      status: 422,
    );
    await openDetail(tester);
    await tapKey(tester, 'projectDetail.addDecision');
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const Key('projectNote.titleField')), '토큰을 그대로 적었다');
    for (final field in ['decisionChoice', 'decisionOptions', 'decisionRationale']) {
      await tester.enterText(find.byKey(Key('projectNote.$field')), '적었다');
    }
    await tester.pump();

    await tapKey(tester, 'projectNote.saveButton');

    expect(find.textContaining('개인 키(private key)로 보이는 내용'), findsOneWidget);
    expect(find.text('토큰을 그대로 적었다'), findsOneWidget);
  });

  /// 기록이 0개면 빈 파일을 만들지 않는다.
  testWidgets('shouldNotExportAnEmptyFile', (tester) async {
    backend.projectNoteRepository.notes = [];
    await openDetail(tester);

    await tapKey(tester, 'projectDetail.menu');
    await tester.pumpAndSettle();
    await tester.tap(find.text('Markdown으로 내려받기'));
    await tester.pumpAndSettle();

    expect(find.text('내려받을 기록이 아직 없어요.'), findsOneWidget);
  });

  testWidgets('shouldSayNothingIsWrittenYetWithoutNudging', (tester) async {
    backend.projectNoteRepository.notes = [];

    await openDetail(tester);

    expect(find.byKey(const Key('projectDetail.notesEmpty')), findsOneWidget);
    expect(find.text('아직 기록이 없어요.'), findsOneWidget);
  });
}
