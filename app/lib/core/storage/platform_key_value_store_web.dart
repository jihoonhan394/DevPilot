import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:web/web.dart' as web;

/// Web build: `localStorage`. Failures are ignored so a blocked storage never breaks a screen.
KeyValueStore createPlatformKeyValueStore() => _LocalStorageKeyValueStore();

final class _LocalStorageKeyValueStore implements KeyValueStore {
  @override
  String? read(String key) {
    try {
      return web.window.localStorage.getItem(key);
    } on Object {
      // Storage disabled or blocked: behave as if nothing was saved.
      return null;
    }
  }

  @override
  void write(String key, String value) {
    try {
      web.window.localStorage.setItem(key, value);
    } on Object {
      // Quota exceeded or storage blocked: the value is simply not persisted.
      return;
    }
  }

  @override
  void remove(String key) {
    try {
      web.window.localStorage.removeItem(key);
    } on Object {
      // Storage blocked: nothing was persisted, so there is nothing to remove.
      return;
    }
  }
}
