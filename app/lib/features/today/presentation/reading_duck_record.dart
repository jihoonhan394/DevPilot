import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_local_store.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Whether this device saw the READ_CODE task [taskId] explained to the end: the session the
/// rubber duck remembered for it (`devpilot.rubberduck.task.<taskId>`) is COMPLETED. Only then
/// does Today offer "완료" (RC-1, docs/02 SCR-TODAY "READ_CODE 완료 확인"); the server checks it
/// again. Any failure counts as not confirmed.
final readingDuckDoneProvider = FutureProvider.autoDispose.family<bool, String>((
  ref,
  taskId,
) async {
  final sessionId = ref.watch(rubberDuckLocalStoreProvider).readTaskSession(taskId);
  if (sessionId == null) {
    return false;
  }
  try {
    final session = await ref.read(rubberDuckRepositoryProvider).fetchSession(sessionId);
    return session.status == RubberDuckStatus.completed;
  } on ApiException {
    return false;
  }
});
