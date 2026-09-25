import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/term/data/term_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// 용어 사전 (docs/05 §20.5~§20.7). AI를 부르지 않는다.
abstract interface class TermRepository {
  /// `GET /terms`. 대표 표기·영어·다른 표기 어느 쪽으로도 찾는다. 은퇴한 용어는 나오지 않는다.
  Future<CursorPage<TermSummaryView>> list({String? q, String? skillId, String? cursor});

  /// `GET /terms/{termKey}`. 은퇴한 용어도 열린다 (docs/05 §20.6).
  Future<TermView> fetch(String termKey);

  /// `POST /terms/{termKey}/card`. 앞뒤 두 장을 만든다. 이미 있으면 새로 만들지 않는다.
  Future<TermCardResponse> createCard(String termKey, {required IdempotencyKey idempotencyKey});
}

final class ApiTermRepository implements TermRepository {
  ApiTermRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<CursorPage<TermSummaryView>> list({String? q, String? skillId, String? cursor}) async {
    final json = await _apiClient.getJson(
      '/terms',
      queryParameters: {'q': ?q, 'skillId': ?skillId, 'cursor': ?cursor},
    );
    return CursorPage.fromJson(
      json,
      (row) => TermSummaryView.fromJson(row! as Map<String, Object?>),
    );
  }

  @override
  Future<TermView> fetch(String termKey) async =>
      TermView.fromJson(await _apiClient.getJson('/terms/$termKey'));

  @override
  Future<TermCardResponse> createCard(
    String termKey, {
    required IdempotencyKey idempotencyKey,
  }) async => TermCardResponse.fromJson(
    // body 없는 POST다 (docs/05 §20.7). 서버는 경로와 IK만 본다
    await _apiClient.postJson(
      '/terms/$termKey/card',
      body: const {},
      idempotencyKey: idempotencyKey,
    ),
  );
}

final termRepositoryProvider = Provider<TermRepository>(
  (ref) => ApiTermRepository(ref.watch(apiClientProvider)),
);

/// docs/05 §20: `TERM.<GROUP>.<NAME>`. 형식이 틀리면 서버에 묻지 않고 SCR-NOT-FOUND로 간다.
final termKeyPattern = RegExp(r'^TERM\.[A-Z][A-Z0-9_]*\.[A-Z][A-Z0-9_]*$');
