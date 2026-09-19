import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/cursor_list.dart';
import 'package:devpilot_app/features/training/data/challenge_models.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';
import 'package:devpilot_app/features/training/data/training_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// SCR-TRAINING-LIST: `GET /challenges?skillId=&purpose=PRACTICE&cursor=` for the skill filter of
/// the route (`/training?skillId=`, docs/02 SCR-TRAINING-LIST).
final class TrainingListController extends AsyncNotifier<CursorList<ChallengeSummaryView>> {
  TrainingListController(this.skillId);

  /// Null lists every skill.
  final String? skillId;

  @override
  Future<CursorList<ChallengeSummaryView>> build() async => CursorList.firstPage(
    await ref
        .read(trainingRepositoryProvider)
        .fetchChallenges(skillId: skillId, purpose: ChallengePurpose.practice),
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
      final page = await ref
          .read(trainingRepositoryProvider)
          .fetchChallenges(skillId: skillId, purpose: ChallengePurpose.practice, cursor: cursor);
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
}

final trainingListControllerProvider = AsyncNotifierProvider.autoDispose
    .family<TrainingListController, CursorList<ChallengeSummaryView>, String?>(
      TrainingListController.new,
    );
