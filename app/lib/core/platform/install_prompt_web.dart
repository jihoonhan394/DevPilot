import 'dart:js_interop';

import 'package:devpilot_app/core/platform/install_prompt.dart';
import 'package:web/web.dart' as web;

/// Web build: reads the `beforeinstallprompt` event kept by `web/pwa_install.js`, which runs
/// before the app so an early event is not lost (docs/02 §8.2).
InstallPrompt createPlatformInstallPrompt() => _WebInstallPrompt();

/// `window.devpilotInstallEvent`, set by `web/pwa_install.js`.
@JS('devpilotInstallEvent')
external _BeforeInstallPromptEvent? get _keptEvent;

@JS('devpilotInstallEvent')
external set _keptEvent(_BeforeInstallPromptEvent? value);

extension type _BeforeInstallPromptEvent._(JSObject _) implements JSObject {
  external JSPromise<JSAny?> prompt();

  external JSPromise<_InstallChoice> get userChoice;
}

extension type _InstallChoice._(JSObject _) implements JSObject {
  external String get outcome;
}

/// `navigator.standalone` exists only in iOS Safari.
extension on web.Navigator {
  @JS('standalone')
  external JSBoolean? get iosStandalone;
}

final class _WebInstallPrompt implements InstallPrompt {
  /// Event name dispatched by `web/pwa_install.js`.
  static const _changeEvent = 'devpilot-install-change';

  @override
  InstallEnvironment get environment {
    final navigator = web.window.navigator;
    final standalone = web.window.matchMedia('(display-mode: standalone)').matches;
    if (standalone || (navigator.iosStandalone?.toDart ?? false)) {
      return InstallEnvironment.installed;
    }
    if (_keptEvent != null) {
      return InstallEnvironment.promptAvailable;
    }
    final agent = navigator.userAgent;
    final iPadDesktopMode = agent.contains('Macintosh') && navigator.maxTouchPoints > 1;
    if (RegExp('iPhone|iPad|iPod').hasMatch(agent) || iPadDesktopMode) {
      return InstallEnvironment.ios;
    }
    return InstallEnvironment.unsupported;
  }

  @override
  Stream<void> get changes =>
      const web.EventStreamProvider<web.Event>(_changeEvent).forTarget(web.window).map((_) {});

  @override
  Future<bool> prompt() async {
    final event = _keptEvent;
    if (event == null) {
      return false;
    }
    // The browser allows one prompt per event.
    _keptEvent = null;
    await event.prompt().toDart;
    final choice = await event.userChoice.toDart;
    web.window.dispatchEvent(web.Event(_changeEvent));
    return choice.outcome == 'accepted';
  }
}
