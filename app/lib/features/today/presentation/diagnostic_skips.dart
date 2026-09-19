import 'dart:convert';

import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/core/auth/jwt_subject.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:devpilot_app/features/onboarding/data/diagnostic_repository.dart';
import 'package:devpilot_app/features/onboarding/data/onboarding_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Diagnostic challenges the user chose to skip (docs/02 SCR-DIAGNOSTICS "건너뛰기 저장"). There
/// is no server API: the ids live in `localStorage` `devpilot.diagnostics.skipped.<externalAuthId>`.
/// Without a readable subject the list stays in memory.
final class DiagnosticSkips extends Notifier<Set<String>> {
  static const storageKeyPrefix = 'devpilot.diagnostics.skipped.';

  String? _storageKey;

  @override
  Set<String> build() {
    final accessToken = ref.watch(authStateProvider.select((authState) => authState.accessToken));
    final subject = accessToken == null ? null : jwtSubject(accessToken);
    _storageKey = subject == null ? null : '$storageKeyPrefix$subject';
    return _load();
  }

  void skip(String challengeId) => _save({...state, challengeId});

  void restore(String challengeId) => _save({...state}..remove(challengeId));

  void _save(Set<String> skipped) {
    state = skipped;
    final key = _storageKey;
    if (key != null) {
      ref.read(keyValueStoreProvider).write(key, jsonEncode(skipped.toList()));
    }
  }

  Set<String> _load() {
    final key = _storageKey;
    final raw = key == null ? null : ref.read(keyValueStoreProvider).read(key);
    if (raw == null) {
      return const {};
    }
    try {
      final Object? json = jsonDecode(raw);
      return json is List<Object?>
          ? {
              for (final id in json)
                if (id is String) id,
            }
          : const {};
    } on FormatException {
      return const {};
    }
  }
}

final diagnosticSkipsProvider = NotifierProvider<DiagnosticSkips, Set<String>>(
  DiagnosticSkips.new,
);

/// `GET /diagnostics/suggestions`, read each time a screen that shows them opens.
final diagnosticSuggestionsProvider = FutureProvider.autoDispose<List<DiagnosticSuggestionView>>(
  (ref) => ref.watch(diagnosticRepositoryProvider).fetchSuggestions(),
);

/// Diagnostic mode (onboarding `runDiagnostic = true`): no suggestion carries a self-assessed
/// level (docs/02 SCR-DIAGNOSTICS "모드 판정").
bool isDiagnosticMode(List<DiagnosticSuggestionView> suggestions) =>
    suggestions.every((suggestion) => suggestion.selfAssessedLevel == null);
