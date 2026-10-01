import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/tip/data/tip_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// 오늘의 팁 (docs/05 §20). AI를 부르지 않는다.
abstract interface class TipRepository {
  /// `GET /tips/today`. 같은 날 다시 불러도 같은 팁이다 (docs/06 §5.12 TIP-3).
  Future<DailyTipView> fetchToday();

  /// `POST /tips/{tipKey}/feedback`. 이미 고른 값이 있으면 덮어쓰지 않고 그 값이 돌아온다.
  Future<DailyTipView> chooseFeedback(
    String tipKey,
    TipFeedback feedback, {
    required IdempotencyKey idempotencyKey,
  });

  /// `GET /tips/{tipKey}`. 오늘의 팁이 아니어도, 은퇴한 팁이어도 본문이 온다 (docs/05 §20.4a).
  Future<DailyTipView> fetch(String tipKey);

  /// `GET /tips`. 은퇴하지 않은 팁, `tipKey` ASC.
  Future<CursorPage<TipSummaryView>> list({
    TipSeries? series,
    TipLevel? level,
    String? cursor,
  });
}

final class ApiTipRepository implements TipRepository {
  ApiTipRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<DailyTipView> fetchToday() async =>
      DailyTipView.fromJson(await _apiClient.getJson('/tips/today'));

  @override
  Future<DailyTipView> chooseFeedback(
    String tipKey,
    TipFeedback feedback, {
    required IdempotencyKey idempotencyKey,
  }) async {
    assert(feedback != TipFeedback.unknown, 'unknown is never sent');
    return DailyTipView.fromJson(
      await _apiClient.postJson(
        '/tips/$tipKey/feedback',
        body: TipFeedbackRequest(feedback: feedback).toJson(),
        idempotencyKey: idempotencyKey,
      ),
    );
  }

  @override
  Future<DailyTipView> fetch(String tipKey) async =>
      DailyTipView.fromJson(await _apiClient.getJson('/tips/$tipKey'));

  @override
  Future<CursorPage<TipSummaryView>> list({
    TipSeries? series,
    TipLevel? level,
    String? cursor,
  }) async {
    final json = await _apiClient.getJson(
      '/tips',
      queryParameters: {
        if (series != null && series != TipSeries.unknown) 'series': _name(series),
        if (level != null && level != TipLevel.unknown) 'level': _name(level),
        'cursor': ?cursor,
      },
    );
    return CursorPage.fromJson(
      json,
      (row) => TipSummaryView.fromJson(row! as Map<String, Object?>),
    );
  }

  static String _name(Enum value) => _wireNames[value]!;

  /// 화면이 고른 값 → 서버가 아는 이름. 쿼리 문자열에는 직렬화기가 끼어들지 않아 표를 손으로 들고 있다.
  static const _wireNames = <Enum, String>{
    TipSeries.errorReading: 'ERROR_READING',
    TipSeries.resource: 'RESOURCE',
    TipSeries.logging: 'LOGGING',
    TipSeries.httpIntegration: 'HTTP_INTEGRATION',
    TipSeries.database: 'DATABASE',
    TipSeries.operations: 'OPERATIONS',
    TipSeries.convention: 'CONVENTION',
    TipLevel.basic: 'BASIC',
    TipLevel.practical: 'PRACTICAL',
  };
}

final tipRepositoryProvider = Provider<TipRepository>(
  (ref) => ApiTipRepository(ref.watch(apiClientProvider)),
);

/// docs/05 §20.4a: `TIP.<SERIES>.<TOPIC>.NNN`. 형식이 틀리면 서버에 묻지 않고 SCR-NOT-FOUND로 간다.
final tipKeyPattern = RegExp(r'^TIP\.[A-Z][A-Z0-9_]*\.[A-Z][A-Z0-9_]*\.[0-9]{3}$');
