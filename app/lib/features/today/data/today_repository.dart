import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Today endpoints (docs/05 §8). Methods throw `ApiException`.
abstract interface class TodayRepository {
  /// `GET /today`. `404 TODAY_NOT_GENERATED` before the first generation of the plan-day.
  Future<TodayView> fetchToday();

  /// `POST /today/generate`: creates or regenerates today's plan.
  Future<TodayView> generate(
    TodayGenerateRequest request, {
    required IdempotencyKey idempotencyKey,
  });

  /// `PATCH /today/tasks/{taskId}`: one allowed status transition (docs/04 §4.1).
  Future<TaskStatusView> updateTaskStatus(String taskId, TaskStatusPatchRequest request);
}

final class ApiTodayRepository implements TodayRepository {
  ApiTodayRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<TodayView> fetchToday() async => TodayView.fromJson(await _apiClient.getJson('/today'));

  @override
  Future<TodayView> generate(
    TodayGenerateRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    assert(request.energyLevel != EnergyLevel.unknown, 'unknown is never sent');
    return TodayView.fromJson(
      await _apiClient.postJson(
        '/today/generate',
        body: request.toJson(),
        idempotencyKey: idempotencyKey,
      ),
    );
  }

  @override
  Future<TaskStatusView> updateTaskStatus(String taskId, TaskStatusPatchRequest request) async {
    assert(request.status != TaskStatus.unknown, 'unknown is never sent');
    return TaskStatusView.fromJson(
      await _apiClient.patchJson('/today/tasks/$taskId', body: request.toJson()),
    );
  }
}

final todayRepositoryProvider = Provider<TodayRepository>(
  (ref) => ApiTodayRepository(ref.watch(apiClientProvider)),
);
