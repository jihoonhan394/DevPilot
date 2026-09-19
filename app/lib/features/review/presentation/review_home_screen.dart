import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/refreshing_bar.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/review/data/review_models.dart';
import 'package:devpilot_app/features/review/domain/review_card_progress.dart';
import 'package:devpilot_app/features/review/presentation/due_reviews_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-REVIEW-HOME: today's due cards, "복습 시작", card management and manual cards
/// (docs/02 §3.6).
class ReviewHomeScreen extends ConsumerStatefulWidget {
  const ReviewHomeScreen({super.key});

  @override
  ConsumerState<ReviewHomeScreen> createState() => _ReviewHomeScreenState();
}

class _ReviewHomeScreenState extends ConsumerState<ReviewHomeScreen> {
  @override
  void initState() {
    super.initState();
    // Entering the screen reads the due list again (docs/02 SCR-REVIEW-HOME "데이터").
    ref.invalidate(dueReviewsProvider);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final due = ref.watch(dueReviewsProvider);
    return Scaffold(
      appBar: AppBar(
        title: Semantics(header: true, child: Text(l10n.reviewHomeTitle)),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(2),
          child: RefreshingBar(visible: due.isRefreshing),
        ),
      ),
      body: due.when(
        loading: () => const ScreenBody(child: SkeletonList(count: 1, lines: 4)),
        error: (error, _) => ScreenBody(
          child: ErrorView(error: error, onRetry: () => ref.invalidate(dueReviewsProvider)),
        ),
        data: (response) => ScreenBody(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (response.items.isEmpty)
                EmptyState(
                  icon: Icons.style,
                  message: l10n.reviewHomeEmpty,
                  actionLabel: l10n.reviewHomeAdd,
                  actionKey: const Key('review.home.emptyAddButton'),
                  onAction: () => context.go(AppRoutes.reviewItemsNew),
                )
              else
                _DueSummaryCard(items: response.items),
              const SizedBox(height: AppSpacing.lg),
              _CardLinks(showAdd: response.items.isNotEmpty),
            ],
          ),
        ),
      ),
    );
  }
}

/// `DueSummaryCard`: count, estimated minutes, cards per skill and "복습 시작". The count past the
/// cap is not shown (U-3).
class _DueSummaryCard extends StatelessWidget {
  const _DueSummaryCard({required this.items});

  final List<DueReviewItemView> items;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final perSkill = <String, int>{};
    for (final item in items) {
      perSkill[item.skillName] = (perSkill[item.skillName] ?? 0) + 1;
    }
    return Card(
      key: const Key('review.home.dueCard'),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SectionTitle(l10n.reviewHomeDue),
            const SizedBox(height: AppSpacing.xs),
            Text(
              l10n.reviewHomeDueCount(
                items.length,
                formatMinutes(estimatedReviewMinutes(items.length), l10n),
              ),
              key: const Key('review.home.dueCount'),
              style: textTheme.headlineSmall,
            ),
            const SizedBox(height: AppSpacing.sm),
            Wrap(
              spacing: AppSpacing.md,
              runSpacing: AppSpacing.xs,
              children: [
                for (final entry in perSkill.entries)
                  Text(l10n.reviewHomeSkillCount(entry.key, entry.value)),
              ],
            ),
            const SizedBox(height: AppSpacing.lg),
            FilledButton(
              key: const Key('review.home.startButton'),
              onPressed: () => context.go(AppRoutes.reviewSession),
              child: Text(l10n.reviewHomeStart),
            ),
          ],
        ),
      ),
    );
  }
}

/// "카드 관리" and "카드 직접 추가". The empty state already offers adding, so the second row is
/// left out there (one accessible name per action).
class _CardLinks extends StatelessWidget {
  const _CardLinks({required this.showAdd});

  final bool showAdd;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      children: [
        ListTile(
          key: const Key('review.home.manageLink'),
          contentPadding: EdgeInsets.zero,
          title: Text(l10n.reviewHomeManage),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => context.go(AppRoutes.reviewItems),
        ),
        if (showAdd)
          ListTile(
            key: const Key('review.home.addLink'),
            contentPadding: EdgeInsets.zero,
            title: Text(l10n.reviewHomeAdd),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.go(AppRoutes.reviewItemsNew),
          ),
      ],
    );
  }
}
