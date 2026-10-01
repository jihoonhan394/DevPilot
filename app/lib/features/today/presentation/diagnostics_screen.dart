import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/onboarding/data/onboarding_models.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/today/presentation/diagnostic_actions.dart';
import 'package:devpilot_app/features/today/presentation/diagnostic_skips.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-DIAGNOSTICS: every diagnostic suggestion, solved or skipped one by one (docs/02 §3.5). For a
/// user who chose the short diagnostic in onboarding this sets where each category starts.
class DiagnosticsScreen extends ConsumerWidget {
  const DiagnosticsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final suggestions = ref.watch(diagnosticSuggestionsProvider);
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.diagnosticsTitle))),
      body: ScreenBody(
        child: suggestions.when(
          loading: () => const SkeletonList(count: 2, lines: 4),
          error: (error, _) => ErrorView(
            error: error,
            onRetry: () => ref.invalidate(diagnosticSuggestionsProvider),
          ),
          data: (items) => items.isEmpty
              ? EmptyState(
                  icon: Icons.fact_check_outlined,
                  message: l10n.diagnosticsEmpty,
                  actionLabel: l10n.reviewSummaryToToday,
                  actionKey: const Key('diagnostics.todayButton'),
                  onAction: () => context.go(AppRoutes.today),
                )
              : _SuggestionList(items: items),
        ),
      ),
    );
  }
}

class _SuggestionList extends ConsumerWidget {
  const _SuggestionList({required this.items});

  final List<DiagnosticSuggestionView> items;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final skipped = ref.watch(diagnosticSkipsProvider);
    final open = [
      for (final item in items)
        if (!skipped.contains(item.challengeId)) item,
    ];
    final hidden = [
      for (final item in items)
        if (skipped.contains(item.challengeId)) item,
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(isDiagnosticMode(items) ? l10n.diagnosticsHelpDiagnosticMode : l10n.diagnosticsHelp),
        const SizedBox(height: AppSpacing.lg),
        for (final item in open) ...[
          DiagnosticCard(suggestion: item),
          const SizedBox(height: AppSpacing.md),
        ],
        if (hidden.isNotEmpty)
          ExpansionTile(
            key: const Key('diagnostics.skippedSection'),
            tilePadding: EdgeInsets.zero,
            title: Text(l10n.diagnosticsSkipped),
            children: [
              for (final item in hidden)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(item.title),
                  subtitle: Text(item.category.label(l10n)),
                  trailing: TextButton(
                    key: Key('diagnostics.restore.${item.challengeId}'),
                    onPressed: () =>
                        ref.read(diagnosticSkipsProvider.notifier).restore(item.challengeId),
                    child: Text(l10n.diagnosticsRestore),
                  ),
                ),
            ],
          ),
      ],
    );
  }
}

/// One suggestion: category, the claimed level (self-assessment mode only), skill, challenge,
/// difficulty and time, "풀기" and "건너뛰기". Solving works without AI; submitting does not.
class DiagnosticCard extends ConsumerWidget {
  const DiagnosticCard({super.key, required this.suggestion});

  final DiagnosticSuggestionView suggestion;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final claimed = suggestion.selfAssessedLevel;
    final minutes = suggestion.estimatedMinutes;
    final starting = ref.watch(diagnosticStartProvider) != null;
    final id = suggestion.challengeId;
    return Card(
      key: Key('diagnostics.card.$id'),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(suggestion.category.label(l10n), style: textTheme.titleMedium),
            if (claimed != null) Text(l10n.diagnosticsClaimed(skillLevelLabel(claimed, l10n))),
            Text(suggestion.skill.name, style: textTheme.bodySmall),
            const SizedBox(height: AppSpacing.xs),
            Text(suggestion.title),
            Text(
              [
                l10n.diagnosticsDifficulty(suggestion.difficulty),
                if (minutes != null) l10n.todayMainEstimated(formatMinutes(minutes, l10n)),
              ].join(' · '),
              style: textTheme.bodySmall,
            ),
            if (!ref.watch(aiStatusProvider).allowsAi)
              Text(l10n.diagnosticsAiNote, key: Key('diagnostics.aiNote.$id')),
            const SizedBox(height: AppSpacing.sm),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  key: Key('diagnostics.skip.$id'),
                  onPressed: () => ref.read(diagnosticSkipsProvider.notifier).skip(id),
                  child: Text(l10n.diagnosticsSkip),
                ),
                const SizedBox(width: AppSpacing.sm),
                FilledButton.tonal(
                  key: Key('diagnostics.solve.$id'),
                  onPressed: starting ? null : () => startDiagnostic(context, ref, id),
                  child: Text(l10n.diagnosticsSolve),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
