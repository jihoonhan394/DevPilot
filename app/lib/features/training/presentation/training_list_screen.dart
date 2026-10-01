import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/cursor_list.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/ai_status_widgets.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/load_more_footer.dart';
import 'package:devpilot_app/core/widgets/refreshing_bar.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/core/widgets/skill_multi_picker.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:devpilot_app/features/training/data/challenge_models.dart';
import 'package:devpilot_app/features/training/presentation/challenge_tile.dart';
import 'package:devpilot_app/features/training/presentation/training_list_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-TRAINING-LIST: seed and own practice challenges for a skill filter (docs/02 §3.7). Making
/// a challenge with AI ships with S5 (`challenge_generate`), so its button is not shown.
class TrainingListScreen extends ConsumerWidget {
  const TrainingListScreen({super.key, this.skillId});

  /// `?skillId=`: the initial filter.
  final String? skillId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final provider = trainingListControllerProvider(skillId);
    final list = ref.watch(provider);
    return Scaffold(
      appBar: AppBar(
        title: Semantics(header: true, child: Text(l10n.trainingListTitle)),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(2),
          child: RefreshingBar(visible: list.isRefreshing),
        ),
      ),
      body: Column(
        children: [
          AiUnavailableBanner(status: ref.watch(aiStatusProvider)),
          Expanded(
            child: ScreenBody(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _SkillFilterChip(skillId: skillId),
                  const SizedBox(height: AppSpacing.md),
                  list.when(
                    loading: () => const SkeletonList(count: 4, lines: 2),
                    error: (error, _) => ErrorView(
                      error: error,
                      onRetry: () => ref.read(provider.notifier).reload(),
                    ),
                    data: (challenges) => _ChallengeList(skillId: skillId, list: challenges),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ChallengeList extends ConsumerWidget {
  const _ChallengeList({required this.skillId, required this.list});

  final String? skillId;
  final CursorList<ChallengeSummaryView> list;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    if (list.items.isEmpty) {
      return EmptyState(
        icon: Icons.fitness_center,
        message: l10n.trainingListEmpty,
        actionLabel: l10n.reviewSummaryToToday,
        actionKey: const Key('training.list.todayButton'),
        onAction: () => context.go(AppRoutes.today),
      );
    }
    final notifier = ref.read(trainingListControllerProvider(skillId).notifier);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final challenge in list.items) ...[
          ChallengeTile(challenge: challenge),
          const SizedBox(height: AppSpacing.sm),
        ],
        LoadMoreFooter(
          hasMore: list.hasMore,
          isLoading: list.isLoadingMore,
          error: list.loadMoreError,
          onLoadMore: () async {
            if (await notifier.loadMore() && context.mounted) {
              showToast(context, l10n.errorInvalidCursor);
            }
          },
        ),
      ],
    );
  }
}

/// "기술: 전체 ▾" / "기술: {name} ▾": a one-skill picker; clearing it lists every skill.
class _SkillFilterChip extends ConsumerWidget {
  const _SkillFilterChip({required this.skillId});

  final String? skillId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final tree = ref.watch(skillTreeProvider).value;
    final selected = tree?.skills.where((skill) => skill.id == skillId).firstOrNull;
    return Align(
      alignment: Alignment.centerLeft,
      child: ActionChip(
        key: const Key('training.list.skillFilter'),
        avatar: const Icon(Icons.filter_list, size: 18),
        label: Text(l10n.trainingListFilterSkill(selected?.name ?? l10n.trainingListFilterAll)),
        onPressed: tree == null
            ? null
            : () async {
                final codes = await showSkillMultiPicker(
                  context,
                  title: l10n.trainingListFilterTitle,
                  skills: pickableSkills(tree, l10n),
                  initialCodes: [?selected?.code],
                  maxCount: 1,
                );
                if (codes == null || !context.mounted) {
                  return;
                }
                final code = codes.firstOrNull;
                final id = tree.skills.where((skill) => skill.code == code).firstOrNull?.id;
                context.go(AppRoutes.trainingFor(id));
              },
      ),
    );
  }
}
