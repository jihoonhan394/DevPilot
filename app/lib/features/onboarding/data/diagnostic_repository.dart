import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/features/onboarding/data/onboarding_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// `GET /diagnostics/suggestions` (docs/05 §4.2): at most five diagnostic challenges, one per
/// category, recomputed on every call. Throws `ApiException`.
abstract interface class DiagnosticRepository {
  Future<List<DiagnosticSuggestionView>> fetchSuggestions();
}

final class ApiDiagnosticRepository implements DiagnosticRepository {
  ApiDiagnosticRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<List<DiagnosticSuggestionView>> fetchSuggestions() async {
    final json = await _apiClient.getJson('/diagnostics/suggestions');
    final items = json['items'];
    return [
      if (items is List<Object?>)
        for (final item in items)
          if (item is Map<String, Object?>) DiagnosticSuggestionView.fromJson(item),
    ];
  }
}

final diagnosticRepositoryProvider = Provider<DiagnosticRepository>(
  (ref) => ApiDiagnosticRepository(ref.watch(apiClientProvider)),
);
