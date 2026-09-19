import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/features/settings/data/me_response.dart';
import 'package:devpilot_app/features/settings/data/update_me_request.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// The signed-in user's profile (docs/05 §3.1, §3.2). Methods throw `ApiException`.
abstract interface class MeRepository {
  /// `GET /me`.
  Future<MeResponse> fetchMe();

  /// `PATCH /me`. `409 CONCURRENT_MODIFICATION` when [request] carries an old version.
  Future<MeResponse> updateMe(UpdateMeRequest request);
}

final class ApiMeRepository implements MeRepository {
  ApiMeRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<MeResponse> fetchMe() async => MeResponse.fromJson(await _apiClient.getJson('/me'));

  @override
  Future<MeResponse> updateMe(UpdateMeRequest request) async =>
      MeResponse.fromJson(await _apiClient.patchJson('/me', body: request.toJson()));
}

final meRepositoryProvider = Provider<MeRepository>(
  (ref) => ApiMeRepository(ref.watch(apiClientProvider)),
);
