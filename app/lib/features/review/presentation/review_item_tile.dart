import 'dart:async';

import 'package:devpilot_app/app/explain_with_duck_button.dart';
import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/confirm_dialog.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_item_models.dart';
import 'package:devpilot_app/features/review/presentation/review_items_controller.dart';
import 'package:devpilot_app/features/review/presentation/review_labels.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

enum _ItemAction { suspend, reactivate, archive, edit, explain }

/// One row of SCR-REVIEW-ITEMS: the prompt in two lines, `skill · 유형 · 출처`, the next review
/// date or the status, the last result, and the `⋮` menu (docs/02 SCR-REVIEW-ITEMS).
class ReviewItemTile extends ConsumerWidget {
  const ReviewItemTile({super.key, required this.item, required this.filter});

  final ReviewItemView item;
  final ReviewItemsFilter filter;

  Future<void> _onAction(BuildContext context, WidgetRef ref, _ItemAction action) async {
    final l10n = AppLocalizations.of(context);
    final controller = ref.read(reviewItemsControllerProvider(filter).notifier);
    final ReviewItemStatus target;
    switch (action) {
      case _ItemAction.edit:
        context.go(AppRoutes.reviewItem(item.id), extra: item);
        return;
      case _ItemAction.explain:
        openRubberDuck(context, _duckLaunch());
        return;
      case _ItemAction.suspend:
        target = ReviewItemStatus.suspended;
      case _ItemAction.reactivate:
        target = ReviewItemStatus.active;
      case _ItemAction.archive:
        final confirmed = await showConfirmDialog(
          context,
          title: l10n.reviewItemsArchive,
          body: l10n.reviewItemsArchiveConfirm,
          confirmLabel: l10n.reviewItemsArchive,
          cancelLabel: l10n.commonCancel,
          destructive: true,
          confirmKey: const Key('reviewItems.archiveConfirmButton'),
        );
        if (!confirmed) {
          return;
        }
        target = ReviewItemStatus.archived;
    }
    final outcome = await controller.changeStatus(item, target);
    if (!context.mounted) {
      return;
    }
    switch (outcome) {
      case ReviewItemActionDone():
        if (target == ReviewItemStatus.active) {
          showToast(context, l10n.reviewItemsReactivated);
        }
      case ReviewItemActionReloaded():
        showToast(context, l10n.errorConcurrentModification);
      case ReviewItemActionFailed(:final error):
        await presentActionError(context, ref, error);
    }
  }

  RubberDuckLaunch _duckLaunch() => RubberDuckLaunch(
    targetType: RubberDuckTargetType.reviewItem,
    targetId: item.id,
    skillCode: item.skill.code,
    preview: RubberDuckTargetPreview(title: item.skill.name, summary: item.prompt),
  );

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final due = LocalDate.tryParse(item.dueDate);
    final last = item.lastResult;
    final state = item.status == ReviewItemStatus.active && due != null
        ? l10n.reviewItemsNextDue(formatPlanDate(due, l10n))
        : item.status.label(l10n);
    return Card(
      key: Key('reviewItems.item.${item.id}'),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.lg,
          AppSpacing.md,
          AppSpacing.xs,
          AppSpacing.md,
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(item.prompt, maxLines: 2, overflow: TextOverflow.ellipsis),
                  Text(
                    l10n.reviewItemsMeta(
                      item.skill.name,
                      item.reviewType.label(l10n),
                      item.sourceType.label(l10n),
                    ),
                    style: textTheme.bodySmall,
                  ),
                  Text(state, style: textTheme.bodySmall),
                  if (last != null)
                    Text(l10n.reviewItemsLastResult(last.label(l10n)), style: textTheme.bodySmall),
                ],
              ),
            ),
            _ItemMenu(
              item: item,
              onSelected: (action) => unawaited(_onAction(context, ref, action)),
            ),
          ],
        ),
      ),
    );
  }
}

String _short(String text) => text.length <= 40 ? text : '${text.substring(0, 40)}…';

/// ACTIVE → 일시중지 · 보관 · 수정 · 러버덕 / SUSPENDED → 다시 사용 · 보관 · 수정 · 러버덕 /
/// ARCHIVED → no menu (archiving is final). The rubber duck item is hidden while the AI is off.
class _ItemMenu extends ConsumerWidget {
  const _ItemMenu({required this.item, required this.onSelected});

  final ReviewItemView item;
  final ValueChanged<_ItemAction> onSelected;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    if (item.status == ReviewItemStatus.archived || item.status == ReviewItemStatus.unknown) {
      return const SizedBox(width: AppSizes.minTouchTarget);
    }
    final active = item.status == ReviewItemStatus.active;
    final aiAvailable = ref.watch(aiStatusProvider).allowsAi;
    PopupMenuItem<_ItemAction> entry(_ItemAction action, String label) => PopupMenuItem(
      key: Key('reviewItems.menu.${action.name}'),
      value: action,
      child: Text(label),
    );
    return PopupMenuButton<_ItemAction>(
      key: Key('reviewItems.menuButton.${item.id}'),
      // The prompt keeps each row's menu name distinct for screen readers.
      tooltip: l10n.reviewItemsMenu(_short(item.prompt)),
      onSelected: onSelected,
      itemBuilder: (_) => [
        if (active)
          entry(_ItemAction.suspend, l10n.reviewItemsSuspend)
        else
          entry(_ItemAction.reactivate, l10n.reviewItemsReactivate),
        entry(_ItemAction.archive, l10n.reviewItemsArchive),
        entry(_ItemAction.edit, l10n.reviewItemsEdit),
        if (aiAvailable) entry(_ItemAction.explain, l10n.reviewItemsExplain),
      ],
    );
  }
}
