import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/cursor_list.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_item_models.dart';
import 'package:devpilot_app/features/review/data/review_item_repository.dart';
import 'package:devpilot_app/features/review/presentation/due_reviews_provider.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Filters of SCR-REVIEW-ITEMS, from the route query (`?skillId=&status=`).
typedef ReviewItemsFilter = ({String? skillId, ReviewItemStatus status});

sealed class ReviewItemActionOutcome {
  const ReviewItemActionOutcome();
}

final class ReviewItemActionDone extends ReviewItemActionOutcome {
  const ReviewItemActionDone();
}

/// `CONCURRENT_MODIFICATION` or `INVALID_STATE_TRANSITION`: changed elsewhere, list re-read.
final class ReviewItemActionReloaded extends ReviewItemActionOutcome {
  const ReviewItemActionReloaded();
}

final class ReviewItemActionFailed extends ReviewItemActionOutcome {
  const ReviewItemActionFailed(this.error);

  final Object error;
}

/// SCR-REVIEW-ITEMS: `GET /review-items` for one filter with paging, and the `⋮` status changes
/// (docs/02 §3.6, docs/05 §11.4, §11.6).
final class ReviewItemsController extends AsyncNotifier<CursorList<ReviewItemView>> {
  ReviewItemsController(this.filter);

  final ReviewItemsFilter filter;

  ReviewItemRepository get _repository => ref.read(reviewItemRepositoryProvider);

  @override
  Future<CursorList<ReviewItemView>> build() async => CursorList.firstPage(
    await _repository.fetchItems(skillId: filter.skillId, status: filter.status),
  );

  void reload() => ref.invalidateSelf();

  /// Next page. `INVALID_CURSOR` restarts from the first page and returns true (toast).
  Future<bool> loadMore() async {
    final list = state.value;
    final cursor = list?.nextCursor;
    if (list == null || cursor == null || list.isLoadingMore) {
      return false;
    }
    state = AsyncData(list.loadingMore());
    try {
      final page = await _repository.fetchItems(
        skillId: filter.skillId,
        status: filter.status,
        cursor: cursor,
      );
      if (ref.mounted) {
        state = AsyncData(list.append(page));
      }
      return false;
    } on ApiException catch (error) {
      if (!ref.mounted) {
        return false;
      }
      if (error.code == ApiErrorCode.invalidCursor) {
        reload();
        return true;
      }
      state = AsyncData(list.failedToLoadMore(error));
      return false;
    }
  }

  /// "일시중지", "다시 사용", "보관": the card leaves this filter's list.
  Future<ReviewItemActionOutcome> changeStatus(
    ReviewItemView item,
    ReviewItemStatus status,
  ) async {
    try {
      await _repository.updateItem(
        item.id,
        ReviewItemPatchRequest(status: status, version: item.version),
      );
      // Due cards may change (reactivated cards are due from the next plan-day).
      ref.invalidate(dueReviewsProvider);
      final list = state.value;
      if (list != null && ref.mounted) {
        state = AsyncData(
          list.withItems([
            for (final other in list.items)
              if (other.id != item.id) other,
          ]),
        );
      }
      return const ReviewItemActionDone();
    } on ApiException catch (error) {
      if (error.code == ApiErrorCode.concurrentModification ||
          error.code == ApiErrorCode.invalidStateTransition ||
          error.code == ApiErrorCode.resourceNotFound) {
        reload();
        return const ReviewItemActionReloaded();
      }
      return ReviewItemActionFailed(error);
    }
  }
}

final reviewItemsControllerProvider = AsyncNotifierProvider.autoDispose
    .family<ReviewItemsController, CursorList<ReviewItemView>, ReviewItemsFilter>(
      ReviewItemsController.new,
    );
