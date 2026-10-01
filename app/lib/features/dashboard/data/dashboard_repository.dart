import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/features/dashboard/data/dashboard_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// `GET /dashboard` (docs/05 §13.1). Throws `ApiException`.
abstract interface class DashboardRepository {
  Future<DashboardView> fetchDashboard();
}

final class ApiDashboardRepository implements DashboardRepository {
  ApiDashboardRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<DashboardView> fetchDashboard() async =>
      DashboardView.fromJson(await _apiClient.getJson('/dashboard'));
}

final dashboardRepositoryProvider = Provider<DashboardRepository>(
  (ref) => ApiDashboardRepository(ref.watch(apiClientProvider)),
);
