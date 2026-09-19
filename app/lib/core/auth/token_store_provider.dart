import 'package:devpilot_app/core/auth/platform_token_store_stub.dart'
    if (dart.library.js_interop) 'package:devpilot_app/core/auth/platform_token_store_web.dart';
import 'package:devpilot_app/core/auth/token_store.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// sessionStorage on the web, memory elsewhere. Tests override it with [MemoryTokenStore].
final tokenStoreProvider = Provider<TokenStore>((ref) => createPlatformTokenStore());
