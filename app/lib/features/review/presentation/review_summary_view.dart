import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/clock.dart';
import 'package:devpilot_app/core/time/session_time_rules.dart';
import 'package:devpilot_app/core/widgets/complete_session_sheet.dart';
import 'package:devpilot_app/features/review/presentation/review_explain_list.dart';
import 'package:devpilot_app/features/review/presentation/review_labels.dart';
import 'package:devpilot_app/features/review/presentation/review_session_controller.dart';
import 'package:devpilot_app/features/review/presentation/review_session_state.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// ④ End: "{n}장 복습했어요", cards per final rating (text labels) and "완료 기록" when this screen
/// started the session, otherwise "Today로" (docs/02 SCR-REVIEW-SESSION ④).
class ReviewSummaryView extends ConsumerWidget {
  const ReviewSummaryView({super.key, required this.state, required this.taskId});

  final ReviewSessionState state;
  final String? taskId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final canRecord = state.sessionId != null && !state.recorded;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Semantics(
          header: true,
          child: Text(
            l10n.reviewSummaryTitle(state.answeredCount),
            key: const Key('review.summary.title'),
            style: textTheme.headlineSmall,
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        for (final entry in state.ratingCounts.entries)
          Text(l10n.reviewSummaryRatingCount(entry.key.label(l10n), entry.value)),
        const SizedBox(height: AppSpacing.lg),
        ReviewExplainList(cards: state.struggled),
        const SizedBox(height: AppSpacing.lg),
        if (canRecord)
          FilledButton(
            key: const Key('review.summary.completeButton'),
            onPressed: () => openReviewRecordSheet(context, taskId, partial: false),
            child: Text(l10n.reviewSummaryComplete),
          )
        else
          FilledButton(
            key: const Key('review.summary.todayButton'),
            onPressed: () => context.go(AppRoutes.today),
            child: Text(l10n.reviewSummaryToToday),
          ),
      ],
    );
  }
}

/// The completion sheet for this screen's session ("완료 기록", or the partial record when
/// leaving). A full record goes to Today afterwards. Returns whether the session was recorded.
Future<bool> openReviewRecordSheet(
  BuildContext context,
  String? taskId, {
  required bool partial,
}) async {
  final l10n = AppLocalizations.of(context);
  final container = ProviderScope.containerOf(context, listen: false);
  final provider = reviewSessionControllerProvider(taskId);
  final startedAt = container.read(provider).value?.sessionStartedAt;
  if (startedAt == null) {
    return true;
  }
  // Keeps the screen's state alive while the sheet is open, even if the route is leaving.
  final keepAlive = container.listen(provider, (_, _) {});
  try {
    final now = container.read(clockProvider)();
    await showCompleteSessionSheet(
      context,
      title: partial ? l10n.todayPartialTitle : l10n.todayCompleteSheetTitle,
      initialMinutes: SessionTimeRules.defaultActualMinutes(startedAt, now),
      maxMinutes: SessionTimeRules.maxActualMinutes(startedAt, now),
      // 복습 세션은 묻지 않는다 — 안다/모른다는 카드마다 이미 답했다.
      onSubmit: (minutes, reflection, _, {understood}) => container
          .read(provider.notifier)
          .record(actualMinutes: minutes, reflection: reflection, partial: partial),
    );
    final recorded = container.read(provider).value?.recorded ?? false;
    if (recorded && !partial && context.mounted) {
      context.go(AppRoutes.today);
    }
    return recorded;
  } finally {
    keepAlive.close();
  }
}
