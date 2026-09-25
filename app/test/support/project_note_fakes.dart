import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/project/data/project_note_models.dart';
import 'package:devpilot_app/features/project/data/project_note_repository.dart';

const decisionNoteId = 'd1000000-0000-4000-8000-000000000001';
const incidentNoteId = 'd1000000-0000-4000-8000-000000000002';

SideProjectNoteView testDecisionNote({String sideProjectId = 'p1'}) => SideProjectNoteView(
  id: decisionNoteId,
  sideProjectId: sideProjectId,
  noteType: SideProjectNoteType.decision,
  title: '주문 번호를 시퀀스 기반으로',
  occurredOn: '2026-10-09',
  skill: const SkillRef(
    id: 's1000000-0000-4000-8000-000000000001',
    code: 'DATABASE.INDEX',
    name: 'Index',
    category: SkillCategory.database,
  ),
  decisionChoice: 'yyyyMMdd + 일련번호 형식을 쓰기로 했다',
  decisionOptions: 'UUID v4 / UUID v7 / 날짜 + 시퀀스',
  decisionRationale: '날짜 범위 조회가 가장 잦다',
  createdAt: DateTime.utc(2026, 10, 9),
  updatedAt: DateTime.utc(2026, 10, 9),
  version: 0,
);

SideProjectNoteView testIncidentNote({String sideProjectId = 'p1'}) => SideProjectNoteView(
  id: incidentNoteId,
  sideProjectId: sideProjectId,
  noteType: SideProjectNoteType.incident,
  title: '재고가 음수로 내려갔다',
  occurredOn: '2026-10-11',
  incidentSymptom: '동시에 주문 두 건이 들어오면 재고가 음수가 됐다',
  incidentDetection: '주문 목록에서 수량이 -1인 것을 봤다',
  incidentFix: '재고 차감을 비관적 락으로 감쌌다',
  incidentPrevention: '동시 주문 테스트를 추가했다',
  createdAt: DateTime.utc(2026, 10, 11),
  updatedAt: DateTime.utc(2026, 10, 11),
  version: 0,
);

final class FakeProjectNoteRepository implements ProjectNoteRepository {
  /// `occurredOn` DESC — 서버 정렬을 그대로 흉내 낸다.
  List<SideProjectNoteView> notes = [testIncidentNote(), testDecisionNote()];

  final created = <SideProjectNoteCreateRequest>[];
  final patched = <({String noteId, SideProjectNotePatchRequest request})>[];
  final deleted = <String>[];
  final listQueries = <SideProjectNoteType?>[];

  /// 설정하면 저장이 그 오류로 실패한다.
  ApiException? saveFailure;

  /// 409 뒤에 돌려줄 최신 값.
  SideProjectNoteView? latestAfterConflict;

  @override
  Future<CursorPage<SideProjectNoteView>> fetchNotes(
    String sideProjectId, {
    SideProjectNoteType? noteType,
    String? cursor,
  }) async {
    listQueries.add(noteType);
    final items = noteType == null
        ? notes
        : notes.where((note) => note.noteType == noteType).toList();
    return CursorPage(items: items);
  }

  @override
  Future<SideProjectNoteView> fetchNote(String sideProjectId, String noteId) async =>
      latestAfterConflict ?? notes.firstWhere((note) => note.id == noteId);

  @override
  Future<SideProjectNoteView> createNote(
    String sideProjectId,
    SideProjectNoteCreateRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    final failure = saveFailure;
    if (failure != null) {
      throw failure;
    }
    created.add(request);
    return testDecisionNote(sideProjectId: sideProjectId);
  }

  @override
  Future<SideProjectNoteView> updateNote(
    String sideProjectId,
    String noteId,
    SideProjectNotePatchRequest request,
  ) async {
    final failure = saveFailure;
    if (failure != null) {
      saveFailure = null;
      throw failure;
    }
    patched.add((noteId: noteId, request: request));
    return notes.firstWhere((note) => note.id == noteId);
  }

  @override
  Future<void> deleteNote(String sideProjectId, String noteId) async {
    deleted.add(noteId);
    notes = notes.where((note) => note.id != noteId).toList();
  }

  @override
  Future<({String fileName, String markdown})> exportNotes(String sideProjectId) async =>
      (fileName: 'notes-$sideProjectId-20261102.md', markdown: '# 주문 시스템 — 결정·장애 기록\n');
}
