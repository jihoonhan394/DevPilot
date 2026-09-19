import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/features/plan/data/learning_goal_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Learning goal endpoints (docs/05 §5). Methods throw `ApiException`.
abstract interface class LearningGoalRepository {
  /// `GET /learning-goal`. `404 LEARNING_GOAL_NOT_FOUND` before onboarding.
  Future<LearningGoalView> fetchGoal();

  /// `PUT /learning-goal` (full replacement with version).
  Future<LearningGoalView> updateGoal(LearningGoalUpdateRequest request);
}

final class ApiLearningGoalRepository implements LearningGoalRepository {
  ApiLearningGoalRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<LearningGoalView> fetchGoal() async =>
      LearningGoalView.fromJson(await _apiClient.getJson('/learning-goal'));

  @override
  Future<LearningGoalView> updateGoal(LearningGoalUpdateRequest request) async =>
      LearningGoalView.fromJson(await _apiClient.putJson('/learning-goal', body: request.toJson()));
}

final learningGoalRepositoryProvider = Provider<LearningGoalRepository>(
  (ref) => ApiLearningGoalRepository(ref.watch(apiClientProvider)),
);
