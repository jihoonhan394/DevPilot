import 'package:freezed_annotation/freezed_annotation.dart';

part 'lesson_models.freezed.dart';
part 'lesson_models.g.dart';

// 개념 노트 (docs/05 §21, docs/19 §3.14). 가르치는 단계의 본문이다.
//
// 조회 응답에는 **정답이 없다** — 예측의 답, 빈칸의 답, 모범 답안, 확인 목록은 채점·조회 응답으로만 온다.

/// 문제를 풀 때 쓴 도움 (docs/05 §21.7). 화면이 무엇을 열었는지 세어 보낸다.
enum HelpLevel {
  @JsonValue('NONE')
  none,
  @JsonValue('HINT')
  hint,
  @JsonValue('DUCK')
  duck,
  @JsonValue('ANSWER')
  answer,
}

@freezed
abstract class LessonView with _$LessonView {
  const LessonView._();

  const factory LessonView({
    required String lessonKey,
    String? skillId,
    required String skillCode,
    required String skillName,
    required String title,

    /// 모르면 무슨 일이 생기나 + 내 프로젝트 어디에 쓰이나.
    required String whyItMatters,
    required String oneLine,
    required List<LessonUnitView> units,
    @Default(<String>[]) List<String> commonMistakes,
    required String inProject,
    @Default(<LessonSourceView>[]) List<LessonSourceView> sources,
    @Default(<LessonSourceView>[]) List<LessonSourceView> readMore,
    @Default(false) bool retired,
  }) = _LessonView;

  factory LessonView.fromJson(Map<String, Object?> json) => _$LessonViewFromJson(json);

  /// 다음에 할 단위: 아직 마치지 않은 첫 번째. 전부 마쳤으면 null.
  LessonUnitView? get nextUnit => units.where((unit) => unit.progress == null).firstOrNull;

  int get solvedCount => units.where((unit) => unit.progress != null).length;
}

@freezed
abstract class LessonUnitView with _$LessonUnitView {
  const LessonUnitView._();

  const factory LessonUnitView({
    required String unitKey,
    required String title,
    required int minutes,

    /// 기한이 촉박하면 이것만 한다.
    required bool core,

    /// 마크다운.
    required String explain,
    required LessonExampleView example,
    required LessonQuestionView predict,
    required LessonQuestionView complete,
    required LessonProblemView problem,
    @Default(<String>[]) List<String> prerequisiteUnits,

    /// 그 사용자의 진행. 마친 적이 없으면 null.
    UnitProgressView? progress,
  }) = _LessonUnitView;

  factory LessonUnitView.fromJson(Map<String, Object?> json) => _$LessonUnitViewFromJson(json);
}

@freezed
abstract class LessonExampleView with _$LessonExampleView {
  const factory LessonExampleView({
    required String language,
    required String code,
    String? output,
    String? note,
  }) = _LessonExampleView;

  factory LessonExampleView.fromJson(Map<String, Object?> json) =>
      _$LessonExampleViewFromJson(json);
}

/// 문항의 보이는 부분. 정답은 채점 응답에만 있다.
@freezed
abstract class LessonQuestionView with _$LessonQuestionView {
  const LessonQuestionView._();

  const factory LessonQuestionView({
    required String question,
    String? code,

    /// 비어 있으면 한 줄을 직접 쓴다.
    @Default(<String>[]) List<String> choices,

    /// 빈칸 수. 예측 문항은 0.
    @Default(0) int blanks,
  }) = _LessonQuestionView;

  factory LessonQuestionView.fromJson(Map<String, Object?> json) =>
      _$LessonQuestionViewFromJson(json);

  bool get multipleChoice => choices.isNotEmpty;
}

/// 백지 문제의 보이는 부분. 모범 답안은 따로 받는다.
@freezed
abstract class LessonProblemView with _$LessonProblemView {
  const factory LessonProblemView({
    required String prompt,
    @Default(<String>[]) List<String> deliverables,
    String? starterCode,
    @Default(<String>[]) List<String> hints,
  }) = _LessonProblemView;

  factory LessonProblemView.fromJson(Map<String, Object?> json) =>
      _$LessonProblemViewFromJson(json);
}

@freezed
abstract class LessonSourceView with _$LessonSourceView {
  const factory LessonSourceView({
    required String title,
    required String url,
    String? versionScope,
  }) = _LessonSourceView;

  factory LessonSourceView.fromJson(Map<String, Object?> json) => _$LessonSourceViewFromJson(json);
}

@freezed
abstract class UnitProgressView with _$UnitProgressView {
  const factory UnitProgressView({
    required bool solved,
    required HelpLevel helpLevel,
    required DateTime solvedAt,
    int? selfChecksMet,
  }) = _UnitProgressView;

  factory UnitProgressView.fromJson(Map<String, Object?> json) => _$UnitProgressViewFromJson(json);
}

/// 예측 채점 결과 (docs/05 §21.4).
@freezed
abstract class PredictResult with _$PredictResult {
  const factory PredictResult({
    required bool correct,
    required String expected,
    required String explanation,
  }) = _PredictResult;

  factory PredictResult.fromJson(Map<String, Object?> json) => _$PredictResultFromJson(json);
}

/// 빈칸 채점 결과 (docs/05 §21.5). `results`는 빈칸 순서대로다.
@freezed
abstract class CompleteResult with _$CompleteResult {
  const factory CompleteResult({
    required bool correct,
    @Default(<bool>[]) List<bool> results,
    @Default(<String>[]) List<String> expected,
    required String explanation,
  }) = _CompleteResult;

  factory CompleteResult.fromJson(Map<String, Object?> json) => _$CompleteResultFromJson(json);
}

/// 모범 답안과 확인 목록 (docs/05 §21.6).
@freezed
abstract class AnswerResult with _$AnswerResult {
  const factory AnswerResult({
    required String modelAnswer,
    @Default(<String>[]) List<String> selfChecks,
  }) = _AnswerResult;

  factory AnswerResult.fromJson(Map<String, Object?> json) => _$AnswerResultFromJson(json);
}

/// 단위를 마친 기록 (docs/05 §21.7).
@freezed
abstract class FinishResult with _$FinishResult {
  const factory FinishResult({
    required String unitKey,
    required HelpLevel helpLevel,
    int? selfChecksMet,
    required DateTime recordedAt,
  }) = _FinishResult;

  factory FinishResult.fromJson(Map<String, Object?> json) => _$FinishResultFromJson(json);
}
