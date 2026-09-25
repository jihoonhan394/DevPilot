import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/settings/data/me_response.dart';
import 'package:devpilot_app/features/settings/data/progress_reset_response.dart';
import 'package:devpilot_app/features/settings/data/update_me_request.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// The signed-in user's profile (docs/05 §3.1, §3.2). Methods throw `ApiException`.
abstract interface class MeRepository {
  /// `GET /me`.
  Future<MeResponse> fetchMe();

  /// `PATCH /me`. `409 CONCURRENT_MODIFICATION` when [request] carries an old version.
  Future<MeResponse> updateMe(UpdateMeRequest request);

  /// `POST /me/reset` (docs/05 §3.7). 계정은 남기고 진도만 온보딩 이전으로 되돌린다.
  Future<ProgressResetResponse> resetProgress({
    required String confirmation,
    required bool includeProjects,
    required IdempotencyKey idempotencyKey,
  });
}

final class ApiMeRepository implements MeRepository {
  ApiMeRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<MeResponse> fetchMe() async => MeResponse.fromJson(await _apiClient.getJson('/me'));

  @override
  Future<MeResponse> updateMe(UpdateMeRequest request) async =>
      MeResponse.fromJson(await _apiClient.patchJson('/me', body: request.toJson()));

  @override
  Future<ProgressResetResponse> resetProgress({
    required String confirmation,
    required bool includeProjects,
    required IdempotencyKey idempotencyKey,
  }) async => ProgressResetResponse.fromJson(
    await _apiClient.postJson(
      '/me/reset',
      body: {'confirmation': confirmation, 'includeProjects': includeProjects},
      idempotencyKey: idempotencyKey,
    ),
  );
}

final meRepositoryProvider = Provider<MeRepository>(
  (ref) => ApiMeRepository(ref.watch(apiClientProvider)),
);
