import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/cursor_list.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/features/plan/data/plan_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// SCR-PLAN-HISTORY: `GET /plans?cursor=` with scroll pagination (docs/02 §3.9).
final class PlanHistoryController extends AsyncNotifier<CursorList<PlanSummaryView>> {
  @override
  Future<CursorList<PlanSummaryView>> build() async =>
      CursorList.firstPage(await ref.read(planRepositoryProvider).fetchPlans());

  /// Loads the next page. `INVALID_CURSOR` restarts from the first page (docs/02 §5.1); returns
  /// true in that case so the screen can show the toast.
  Future<bool> loadMore() async {
    final list = state.value;
    final cursor = list?.nextCursor;
    if (list == null || cursor == null || list.isLoadingMore) {
      return false;
    }
    state = AsyncData(list.loadingMore());
    try {
      final page = await ref.read(planRepositoryProvider).fetchPlans(cursor: cursor);
      if (ref.mounted) {
        state = AsyncData(list.append(page));
      }
      return false;
    } on ApiException catch (error) {
      if (!ref.mounted) {
        return false;
      }
      if (error.code == ApiErrorCode.invalidCursor) {
        ref.invalidateSelf();
        return true;
      }
      state = AsyncData(list.failedToLoadMore(error));
      return false;
    }
  }
}

final planHistoryControllerProvider =
    AsyncNotifierProvider.autoDispose<PlanHistoryController, CursorList<PlanSummaryView>>(
      PlanHistoryController.new,
    );
