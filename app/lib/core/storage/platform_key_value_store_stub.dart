import 'package:devpilot_app/core/storage/key_value_store.dart';

/// Non-web fallback (VM tests): values live only in memory.
KeyValueStore createPlatformKeyValueStore() => MemoryKeyValueStore();
