import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/project/data/project_note_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// 프로젝트 기록 (docs/05 §19.9~§19.13). AI를 호출하지 않는다.
abstract interface class ProjectNoteRepository {
  /// `GET …/notes?noteType=&cursor=` (`occurredOn` DESC, `id` DESC).
  Future<CursorPage<SideProjectNoteView>> fetchNotes(
    String sideProjectId, {
    SideProjectNoteType? noteType,
    String? cursor,
  });

  /// `GET …/notes/{noteId}`.
  Future<SideProjectNoteView> fetchNote(String sideProjectId, String noteId);

  /// `POST …/notes` → 201.
  Future<SideProjectNoteView> createNote(
    String sideProjectId,
    SideProjectNoteCreateRequest request, {
    required IdempotencyKey idempotencyKey,
  });

  /// `PATCH …/notes/{noteId}`.
  Future<SideProjectNoteView> updateNote(
    String sideProjectId,
    String noteId,
    SideProjectNotePatchRequest request,
  );

  /// `DELETE …/notes/{noteId}` → 204.
  Future<void> deleteNote(String sideProjectId, String noteId);

  /// `GET …/notes/export` → Markdown 본문. 파일 이름은 서버가 정한다 (docs/05 §19.13).
  Future<({String fileName, String markdown})> exportNotes(String sideProjectId);
}

final class ApiProjectNoteRepository implements ProjectNoteRepository {
  ApiProjectNoteRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<CursorPage<SideProjectNoteView>> fetchNotes(
    String sideProjectId, {
    SideProjectNoteType? noteType,
    String? cursor,
  }) async {
    assert(noteType != SideProjectNoteType.unknown, 'unknown is never sent');
    final json = await _apiClient.getJson(
      '/side-projects/$sideProjectId/notes',
      queryParameters: {'noteType': ?_wireName(noteType), 'cursor': ?cursor},
    );
    return CursorPage.fromJson(
      json,
      (item) => SideProjectNoteView.fromJson(item! as Map<String, Object?>),
    );
  }

  @override
  Future<SideProjectNoteView> fetchNote(String sideProjectId, String noteId) async =>
      SideProjectNoteView.fromJson(
        await _apiClient.getJson('/side-projects/$sideProjectId/notes/$noteId'),
      );

  @override
  Future<SideProjectNoteView> createNote(
    String sideProjectId,
    SideProjectNoteCreateRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async => SideProjectNoteView.fromJson(
    await _apiClient.postJson(
      '/side-projects/$sideProjectId/notes',
      body: request.toJson(),
      idempotencyKey: idempotencyKey,
    ),
  );

  @override
  Future<SideProjectNoteView> updateNote(
    String sideProjectId,
    String noteId,
    SideProjectNotePatchRequest request,
  ) async => SideProjectNoteView.fromJson(
    await _apiClient.patchJson(
      '/side-projects/$sideProjectId/notes/$noteId',
      body: request.toJson(),
    ),
  );

  @override
  Future<void> deleteNote(String sideProjectId, String noteId) =>
      _apiClient.delete('/side-projects/$sideProjectId/notes/$noteId');

  @override
  Future<({String fileName, String markdown})> exportNotes(String sideProjectId) =>
      _apiClient.getFile('/side-projects/$sideProjectId/notes/export');

  static String? _wireName(SideProjectNoteType? noteType) => switch (noteType) {
    SideProjectNoteType.decision => 'DECISION',
    SideProjectNoteType.incident => 'INCIDENT',
    _ => null,
  };
}

final projectNoteRepositoryProvider = Provider<ProjectNoteRepository>(
  (ref) => ApiProjectNoteRepository(ref.watch(apiClientProvider)),
);
