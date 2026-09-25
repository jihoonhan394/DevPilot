import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/tip/data/tip_models.dart';
import 'package:devpilot_app/features/tip/data/tip_repository.dart';

const testTipKey = 'TIP.LOGGING.LEVELS.001';

DailyTipView testTip({
  String tipKey = testTipKey,
  TipFeedback? feedback,
  String? shownOn = '2026-09-25',
  String? example,
  String? experiment = '레벨을 한 단계 올리고 같은 요청을 한 번 보내 어떤 줄이 사라지는지 본다.',
}) => DailyTipView(
  tipKey: tipKey,
  series: TipSeries.logging,
  level: TipLevel.practical,
  title: '로그 레벨은 언제 무엇을 쓰나',
  symptom: '장애가 났는데 로그에는 INFO 한 줄만 남아 있다.',
  cause: '레벨은 "얼마나 자세한가"가 아니라 "누가 언제 보는가"로 나눈다.',
  example: example,
  whereToLook: '기본 레벨 설정과, 예외를 잡는 자리에서 어떤 레벨로 남기는지 두 곳을 본다.',
  experiment: experiment,
  estimatedMinutes: 3,
  shownOn: shownOn,
  feedback: feedback,
);

TipSummaryView testTipSummary({String tipKey = testTipKey, TipFeedback? feedback}) =>
    TipSummaryView(
      tipKey: tipKey,
      series: TipSeries.logging,
      level: TipLevel.practical,
      title: '로그 레벨은 언제 무엇을 쓰나',
      symptom: '장애가 났는데 로그에는 INFO 한 줄만 남아 있다.',
      estimatedMinutes: 3,
      feedback: feedback,
    );

final class FakeTipRepository implements TipRepository {
  /// null이면 오늘 보여 줄 팁이 없다(404) — SCR-TODAY는 카드 자리를 비운다.
  DailyTipView? today = testTip();

  /// key → 본문. 없으면 404다.
  final tips = <String, DailyTipView>{testTipKey: testTip()};

  var page = CursorPage<TipSummaryView>(items: [testTipSummary()]);

  /// 보낸 피드백 기록. 두 번 눌러도 한 번만 늘어야 한다.
  final chosen = <({String tipKey, TipFeedback feedback})>[];

  ApiException? feedbackFailure;

  @override
  Future<DailyTipView> fetchToday() async {
    final value = today;
    if (value == null) {
      throw const ApiException(code: ApiErrorCode.resourceNotFound, status: 404);
    }
    return value;
  }

  @override
  Future<DailyTipView> fetch(String tipKey) async {
    final value = tips[tipKey];
    if (value == null) {
      throw const ApiException(code: ApiErrorCode.resourceNotFound, status: 404);
    }
    return value;
  }

  @override
  Future<DailyTipView> chooseFeedback(
    String tipKey,
    TipFeedback feedback, {
    required IdempotencyKey idempotencyKey,
  }) async {
    final failure = feedbackFailure;
    if (failure != null) {
      throw failure;
    }
    chosen.add((tipKey: tipKey, feedback: feedback));
    final updated = (tips[tipKey] ?? testTip(tipKey: tipKey)).copyWith(feedback: feedback);
    tips[tipKey] = updated;
    if (today?.tipKey == tipKey) {
      today = updated;
    }
    return updated;
  }

  @override
  Future<CursorPage<TipSummaryView>> list({
    TipSeries? series,
    TipLevel? level,
    String? cursor,
  }) async {
    listQueries.add((series: series, level: level, cursor: cursor));
    return page;
  }

  /// 목록 요청 기록. 필터를 바꾸면 cursor 없이 다시 읽어야 한다 (docs/02 §3.17).
  final listQueries = <({TipSeries? series, TipLevel? level, String? cursor})>[];
}
