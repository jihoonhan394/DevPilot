import 'package:devpilot_app/core/platform/page_reloader_stub.dart'
    if (dart.library.js_interop) 'package:devpilot_app/core/platform/page_reloader_web.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Reloads the page so a newer web build is loaded (docs/02 §5.1 `UNKNOWN_ENUM_VALUE`, §8.4).
typedef PageReloader = void Function();

final pageReloaderProvider = Provider<PageReloader>((ref) => reloadPage);
