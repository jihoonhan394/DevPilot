import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/core/auth/jwt_subject.dart';
import 'package:devpilot_app/core/storage/key_value_store.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// 이 브라우저에서 안내를 닫았는지 (docs/02 SCR-TODAY "처음 안내 카드", BL-CLI-49).
const firstRunDismissedKeyPrefix = 'devpilot.today.firstRun.dismissed.';

/// 안내 카드를 닫았는지. 저장이 막힌 브라우저에서는 늘 `false`라 매번 보이지만, 화면은 그대로 동작한다(docs/02 §8.3).
final firstRunDismissedProvider = NotifierProvider<FirstRunDismissed, bool>(FirstRunDismissed.new);

final class FirstRunDismissed extends Notifier<bool> {
  String? _storageKey;

  @override
  bool build() {
    final accessToken = ref.watch(authStateProvider.select((state) => state.accessToken));
    final subject = accessToken == null ? null : jwtSubject(accessToken);
    _storageKey = subject == null ? null : '$firstRunDismissedKeyPrefix$subject';
    final key = _storageKey;
    return key != null && ref.read(keyValueStoreProvider).read(key) == '1';
  }

  /// 다시 보이지 않게 한다. 되돌리는 버튼은 두지 않는다 — 한 번 읽으면 끝나는 안내다.
  void dismiss() {
    final key = _storageKey;
    if (key != null) {
      ref.read(keyValueStoreProvider).write(key, '1');
    }
    state = true;
  }
}

/// SCR-TODAY 맨 위 안내 (docs/02 SCR-TODAY, BL-CLI-49).
///
/// <b>어디에 무엇이 있는지가 아니라 무엇부터 하는지를 말한다</b> — 목적지 이름만 늘어놓으면 처음 쓰는 사람은 시작점을
/// 찾지 못한다. 생성 전 레이아웃에서만 보이고, 닫으면 이 브라우저에서 다시 뜨지 않는다.
class TodayFirstRunCard extends ConsumerWidget {
  const TodayFirstRunCard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (ref.watch(firstRunDismissedProvider)) {
      return const SizedBox.shrink();
    }
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Card(
      key: const Key('today.firstRun'),
      margin: const EdgeInsets.only(bottom: AppSpacing.lg),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Semantics(
                    header: true,
                    child: Text(l10n.todayFirstRunTitle, style: theme.textTheme.titleSmall),
                  ),
                ),
                IconButton(
                  key: const Key('today.firstRun.dismiss'),
                  icon: const Icon(Icons.close, size: 18),
                  tooltip: l10n.todayFirstRunDismiss,
                  onPressed: ref.read(firstRunDismissedProvider.notifier).dismiss,
                ),
              ],
            ),
            Text(l10n.todayFirstRunLead),
            const SizedBox(height: AppSpacing.sm),
            for (final (index, step) in [
              l10n.todayFirstRunStep1,
              l10n.todayFirstRunStep2,
              l10n.todayFirstRunStep3,
            ].indexed)
              Padding(
                padding: const EdgeInsets.only(bottom: AppSpacing.xs),
                child: Text('${index + 1}. $step', style: theme.textTheme.bodySmall),
              ),
          ],
        ),
      ),
    );
  }
}
