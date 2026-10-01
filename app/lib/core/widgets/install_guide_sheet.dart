import 'dart:async';

import 'package:devpilot_app/core/platform/install_prompt.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/form_modal.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// `InstallGuideSheet` (docs/02 §8.2, SCR-SETTINGS "앱 설치"): the install button, the iOS steps,
/// or the general hint, depending on the browser.
Future<void> showInstallGuideSheet(BuildContext context) =>
    showFormModal<void>(context, builder: (_) => const InstallGuideSheet());

/// SCR-SETTINGS "앱 설치 · 홈 화면에 추가하는 방법 >".
class InstallSettingsSection extends StatelessWidget {
  const InstallSettingsSection({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: AppSpacing.sm),
        Semantics(
          header: true,
          child: Text(l10n.settingsInstall, style: Theme.of(context).textTheme.titleMedium),
        ),
        ListTile(
          key: const Key('settings.installGuideTile'),
          contentPadding: EdgeInsets.zero,
          title: Text(l10n.settingsInstallHowto),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => unawaited(showInstallGuideSheet(context)),
        ),
      ],
    );
  }
}

class InstallGuideSheet extends ConsumerWidget {
  const InstallGuideSheet({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final environment =
        ref.watch(installEnvironmentProvider).value ?? ref.read(installPromptProvider).environment;
    final text = switch (environment) {
      InstallEnvironment.promptAvailable => l10n.pwaInstallCard,
      InstallEnvironment.ios => l10n.pwaInstallIosSteps,
      InstallEnvironment.installed => l10n.pwaInstallInstalled,
      InstallEnvironment.unsupported => l10n.pwaInstallGeneric,
    };
    return SingleChildScrollView(
      padding: const EdgeInsets.all(AppSpacing.xl),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Semantics(
            header: true,
            child: Text(l10n.settingsInstall, style: Theme.of(context).textTheme.titleLarge),
          ),
          const SizedBox(height: AppSpacing.md),
          Text(text, key: const Key('install.guideText')),
          if (environment == InstallEnvironment.promptAvailable) ...[
            const SizedBox(height: AppSpacing.xl),
            FilledButton(
              key: const Key('install.guideInstallButton'),
              onPressed: () {
                unawaited(ref.read(installPromptProvider).prompt());
                Navigator.of(context).pop();
              },
              child: Text(l10n.pwaInstallButton),
            ),
          ],
        ],
      ),
    );
  }
}
