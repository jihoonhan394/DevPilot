import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/project/data/side_project_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Side project endpoints (docs/05 §19.2~§19.6). Methods throw `ApiException`.
abstract interface class SideProjectRepository {
  /// `GET /side-projects?status=&cursor=` (updatedAt DESC, id DESC).
  Future<CursorPage<SideProjectView>> fetchProjects({SideProjectStatus? status, String? cursor});

  /// `GET /side-projects/{sideProjectId}`.
  Future<SideProjectView> fetchProject(String sideProjectId);

  /// `POST /side-projects` → 201.
  Future<SideProjectView> createProject(
    SideProjectCreateRequest request, {
    required IdempotencyKey idempotencyKey,
  });

  /// `PATCH /side-projects/{sideProjectId}`.
  Future<SideProjectView> updateProject(String sideProjectId, SideProjectPatchRequest request);

  /// `DELETE /side-projects/{sideProjectId}` → 204.
  Future<void> deleteProject(String sideProjectId);
}

final class ApiSideProjectRepository implements SideProjectRepository {
  ApiSideProjectRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<CursorPage<SideProjectView>> fetchProjects({
    SideProjectStatus? status,
    String? cursor,
  }) async {
    assert(status != SideProjectStatus.unknown, 'unknown is never sent');
    final json = await _apiClient.getJson(
      '/side-projects',
      queryParameters: {'status': ?status?.wireName, 'cursor': ?cursor},
    );
    return CursorPage.fromJson(
      json,
      (item) => SideProjectView.fromJson(item! as Map<String, Object?>),
    );
  }

  @override
  Future<SideProjectView> fetchProject(String sideProjectId) async =>
      SideProjectView.fromJson(await _apiClient.getJson('/side-projects/$sideProjectId'));

  @override
  Future<SideProjectView> createProject(
    SideProjectCreateRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async => SideProjectView.fromJson(
    await _apiClient.postJson(
      '/side-projects',
      body: request.toJson(),
      idempotencyKey: idempotencyKey,
    ),
  );

  @override
  Future<SideProjectView> updateProject(
    String sideProjectId,
    SideProjectPatchRequest request,
  ) async {
    assert(request.status != SideProjectStatus.unknown, 'unknown is never sent');
    return SideProjectView.fromJson(
      await _apiClient.patchJson('/side-projects/$sideProjectId', body: request.toJson()),
    );
  }

  @override
  Future<void> deleteProject(String sideProjectId) =>
      _apiClient.delete('/side-projects/$sideProjectId');
}

final sideProjectRepositoryProvider = Provider<SideProjectRepository>(
  (ref) => ApiSideProjectRepository(ref.watch(apiClientProvider)),
);
