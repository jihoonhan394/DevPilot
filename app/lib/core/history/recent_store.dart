import 'dart:convert';

import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/core/auth/jwt_subject.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// What kind of thing was opened. The label comes from l10n, not from here.
enum RecentKind { rubberDuck, readCode, challenge, lesson, term, tip }

/// One visit: enough to draw a line and go back to it.
@immutable
final class RecentEntry {
  const RecentEntry({
    required this.kind,
    required this.route,
    required this.title,
    required this.visitedAt,
  });

  final RecentKind kind;

  /// The route to push. Two visits to the same route count as one (the newest wins).
  final String route;
  final String title;
  final DateTime visitedAt;

  Map<String, Object?> toJson() => {
    'kind': kind.name,
    'route': route,
    'title': title,
    'visitedAt': visitedAt.toUtc().toIso8601String(),
  };

  static RecentEntry? tryFrom(Object? json) {
    if (json is! Map<String, Object?>) {
      return null;
    }
    final kind = RecentKind.values.where((value) => value.name == json['kind']).firstOrNull;
    final route = json['route'];
    final title = json['title'];
    final visitedAt = DateTime.tryParse(json['visitedAt'] as String? ?? '');
    if (kind == null || route is! String || title is! String || visitedAt == null) {
      return null;
    }
    return RecentEntry(kind: kind, route: route, title: title, visitedAt: visitedAt);
  }
}

/// The screens this browser opened recently (docs/02 §3.19).
///
/// The server has no "what did I do" list: rubber duck sessions and code readings can only be
/// fetched one by one, and a finished task leaves Today for good. Without this a reader cannot get
/// back to a conversation they just had or a file they just read — the reachability gap of
/// `DevPilot-ops/reachability-audit-2026-09-29.md`.
///
/// Per browser and per user, never synced, and never the source of truth: it only remembers where
/// something was. Every read tolerates a missing or broken value.
final class RecentStore {
  RecentStore(this._store, this._subject);

  static const prefix = 'devpilot.recent.';

  /// Older entries are dropped. Long enough to cover a few days of study, short enough to scan.
  static const limit = 20;

  final KeyValueStore _store;

  /// `sub` of the access token; without it nothing is remembered.
  final String? _subject;

  String? get _key {
    final subject = _subject;
    return subject == null ? null : '$prefix$subject';
  }

  List<RecentEntry> read() {
    final key = _key;
    final raw = key == null ? null : _store.read(key);
    if (raw == null) {
      return const [];
    }
    try {
      final Object? json = jsonDecode(raw);
      if (json is! List) {
        return const [];
      }
      return [
        for (final item in json) ?RecentEntry.tryFrom(item),
      ];
    } on FormatException {
      return const [];
    }
  }

  /// Puts [entry] on top, drops an older visit to the same route, and keeps at most [limit].
  List<RecentEntry> add(RecentEntry entry) {
    final key = _key;
    if (key == null) {
      return const [];
    }
    final kept = [
      entry,
      ...read().where((existing) => existing.route != entry.route),
    ].take(limit).toList();
    _store.write(key, jsonEncode([for (final item in kept) item.toJson()]));
    return kept;
  }

  void clear() {
    final key = _key;
    if (key != null) {
      _store.remove(key);
    }
  }
}

final recentStoreProvider = Provider<RecentStore>((ref) {
  final accessToken = ref.watch(authStateProvider.select((state) => state.accessToken));
  return RecentStore(
    ref.watch(keyValueStoreProvider),
    accessToken == null ? null : jwtSubject(accessToken),
  );
});

/// The list as the screen reads it. `add` writes through this so the screen rebuilds.
final recentEntriesProvider = NotifierProvider<RecentEntriesNotifier, List<RecentEntry>>(
  RecentEntriesNotifier.new,
);

final class RecentEntriesNotifier extends Notifier<List<RecentEntry>> {
  @override
  List<RecentEntry> build() => ref.watch(recentStoreProvider).read();

  void add(RecentEntry entry) {
    state = ref.read(recentStoreProvider).add(entry);
  }
}
