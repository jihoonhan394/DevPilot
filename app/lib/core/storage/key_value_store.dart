import 'package:devpilot_app/core/storage/platform_key_value_store_stub.dart'
    if (dart.library.js_interop) 'package:devpilot_app/core/storage/platform_key_value_store_web.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Small per-browser preferences and drafts in `localStorage` (docs/02 SCR-ONBOARDING, SCR-SKILL-TREE).
///
/// Only what the user typed or chose is stored here, never API data (docs/02 §8.3). Every call
/// swallows storage failures (private mode, quota): the app must work without it.
abstract interface class KeyValueStore {
  String? read(String key);

  void write(String key, String value);

  void remove(String key);
}

/// In-memory store for non-web platforms and tests.
final class MemoryKeyValueStore implements KeyValueStore {
  MemoryKeyValueStore([Map<String, String>? initial]) : _values = {...?initial};

  final Map<String, String> _values;

  @override
  String? read(String key) => _values[key];

  @override
  void write(String key, String value) => _values[key] = value;

  @override
  void remove(String key) => _values.remove(key);
}

final keyValueStoreProvider = Provider<KeyValueStore>((ref) => createPlatformKeyValueStore());
