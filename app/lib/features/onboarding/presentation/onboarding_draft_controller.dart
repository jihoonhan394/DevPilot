import 'dart:convert';

import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/core/auth/jwt_subject.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_draft.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// One change to the draft, applied by [OnboardingDraftController.edit].
typedef OnboardingDraftEdit = OnboardingDraft Function(OnboardingDraft draft);

/// Onboarding inputs of steps 1~4 (`onboardingDraftProvider`, docs/02 SCR-ONBOARDING).
///
/// Mirrored to `localStorage` under `devpilot.onboarding.draft.<externalAuthId>` so a reload keeps
/// them. Storage failures are ignored (the draft then lives in memory only).
final class OnboardingDraftController extends Notifier<OnboardingDraft> {
  static const storageKeyPrefix = 'devpilot.onboarding.draft.';

  String? _storageKey;

  @override
  OnboardingDraft build() {
    final accessToken = ref.watch(authStateProvider.select((authState) => authState.accessToken));
    final subject = accessToken == null ? null : jwtSubject(accessToken);
    _storageKey = subject == null ? null : '$storageKeyPrefix$subject';
    return _load() ?? const OnboardingDraft();
  }

  /// Applies [change] and saves the result.
  void edit(OnboardingDraftEdit change) {
    state = change(state);
    final key = _storageKey;
    if (key != null) {
      ref.read(keyValueStoreProvider).write(key, jsonEncode(state.toJson()));
    }
  }

  /// Drops the draft after a successful submit.
  void clear() {
    final key = _storageKey;
    if (key != null) {
      ref.read(keyValueStoreProvider).remove(key);
    }
    state = const OnboardingDraft();
  }

  OnboardingDraft? _load() {
    final key = _storageKey;
    if (key == null) {
      return null;
    }
    final store = ref.read(keyValueStoreProvider);
    final raw = store.read(key);
    if (raw == null) {
      return null;
    }
    try {
      final Object? json = jsonDecode(raw);
      return json is Map<String, Object?> ? OnboardingDraft.fromJson(json) : null;
    } on FormatException {
      // A corrupt draft is discarded; the user starts from the defaults.
      store.remove(key);
      return null;
    } on TypeError {
      // A draft written by an older build with other field types is discarded the same way.
      store.remove(key);
      return null;
    }
  }
}

final onboardingDraftProvider = NotifierProvider<OnboardingDraftController, OnboardingDraft>(
  OnboardingDraftController.new,
);
