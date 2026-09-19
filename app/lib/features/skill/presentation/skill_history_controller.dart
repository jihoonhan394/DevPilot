import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/cursor_list.dart';
import 'package:devpilot_app/features/skill/data/skill_history_models.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Level change history of one skill: `GET /skills/{skillId}/history?cursor=` with "더 보기"
/// pagination (docs/02 SCR-SKILL-DETAIL, docs/05 §6.3).
final class SkillHistoryController extends AsyncNotifier<CursorList<SkillStateChangeView>> {
  SkillHistoryController(this.skillId);

  final String skillId;

  SkillRepository get _repository => ref.read(skillRepositoryProvider);

  @override
  Future<CursorList<SkillStateChangeView>> build() async =>
      CursorList.firstPage(await _repository.fetchHistory(skillId: skillId));

  /// Loads the next page. `INVALID_CURSOR` starts again from the first page (docs/02 §5.1) and
  /// returns true so the screen can show the toast.
  Future<bool> loadMore() async {
    final list = state.value;
    final cursor = list?.nextCursor;
    if (list == null || cursor == null || list.isLoadingMore) {
      return false;
    }
    state = AsyncData(list.loadingMore());
    try {
      final page = await _repository.fetchHistory(skillId: skillId, cursor: cursor);
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

final skillHistoryControllerProvider = AsyncNotifierProvider.autoDispose
    .family<SkillHistoryController, CursorList<SkillStateChangeView>, String>(
      SkillHistoryController.new,
    );
