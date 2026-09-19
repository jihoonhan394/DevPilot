import 'package:devpilot_app/core/platform/install_prompt.dart';

/// Non-web builds and tests: there is nothing to install.
InstallPrompt createPlatformInstallPrompt() => const _NoInstallPrompt();

final class _NoInstallPrompt implements InstallPrompt {
  const _NoInstallPrompt();

  @override
  InstallEnvironment get environment => InstallEnvironment.unsupported;

  @override
  Stream<void> get changes => const Stream.empty();

  @override
  Future<bool> prompt() async => false;
}
