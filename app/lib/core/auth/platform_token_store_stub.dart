import 'package:devpilot_app/core/auth/token_store.dart';

/// Non-web fallback (VM tests). The session lives only in memory.
TokenStore createPlatformTokenStore() => MemoryTokenStore();
