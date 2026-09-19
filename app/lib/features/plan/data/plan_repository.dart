import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Plan endpoints (docs/05 §7). Methods throw `ApiException`.
abstract interface class PlanRepository {
  /// `GET /plans/active`. `404 PLAN_NOT_FOUND` when there is no active plan.
  Future<PlanView> fetchActivePlan();

  /// `GET /plans?cursor=` (planVersion DESC).
  Future<CursorPage<PlanSummaryView>> fetchPlans({String? cursor});

  /// `GET /plans/{planId}`, including superseded versions.
  Future<PlanView> fetchPlan(String planId);

  /// `PATCH /plans/{planId}/milestones/{milestoneId}`: status, description or sortOrder only.
  Future<MilestoneView> patchMilestone(
    String planId,
    String milestoneId,
    MilestonePatchRequest request,
  );

  /// `POST /plans/{planId}/replan`: saves a new plan version.
  Future<ReplanCommitResponse> replan(
    String planId,
    ReplanRequest request, {
    required IdempotencyKey idempotencyKey,
  });

  /// `POST /plans` (body none): creates a template plan when none is active.
  Future<PlanView> createPlan({required IdempotencyKey idempotencyKey});
}

final class ApiPlanRepository implements PlanRepository {
  ApiPlanRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<PlanView> fetchActivePlan() async =>
      PlanView.fromJson(await _apiClient.getJson('/plans/active'));

  @override
  Future<CursorPage<PlanSummaryView>> fetchPlans({String? cursor}) async {
    final json = await _apiClient.getJson('/plans', queryParameters: {'cursor': ?cursor});
    return CursorPage.fromJson(
      json,
      (item) => PlanSummaryView.fromJson(item! as Map<String, Object?>),
    );
  }

  @override
  Future<PlanView> fetchPlan(String planId) async =>
      PlanView.fromJson(await _apiClient.getJson('/plans/$planId'));

  @override
  Future<MilestoneView> patchMilestone(
    String planId,
    String milestoneId,
    MilestonePatchRequest request,
  ) async => MilestoneView.fromJson(
    await _apiClient.patchJson(
      '/plans/$planId/milestones/$milestoneId',
      body: request.toJson(),
    ),
  );

  @override
  Future<ReplanCommitResponse> replan(
    String planId,
    ReplanRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async => ReplanCommitResponse.fromJson(
    await _apiClient.postJson(
      '/plans/$planId/replan',
      body: request.toJson(),
      idempotencyKey: idempotencyKey,
    ),
  );

  @override
  Future<PlanView> createPlan({required IdempotencyKey idempotencyKey}) async => PlanView.fromJson(
    await _apiClient.postJson('/plans', body: const {}, idempotencyKey: idempotencyKey),
  );
}

final planRepositoryProvider = Provider<PlanRepository>(
  (ref) => ApiPlanRepository(ref.watch(apiClientProvider)),
);
