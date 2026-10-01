import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/cursor_list.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/load_more_footer.dart';
import 'package:devpilot_app/core/widgets/refreshing_bar.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/core/widgets/skill_multi_picker.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_item_models.dart';
import 'package:devpilot_app/features/review/presentation/review_item_tile.dart';
import 'package:devpilot_app/features/review/presentation/review_items_controller.dart';
import 'package:devpilot_app/features/review/presentation/review_labels.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-REVIEW-ITEMS: find cards by status and skill, then pause, reactivate, archive, edit or
/// explain them (docs/02 §3.6). The filters live in the route (`?skillId=&status=`).
class ReviewItemsScreen extends ConsumerWidget {
  const ReviewItemsScreen({super.key, required this.filter});

  final ReviewItemsFilter filter;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final provider = reviewItemsControllerProvider(filter);
    final list = ref.watch(provider);
    return Scaffold(
      appBar: AppBar(
        title: Semantics(header: true, child: Text(l10n.reviewItemsTitle)),
        actions: [
          IconButton(
            key: const Key('reviewItems.addButton'),
            tooltip: l10n.reviewItemsAdd,
            icon: const Icon(Icons.add),
            onPressed: () => context.go(AppRoutes.reviewItemsNew),
          ),
        ],
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(2),
          child: RefreshingBar(visible: list.isRefreshing),
        ),
      ),
      body: ScreenBody(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _Filters(filter: filter),
            const SizedBox(height: AppSpacing.md),
            list.when(
              loading: () => const SkeletonList(count: 6, lines: 2),
              error: (error, _) =>
                  ErrorView(error: error, onRetry: () => ref.read(provider.notifier).reload()),
              data: (items) => _ItemList(filter: filter, list: items),
            ),
          ],
        ),
      ),
    );
  }
}

/// Status segments (사용 중 · 일시중지 · 보관) and the skill chip; each change is a new route.
class _Filters extends ConsumerWidget {
  const _Filters({required this.filter});

  final ReviewItemsFilter filter;

  void _go(BuildContext context, {String? skillId, required ReviewItemStatus status}) =>
      context.go(AppRoutes.reviewItemsFor(skillId: skillId, status: status.wireName));

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final tree = ref.watch(skillTreeProvider).value;
    final skill = tree?.skills.where((node) => node.id == filter.skillId).firstOrNull;
    return Wrap(
      spacing: AppSpacing.sm,
      runSpacing: AppSpacing.sm,
      children: [
        SegmentedButton<ReviewItemStatus>(
          key: const Key('reviewItems.statusFilter'),
          segments: [
            for (final status in ReviewItemStatus.known)
              ButtonSegment(value: status, label: Text(status.label(l10n))),
          ],
          selected: {filter.status},
          showSelectedIcon: false,
          onSelectionChanged: (selected) =>
              _go(context, skillId: filter.skillId, status: selected.single),
        ),
        ActionChip(
          key: const Key('reviewItems.skillFilter'),
          avatar: const Icon(Icons.filter_list, size: 18),
          label: Text(l10n.trainingListFilterSkill(skill?.name ?? l10n.trainingListFilterAll)),
          onPressed: tree == null
              ? null
              : () async {
                  final codes = await showSkillMultiPicker(
                    context,
                    title: l10n.trainingListFilterTitle,
                    skills: pickableSkills(tree, l10n),
                    initialCodes: [?skill?.code],
                    maxCount: 1,
                  );
                  if (codes == null || !context.mounted) {
                    return;
                  }
                  final code = codes.firstOrNull;
                  final id = tree.skills.where((node) => node.code == code).firstOrNull?.id;
                  _go(context, skillId: id, status: filter.status);
                },
        ),
      ],
    );
  }
}

class _ItemList extends ConsumerWidget {
  const _ItemList({required this.filter, required this.list});

  final ReviewItemsFilter filter;
  final CursorList<ReviewItemView> list;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    if (list.items.isEmpty) {
      final active = filter.status == ReviewItemStatus.active;
      return EmptyState(
        icon: Icons.style,
        message: switch (filter.status) {
          ReviewItemStatus.suspended => l10n.reviewItemsEmptySuspended,
          ReviewItemStatus.archived => l10n.reviewItemsEmptyArchived,
          _ => l10n.reviewItemsEmptyActive,
        },
        actionLabel: active ? l10n.reviewHomeAdd : null,
        actionKey: const Key('reviewItems.emptyAddButton'),
        onAction: active ? () => context.go(AppRoutes.reviewItemsNew) : null,
      );
    }
    final notifier = ref.read(reviewItemsControllerProvider(filter).notifier);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final item in list.items) ...[
          ReviewItemTile(item: item, filter: filter),
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
