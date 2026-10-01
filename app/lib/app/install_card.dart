import 'dart:async';

import 'package:devpilot_app/core/platform/install_prompt.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/widgets/install_guide_sheet.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// When the install card may show (docs/02 §8.2): from the second plan-day with a visit, and not
/// within 30 days of "닫기". Only what the user did is stored, never API data.
final class InstallCardPolicy {
  InstallCardPolicy(this._store);

  static const firstVisitKey = 'devpilot.install.firstVisitDay';
  static const dismissedKey = 'devpilot.install.dismissedAt';
  static const quietDays = 30;

  final KeyValueStore _store;

  /// Records the first visited plan-day, then answers whether [today] may show the card.
  bool shouldShow(LocalDate today) {
    final firstVisit = LocalDate.tryParse(_store.read(firstVisitKey));
    if (firstVisit == null) {
      _store.write(firstVisitKey, today.toIso());
      return false;
    }
    if (!today.isAfter(firstVisit)) {
      return false;
    }
    final dismissed = LocalDate.tryParse(_store.read(dismissedKey));
    return dismissed == null || dismissed.daysUntil(today) >= quietDays;
  }

  void dismiss(LocalDate today) => _store.write(dismissedKey, today.toIso());
}

/// Install card at the bottom of SCR-TODAY: "설치" where the browser offers a prompt, "방법 보기"
/// on iOS Safari, nothing when installed or unsupported.
class InstallCard extends ConsumerStatefulWidget {
  const InstallCard({super.key});

  @override
  ConsumerState<InstallCard> createState() => _InstallCardState();
}

class _InstallCardState extends ConsumerState<InstallCard> {
  late final _policy = InstallCardPolicy(ref.read(keyValueStoreProvider));
  late bool _eligible = _policy.shouldShow(ref.read(userTodayProvider));

  void _dismiss() {
    _policy.dismiss(ref.read(userTodayProvider));
    setState(() => _eligible = false);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final environment =
        ref.watch(installEnvironmentProvider).value ?? ref.read(installPromptProvider).environment;
    final canInstall = environment == InstallEnvironment.promptAvailable;
    if (!_eligible || !(canInstall || environment == InstallEnvironment.ios)) {
      return const SizedBox.shrink();
    }
    return Card(
      key: const Key('install.card'),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.pwaInstallCard),
            const SizedBox(height: AppSpacing.md),
            Wrap(
              alignment: WrapAlignment.end,
              spacing: AppSpacing.sm,
              children: [
                TextButton(
                  key: const Key('install.dismissButton'),
                  onPressed: _dismiss,
                  child: Text(l10n.pwaInstallDismiss),
                ),
                OutlinedButton(
                  key: const Key('install.cardButton'),
                  onPressed: canInstall
                      ? () => unawaited(ref.read(installPromptProvider).prompt())
                      : () => unawaited(showInstallGuideSheet(context)),
                  child: Text(canInstall ? l10n.pwaInstallButton : l10n.pwaInstallHowto),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
