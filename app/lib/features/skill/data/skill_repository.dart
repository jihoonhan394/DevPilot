import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/widgets/skill_multi_picker.dart';
import 'package:devpilot_app/features/skill/data/skill_history_models.dart';
import 'package:devpilot_app/features/skill/data/skill_models.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Skill endpoints (docs/05 §6). Methods throw `ApiException`.
abstract interface class SkillRepository {
  /// `GET /skills/tree?role=` — allowed before onboarding.
  Future<SkillTreeResponse> fetchTree({TargetRole role = TargetRole.javaBackend});

  /// `GET /skills/me`.
  Future<UserSkillStatesResponse> fetchMyStates();

  /// `GET /skills/{skillId}/history?cursor=` — level changes, newest first.
  Future<CursorPage<SkillStateChangeView>> fetchHistory({required String skillId, String? cursor});
}

final class ApiSkillRepository implements SkillRepository {
  ApiSkillRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<SkillTreeResponse> fetchTree({TargetRole role = TargetRole.javaBackend}) async {
    assert(role != TargetRole.unknown, 'unknown is never sent');
    return SkillTreeResponse.fromJson(
      await _apiClient.getJson('/skills/tree', queryParameters: {'role': role.wireName}),
    );
  }

  @override
  Future<UserSkillStatesResponse> fetchMyStates() async =>
      UserSkillStatesResponse.fromJson(await _apiClient.getJson('/skills/me'));

  @override
  Future<CursorPage<SkillStateChangeView>> fetchHistory({
    required String skillId,
    String? cursor,
  }) async {
    final json = await _apiClient.getJson(
      '/skills/$skillId/history',
      queryParameters: {'cursor': ?cursor},
    );
    return CursorPage.fromJson(
      json,
      (item) => SkillStateChangeView.fromJson(item! as Map<String, Object?>),
    );
  }
}

final skillRepositoryProvider = Provider<SkillRepository>(
  (ref) => ApiSkillRepository(ref.watch(apiClientProvider)),
);

/// The JAVA_BACKEND catalog. Kept for the session because the catalog rarely changes and several
/// screens (onboarding, goal, replan, skill tree) pick skills from it.
final skillTreeProvider = FutureProvider<SkillTreeResponse>((ref) {
  ref.watch(authStateProvider.select((authState) => authState.accessToken));
  return ref.watch(skillRepositoryProvider).fetchTree();
});

/// The signed-in user's skill states, re-read each time a skill screen opens.
final mySkillStatesProvider = FutureProvider.autoDispose<UserSkillStatesResponse>(
  (ref) => ref.watch(skillRepositoryProvider).fetchMyStates(),
);

/// Catalog skills as picker choices, grouped by category in catalog order. Root skills (no parent)
/// are categories' umbrella nodes and are included like any other active skill.
List<PickableSkill> pickableSkills(SkillTreeResponse tree, AppLocalizations l10n) => [
  for (final skill in tree.skills)
    PickableSkill(code: skill.code, name: skill.name, groupLabel: skill.category.label(l10n)),
];
