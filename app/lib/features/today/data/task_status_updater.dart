import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';
import 'package:devpilot_app/features/today/data/today_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Moves a Today task to a new status the way docs/02 SCR-TODAY prescribes.
///
/// `409 CONCURRENT_MODIFICATION` re-reads `GET /today` and sends the same change once more with
/// the new `version`. When the re-read task already has the target status (another tab or device
/// did it), the change counts as done. `INVALID_STATE_TRANSITION` gets the same check because the
/// server rejects a change to the current status.
final class TaskStatusUpdater {
  TaskStatusUpdater(this._repository);

  final TodayRepository _repository;

  static const _recheckCodes = {
    ApiErrorCode.concurrentModification,
    ApiErrorCode.invalidStateTransition,
  };

  /// Sends the change. Without [knownVersion] the version is read from `GET /today` first
  /// (the Review screen only knows the task id). [readingFeedback] goes with a READ_CODE
  /// completion only.
  Future<void> update(
    String taskId,
    TaskStatus target, {
    int? knownVersion,
    ReadingFeedback? readingFeedback,
  }) async {
    final version = knownVersion ?? _findTask(await _repository.fetchToday(), taskId)?.version;
    if (version == null) {
      throw const ApiException(code: ApiErrorCode.resourceNotFound, status: 404);
    }
    try {
      await _send(taskId, target, version, readingFeedback);
    } on ApiException catch (error) {
      if (!_recheckCodes.contains(error.code)) {
        rethrow;
      }
      final current = _findTask(await _repository.fetchToday(), taskId);
      if (current == null) {
        rethrow;
      }
      if (current.status == target) {
        return;
      }
      if (error.code != ApiErrorCode.concurrentModification) {
        rethrow;
      }
      await _send(taskId, target, current.version, readingFeedback);
    }
  }

  Future<void> _send(
    String taskId,
    TaskStatus target,
    int version,
    ReadingFeedback? readingFeedback,
  ) => _repository.updateTaskStatus(
    taskId,
    TaskStatusPatchRequest(status: target, readingFeedback: readingFeedback, version: version),
  );

  /// Status and version of [taskId] in [today]: the main task, the REVIEW task or an earlier main.
  static ({TaskStatus status, int version})? _findTask(TodayView today, String taskId) {
    final review = today.reviewTask;
    if (review != null && review.id == taskId) {
      return (status: review.status, version: review.version);
    }
    for (final task in [?today.mainTask, ...today.earlierMainTasks]) {
      if (task.id == taskId) {
        return (status: task.status, version: task.version);
      }
    }
    return null;
  }
}

final taskStatusUpdaterProvider = Provider<TaskStatusUpdater>(
  (ref) => TaskStatusUpdater(ref.watch(todayRepositoryProvider)),
);
