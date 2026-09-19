import 'dart:convert';

import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:flutter/foundation.dart';

/// Answer and code typed into the submission form, kept per attempt in `localStorage`
/// `devpilot.attempt.draft.<attemptId>` (docs/02 SCR-TRAINING-ATTEMPT "입력 보관"). Only what the
/// user typed is stored; a failed read is an empty draft.
@immutable
final class AttemptDraft {
  const AttemptDraft({this.answerText = '', this.code = '', this.language});

  static const storageKeyPrefix = 'devpilot.attempt.draft.';

  final String answerText;
  final String code;
  final CodeLanguage? language;

  bool get isEmpty => answerText.isEmpty && code.isEmpty;

  static AttemptDraft read(KeyValueStore store, String attemptId) {
    final raw = store.read('$storageKeyPrefix$attemptId');
    if (raw == null) {
      return const AttemptDraft();
    }
    try {
      final Object? json = jsonDecode(raw);
      if (json is! Map<String, Object?>) {
        return const AttemptDraft();
      }
      final language = json['language'];
      return AttemptDraft(
        answerText: json['answerText'] is String ? json['answerText']! as String : '',
        code: json['code'] is String ? json['code']! as String : '',
        language: CodeLanguage.known.where((value) => value.wireName == language).firstOrNull,
      );
    } on FormatException {
      return const AttemptDraft();
    }
  }

  void write(KeyValueStore store, String attemptId) {
    if (isEmpty) {
      clear(store, attemptId);
      return;
    }
    store.write(
      '$storageKeyPrefix$attemptId',
      jsonEncode({'answerText': answerText, 'code': code, 'language': language?.wireName}),
    );
  }

  static void clear(KeyValueStore store, String attemptId) =>
      store.remove('$storageKeyPrefix$attemptId');
}
