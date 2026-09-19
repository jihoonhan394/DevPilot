import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/features/today/data/reading_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// `GET /readings/{readingKey}` (docs/05 §19.7): the same content for every user, no AI, no
/// repository fetch. Throws `ApiException`.
abstract interface class ReadingRepository {
  Future<CuratedReadingView> fetchReading(String readingKey);
}

final class ApiReadingRepository implements ReadingRepository {
  ApiReadingRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<CuratedReadingView> fetchReading(String readingKey) async => CuratedReadingView.fromJson(
    await _apiClient.getJson('/readings/${Uri.encodeComponent(readingKey)}'),
  );
}

final readingRepositoryProvider = Provider<ReadingRepository>(
  (ref) => ApiReadingRepository(ref.watch(apiClientProvider)),
);

/// One reading, read again each time SCR-READ-CODE opens.
final readingProvider = FutureProvider.autoDispose.family<CuratedReadingView, String>(
  (ref, readingKey) => ref.watch(readingRepositoryProvider).fetchReading(readingKey),
);

/// `^[A-Z0-9][A-Z0-9_.]{2,149}$` (docs/02 §2.3): anything else is SCR-NOT-FOUND.
final readingKeyPattern = RegExp(r'^[A-Z0-9][A-Z0-9_.]{2,149}$');
