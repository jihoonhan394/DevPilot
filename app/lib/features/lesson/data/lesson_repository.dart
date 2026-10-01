import 'package:devpilot_app/core/api/api_client.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/lesson/data/lesson_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// 개념 노트 (docs/05 §21). 가르치는 단계의 본문·채점·기록이다.
///
/// **AI를 부르지 않는다.** 예측과 빈칸은 서버가 문자열로 즉시 채점하고, 백지 문제는 채점하지 않는다 — 모범 답안을 따로 받아
/// 사용자가 스스로 견준다. 사용자가 쓴 답은 서버로 보내지 않는다.
abstract interface class LessonRepository {
  /// 노트 목록과 진행 (docs/05 §21.9). 이어서 할 것이 맨 위다.
  Future<LessonListView> fetchLessons();

  Future<LessonView> fetchLesson(String lessonKey);

  /// 그 skill의 노트 (docs/05 §21.3). 없으면 404다.
  Future<LessonView> fetchLessonForSkill(String skillId);

  Future<PredictResult> checkPredict(String lessonKey, String unitKey, String answer);

  Future<CompleteResult> checkComplete(String lessonKey, String unitKey, List<String> answers);

  Future<AnswerResult> fetchAnswer(String lessonKey, String unitKey);

  /// 설명을 다른 각도로 한 번 더 (docs/05 §21.10). 저장하지 않는다.
  Future<ReexplainResult> reexplain(String lessonKey, String unitKey, ConfusionReason reason);

  Future<FinishResult> finishUnit(
    String lessonKey,
    String unitKey, {
    required HelpLevel helpLevel,
    int? selfChecksMet,
    required IdempotencyKey idempotencyKey,
  });
}

final class ApiLessonRepository implements LessonRepository {
  ApiLessonRepository(this._apiClient);

  final ApiClient _apiClient;

  @override
  Future<LessonListView> fetchLessons() async =>
      LessonListView.fromJson(await _apiClient.getJson('/lessons'));

  @override
  Future<LessonView> fetchLesson(String lessonKey) async =>
      LessonView.fromJson(await _apiClient.getJson(_lesson(lessonKey)));

  @override
  Future<LessonView> fetchLessonForSkill(String skillId) async => LessonView.fromJson(
    await _apiClient.getJson('/skills/${Uri.encodeComponent(skillId)}/lesson'),
  );

  @override
  Future<PredictResult> checkPredict(String lessonKey, String unitKey, String answer) async =>
      PredictResult.fromJson(
        await _apiClient.postJson(
          '${_unit(lessonKey, unitKey)}/predict',
          body: {'answer': answer},
          idempotencyKey: IdempotencyKey.generate(),
        ),
      );

  @override
  Future<CompleteResult> checkComplete(
    String lessonKey,
    String unitKey,
    List<String> answers,
  ) async => CompleteResult.fromJson(
    await _apiClient.postJson(
      '${_unit(lessonKey, unitKey)}/complete',
      body: {'answers': answers},
      idempotencyKey: IdempotencyKey.generate(),
    ),
  );

  @override
  Future<AnswerResult> fetchAnswer(String lessonKey, String unitKey) async =>
      AnswerResult.fromJson(await _apiClient.getJson('${_unit(lessonKey, unitKey)}/answer'));

  @override
  Future<ReexplainResult> reexplain(
    String lessonKey,
    String unitKey,
    ConfusionReason reason,
  ) async => ReexplainResult.fromJson(
    await _apiClient.postJson(
      '${_unit(lessonKey, unitKey)}/reexplain',
      body: {'reason': _reasonValue(reason)},
      idempotencyKey: IdempotencyKey.generate(),
    ),
  );

  static String _reasonValue(ConfusionReason reason) => switch (reason) {
    ConfusionReason.unfamiliarTerms => 'UNFAMILIAR_TERMS',
    ConfusionReason.whyNotClear => 'WHY_NOT_CLEAR',
    ConfusionReason.exampleUnclear => 'EXAMPLE_UNCLEAR',
  };

  @override
  Future<FinishResult> finishUnit(
    String lessonKey,
    String unitKey, {
    required HelpLevel helpLevel,
    int? selfChecksMet,
    required IdempotencyKey idempotencyKey,
  }) async => FinishResult.fromJson(
    await _apiClient.postJson(
      '${_unit(lessonKey, unitKey)}/finish',
      body: {
        'helpLevel': helpLevel.name.toUpperCase(),
        'selfChecksMet': ?selfChecksMet,
      },
      idempotencyKey: idempotencyKey,
    ),
  );

  static String _lesson(String lessonKey) => '/lessons/${Uri.encodeComponent(lessonKey)}';

  static String _unit(String lessonKey, String unitKey) =>
      '${_lesson(lessonKey)}/units/${Uri.encodeComponent(unitKey)}';
}

final lessonRepositoryProvider = Provider<LessonRepository>(
  (ref) => ApiLessonRepository(ref.watch(apiClientProvider)),
);

/// 노트 목록. 단위를 마치고 돌아오면 다시 읽어 진행이 반영되게 한다.
final lessonListProvider = FutureProvider.autoDispose<LessonListView>(
  (ref) => ref.watch(lessonRepositoryProvider).fetchLessons(),
);

/// 노트 하나. 진행이 바뀌면 다시 읽는다.
final lessonProvider = FutureProvider.autoDispose.family<LessonView, String>(
  (ref, lessonKey) => ref.watch(lessonRepositoryProvider).fetchLesson(lessonKey),
);

/// 그 skill의 노트. 없으면 404이고 화면은 진입점을 숨긴다.
final skillLessonProvider = FutureProvider.autoDispose.family<LessonView?, String>((
  ref,
  skillId,
) async {
  try {
    return await ref.watch(lessonRepositoryProvider).fetchLessonForSkill(skillId);
  } on ApiException catch (failure) {
    if (failure.code == ApiErrorCode.resourceNotFound) {
      return null;
    }
    rethrow;
  }
});

/// `^LESSON\.<주제>[.<하위>]\.NNN$` (docs/05 §21): 다른 것은 SCR-NOT-FOUND다.
final lessonKeyPattern = RegExp(r'^LESSON\.[A-Z][A-Z0-9_]*(\.[A-Z][A-Z0-9_]*)*\.[0-9]{3}$');
