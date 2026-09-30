import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/features/plan/presentation/buildable_providers.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-TODAY 아래의 한 줄 (docs/02 SCR-TODAY, SCR-BUILDABLE). 오늘 할 일을 밀어내지 않게 **카드가 아니라 한 줄**이다 —
/// 이건 오늘 해야 할 일이 아니라 지금 어디까지 왔는지를 알려 주는 자리다.
///
/// 아직 안 왔거나 실패하면 스스로 사라진다. 계획이 없는 사람에게 오류를 보여 줄 자리가 아니다.
///
/// 이동은 `go`다 — 이 라우터에서는 `push`가 주소를 바꾸지 않는다(SCR-SELF-ASSESSMENT와 같은 이유, docs/02 §3.14).
class BuildableLine extends ConsumerWidget {
  const BuildableLine({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final buildable = ref.watch(buildableProvider).value;
    if (buildable == null) {
      return const SizedBox.shrink();
    }
    final label = buildable.buildableStepCount == 0
        ? l10n.buildableTodayLinkNone
        : l10n.buildableTodayLink(buildable.buildableStepCount);
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.md),
      child: Align(
        alignment: Alignment.centerLeft,
        child: TextButton.icon(
          key: const Key('today.buildableLine'),
          onPressed: () => context.go(AppRoutes.buildable),
          icon: const Icon(Icons.construction, size: 18),
          label: Text(label, style: theme.textTheme.bodyMedium),
        ),
      ),
    );
  }
}
