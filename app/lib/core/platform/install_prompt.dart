import 'package:devpilot_app/core/platform/install_prompt_stub.dart'
    if (dart.library.js_interop) 'package:devpilot_app/core/platform/install_prompt_web.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// How this browser can add the app to the home screen (docs/02 §8.2 `InstallPrompt`).
enum InstallEnvironment {
  /// Android Chrome, desktop Chrome and Edge: the kept `beforeinstallprompt` event can be shown.
  promptAvailable,

  /// iOS and iPadOS Safari outside the installed app: the steps are shown instead.
  ios,

  /// Running as the installed app (`display-mode: standalone`).
  installed,

  /// Anything else: only the general hint in Settings.
  unsupported,
}

/// Browser side of the install guide.
abstract interface class InstallPrompt {
  InstallEnvironment get environment;

  /// Fires when the browser offers the prompt or the app gets installed.
  Stream<void> get changes;

  /// Shows the browser's install dialog. True when the user accepted.
  Future<bool> prompt();
}

final installPromptProvider = Provider<InstallPrompt>((ref) => createPlatformInstallPrompt());

/// The current environment, updated when the browser offers the prompt later.
final installEnvironmentProvider = StreamProvider<InstallEnvironment>((ref) async* {
  final installPrompt = ref.watch(installPromptProvider);
  yield installPrompt.environment;
  await for (final _ in installPrompt.changes) {
    yield installPrompt.environment;
  }
});
