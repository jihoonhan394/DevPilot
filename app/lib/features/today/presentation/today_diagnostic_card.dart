import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/features/today/presentation/diagnostic_actions.dart';
import 'package:devpilot_app/features/today/presentation/diagnostic_skips.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// `DiagnosticSuggestionCard` of SCR-TODAY before generation (docs/02 SCR-TODAY, S3): the first
/// suggestion the user did not skip. A failed read only hides the card.
class TodayDiagnosticCard extends ConsumerWidget {
  const TodayDiagnosticCard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final items = ref.watch(diagnosticSuggestionsProvider).value ?? const [];
    final skipped = ref.watch(diagnosticSkipsProvider);
    final open = [
      for (final item in items)
        if (!skipped.contains(item.challengeId)) item,
    ];
    final first = open.firstOrNull;
    if (first == null) {
      return const SizedBox.shrink();
    }
    final minutes = first.estimatedMinutes;
    final starting = ref.watch(diagnosticStartProvider) != null;
    return Card(
      key: const Key('today.diagnosticCard'),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.todayDiagnosticTitle, style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: AppSpacing.xs),
            Text(
              minutes == null
                  ? l10n.todayDiagnosticItem(first.category.label(l10n))
                  : l10n.todayDiagnosticItemMinutes(
                      first.category.label(l10n),
                      formatMinutes(minutes, l10n),
                    ),
            ),
            Text(
              isDiagnosticMode(items)
                  ? l10n.todayDiagnosticBodyDiagnosticMode(open.length)
                  : l10n.todayDiagnosticBody,
              style: Theme.of(context).textTheme.bodySmall,
            ),
            _CardActions(challengeId: first.challengeId, starting: starting),
          ],
        ),
      ),
    );
  }
}

/// "건너뛰기", "모두 보기", "풀기". 셋 다 text 버튼이다 — 이 카드는 곁가지 구역 안에 있고, 그 안에서 테두리나
/// 채운 버튼을 쓰면 오늘의 핵심과 같은 무게로 읽힌다 (docs/02 SCR-TODAY "천천히 봐도 되는 것").
class _CardActions extends ConsumerWidget {
  const _CardActions({required this.challengeId, required this.starting});

  final String challengeId;
  final bool starting;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    return Wrap(
      alignment: WrapAlignment.end,
      spacing: AppSpacing.sm,
      children: [
        TextButton(
          key: const Key('today.diagnosticSkipButton'),
          onPressed: () => ref.read(diagnosticSkipsProvider.notifier).skip(challengeId),
          child: Text(l10n.todayDiagnosticSkip),
        ),
        // 진단은 선택이다. 그만두면 모든 분야가 0에서 시작하는데, 전에는 그 사실도 수준을 직접 고르는 길도
        // 어디에도 없었다 — 설정 안에만 있었다 (ADR-068).
        TextButton(
          key: const Key('today.diagnosticPickLevelButton'),
          onPressed: () => context.go(AppRoutes.settingsSelfAssessment),
          child: Text(l10n.diagnosticsPickLevel),
        ),
        TextButton(
          key: const Key('today.diagnosticAllButton'),
          onPressed: () => context.go(AppRoutes.diagnostics),
          child: Text(l10n.todayDiagnosticAll),
        ),
        TextButton(
          key: const Key('today.diagnosticSolveButton'),
          onPressed: starting ? null : () => startDiagnostic(context, ref, challengeId),
          child: Text(l10n.todayDiagnosticSolve),
        ),
      ],
    );
  }
}
