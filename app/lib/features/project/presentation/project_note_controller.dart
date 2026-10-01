import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/project/data/project_note_models.dart';
import 'package:devpilot_app/features/project/data/project_note_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// 기록 1건 (수정 화면 진입, docs/05 §19.11). 없는 id면 404다.
final projectNoteProvider =
    FutureProvider.family<SideProjectNoteView, ({String sideProjectId, String noteId})>(
      (ref, key) =>
          ref.watch(projectNoteRepositoryProvider).fetchNote(key.sideProjectId, key.noteId),
    );

/// 저장 결과. 화면은 이 값으로 토스트와 이동을 정한다.
sealed class NoteSaveOutcome {
  const NoteSaveOutcome();
}

final class NoteSaved extends NoteSaveOutcome {
  const NoteSaved(this.note);

  final SideProjectNoteView note;
}

/// 409: 다른 곳에서 바뀌었다. 최신 값으로 폼을 다시 채우고 <b>입력은 유지한다</b> (docs/02 §3.16).
final class NoteReloaded extends NoteSaveOutcome {
  const NoteReloaded(this.latest);

  final SideProjectNoteView latest;
}

final class NoteSaveFailed extends NoteSaveOutcome {
  const NoteSaveFailed(this.error);

  final ApiException error;
}

/// 저장 (생성 201 / 수정 200). 409면 최신 값을 읽어 돌려준다 — 덮어쓰지 않는다.
Future<NoteSaveOutcome> saveProjectNote(
  WidgetRef ref, {
  required String sideProjectId,
  required SideProjectNoteCreateRequest? create,
  required ({String noteId, SideProjectNotePatchRequest request})? patch,
  required IdempotencyKey idempotencyKey,
}) async {
  final repository = ref.read(projectNoteRepositoryProvider);
  try {
    if (create != null) {
      return NoteSaved(
        await repository.createNote(sideProjectId, create, idempotencyKey: idempotencyKey),
      );
    }
    final edit = patch!;
    return NoteSaved(await repository.updateNote(sideProjectId, edit.noteId, edit.request));
  } on ApiException catch (error) {
    if (error.code == ApiErrorCode.concurrentModification && patch != null) {
      return NoteReloaded(await repository.fetchNote(sideProjectId, patch.noteId));
    }
    return NoteSaveFailed(error);
  }
}
