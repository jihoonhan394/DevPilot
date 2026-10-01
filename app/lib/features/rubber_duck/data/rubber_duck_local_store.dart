import 'dart:convert';

import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/core/auth/jwt_subject.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// This browser's rubber duck records (docs/02 SCR-TODAY, SCR-RUBBER-DUCK). The server has no
/// session list, so the running session and the session that finished a `READ_CODE` task are
/// remembered here; drafts keep a typed explanation until it is sent. Every read tolerates a
/// missing or broken value.
final class RubberDuckLocalStore {
  RubberDuckLocalStore(this._store, this._subject);

  static const activePrefix = 'devpilot.rubberduck.active.';
  static const taskPrefix = 'devpilot.rubberduck.task.';
  static const draftPrefix = 'devpilot.rubberduck.draft.';

  final KeyValueStore _store;

  /// `sub` of the access token; without it the running session is not remembered.
  final String? _subject;

  /// The IN_PROGRESS session started on this device and the Today task it belongs to.
  ({String sessionId, String? taskId})? readActive() {
    final subject = _subject;
    final raw = subject == null ? null : _store.read('$activePrefix$subject');
    if (raw == null) {
      return null;
    }
    try {
      final Object? json = jsonDecode(raw);
      if (json is Map<String, Object?> && json['sessionId'] is String) {
        final taskId = json['taskId'];
        return (sessionId: json['sessionId']! as String, taskId: taskId is String ? taskId : null);
      }
    } on FormatException {
      // A broken value counts as none.
    }
    return null;
  }

  void writeActive(String sessionId, String? taskId) {
    final subject = _subject;
    if (subject != null) {
      _store.write('$activePrefix$subject', jsonEncode({'sessionId': sessionId, 'taskId': taskId}));
    }
  }

  void clearActive() {
    final subject = _subject;
    if (subject != null) {
      _store.remove('$activePrefix$subject');
    }
  }

  /// The session that explained the `READ_CODE` task [taskId] (RC-1 check on Today).
  String? readTaskSession(String taskId) => _store.read('$taskPrefix$taskId');

  void writeTaskSession(String taskId, String sessionId) =>
      _store.write('$taskPrefix$taskId', sessionId);

  String readDraft(String sessionId) => _store.read('$draftPrefix$sessionId') ?? '';

  void writeDraft(String sessionId, String text) => text.isEmpty
      ? _store.remove('$draftPrefix$sessionId')
      : _store.write('$draftPrefix$sessionId', text);
}

final rubberDuckLocalStoreProvider = Provider<RubberDuckLocalStore>((ref) {
  final accessToken = ref.watch(authStateProvider.select((authState) => authState.accessToken));
  return RubberDuckLocalStore(
    ref.watch(keyValueStoreProvider),
    accessToken == null ? null : jwtSubject(accessToken),
  );
});
