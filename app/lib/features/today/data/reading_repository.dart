import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/features/today/data/reading_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// `GET /readings/{readingKey}` (docs/05 §19.7): code readings (`kind = CODE`) and concept
/// readings (`kind = CONCEPT`), the same content for every user, no AI, no repository or document
/// fetch. Throws `ApiException`.
abstract interface class ReadingRepository {
  Future<ReadingView> fetchReading(String readingKey);
}

final class ApiReadingRepository implements ReadingRepository {
  ApiReadingRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<ReadingView> fetchReading(String readingKey) async => ReadingView.fromJson(
    await _apiClient.getJson('/readings/${Uri.encodeComponent(readingKey)}'),
  );
}

final readingRepositoryProvider = Provider<ReadingRepository>(
  (ref) => ApiReadingRepository(ref.watch(apiClientProvider)),
);

/// One reading, read again each time SCR-READ-CODE or a READING card opens.
final readingProvider = FutureProvider.autoDispose.family<ReadingView, String>(
  (ref, readingKey) => ref.watch(readingRepositoryProvider).fetchReading(readingKey),
);

/// `^[A-Z0-9][A-Z0-9_.]{2,149}$` (docs/02 §2.3): anything else is SCR-NOT-FOUND.
final readingKeyPattern = RegExp(r'^[A-Z0-9][A-Z0-9_.]{2,149}$');
