import 'dart:async';

import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/presentation/review_card_view.dart';
import 'package:devpilot_app/features/review/presentation/review_session_controller.dart';
import 'package:devpilot_app/features/review/presentation/review_session_feedback.dart';
import 'package:devpilot_app/features/review/presentation/review_session_state.dart';
import 'package:devpilot_app/features/review/presentation/review_shortcuts.dart';
import 'package:devpilot_app/features/review/presentation/review_summary_view.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-REVIEW-SESSION: one card at a time → reveal → self rating (docs/02 §3.6, §4.3). A focus
/// screen without the navigation frame; [taskId] is the Today REVIEW task it came from.
class ReviewSessionScreen extends ConsumerWidget {
  const ReviewSessionScreen({super.key, this.taskId});

  final String? taskId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final session = ref.watch(reviewSessionControllerProvider(taskId));
    final data = session.value;
    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        leading: IconButton(
          key: const Key('review.session.closeButton'),
          tooltip: l10n.reviewSessionClose,
          icon: const Icon(Icons.close),
          onPressed: () => context.go(taskId == null ? AppRoutes.review : AppRoutes.today),
        ),
        actions: [
          if (data != null && data.cards.isNotEmpty) _ProgressLabel(state: data),
          const SizedBox(width: AppSpacing.lg),
        ],
        bottom: data == null || data.cards.isEmpty
            ? null
            : PreferredSize(
                preferredSize: const Size.fromHeight(4),
                child: SelectionContainer.disabled(
                  child: LinearProgressIndicator(
                    value: data.index / data.cards.length,
                    semanticsLabel: l10n.reviewSessionProgressSemantics(
                      _position(data),
                      data.cards.length,
                    ),
                  ),
                ),
              ),
      ),
      body: session.when(
        loading: () => const ScreenBody(child: SkeletonList(count: 1, lines: 6)),
        error: (error, _) => ScreenBody(
          child: ErrorView(
            error: error,
            onRetry: () => ref.invalidate(reviewSessionControllerProvider(taskId)),
          ),
        ),
        data: (state) => ScreenBody(
          child: _SessionBody(state: state, taskId: taskId),
        ),
      ),
    );
  }
}

int _position(ReviewSessionState state) => state.finished ? state.cards.length : state.index + 1;

/// "2 / 6" for sight, "카드 6장 중 2번째" for screen readers (A-6).
class _ProgressLabel extends StatelessWidget {
  const _ProgressLabel({required this.state});

  final ReviewSessionState state;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final position = _position(state);
    return Center(
      child: Semantics(
        label: l10n.reviewSessionProgressSemantics(position, state.cards.length),
        excludeSemantics: true,
        child: Text(
          l10n.reviewSessionProgress(position, state.cards.length),
          key: const Key('review.session.progress'),
        ),
      ),
    );
  }
}

class _SessionBody extends ConsumerWidget {
  const _SessionBody({required this.state, required this.taskId});

  final ReviewSessionState state;
  final String? taskId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final card = state.current;
    if (state.cards.isEmpty) {
      return EmptyState(
        icon: Icons.style,
        message: l10n.reviewHomeEmpty,
        actionLabel: l10n.reviewSummaryToToday,
        actionKey: const Key('review.session.todayButton'),
        onAction: () => context.go(AppRoutes.today),
      );
    }
    if (card == null) {
      return ReviewSummaryView(state: state, taskId: taskId);
    }
    final controller = ref.read(reviewSessionControllerProvider(taskId).notifier);
    void rate(ReviewRating rating) => unawaited(rateReviewCard(context, ref, taskId, rating));
    return ReviewShortcuts(
      card: card,
      enabled: !state.submitting,
      onHint: controller.showHint,
      onReveal: controller.reveal,
      onRate: rate,
      child: ReviewCardView(
        key: ValueKey(card.item.reviewItemId),
        card: card,
        submitting: state.submitting,
        onAnswerChanged: controller.setAnswer,
        onHint: controller.showHint,
        onShowAnswerFirst: controller.showAnswerFirst,
        onReveal: controller.reveal,
        onEvaluationChanged: controller.setEvaluation,
        onRate: rate,
      ),
    );
  }
}

/// go_router `onExit` of the session route: with answered cards and a session started here, the
/// partial record sheet comes first (docs/02 SCR-REVIEW-SESSION "✕ 또는 뒤로가기"). Closing the
/// sheet without recording stays on the screen. A sign-out always leaves.
Future<bool> confirmReviewSessionExit(BuildContext context, String? taskId) async {
  final container = ProviderScope.containerOf(context, listen: false);
  if (container.read(authStateProvider) is SignedOut) {
    return true;
  }
  final state = container.read(reviewSessionControllerProvider(taskId)).value;
  if (state == null || !state.needsRecordOnExit) {
    return true;
  }
  return openReviewRecordSheet(context, taskId, partial: true);
}
