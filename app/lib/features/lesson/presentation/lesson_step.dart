import 'package:devpilot_app/features/lesson/data/lesson_models.dart';

/// 학습 단위의 걸음 (docs/02 SCR-LESSON). 한 화면에 다 펼치지 않는다 — 답을 먼저 보게 되고, 평일 60분에 어디까지 했는지도 흐려진다.
enum LessonStep {
  /// 0. 왜 배우나 (노트를 열면 처음 한 번만).
  why,

  /// 1. 설명.
  explain,

  /// 2. 예제.
  example,

  /// 3. 출력 예측 — 내면 바로 채점.
  predict,

  /// 4. 빈칸 채우기 — 내면 바로 채점.
  complete,

  /// 5. 백지 문제 — 막히면 도움.
  problem,

  /// 6. 모범 답안과 견주기.
  compare,

  /// 7. 다음 걸음.
  next;

  LessonStep? get previous => index == 0 ? null : LessonStep.values[index - 1];

  LessonStep? get following =>
      index == LessonStep.values.length - 1 ? null : LessonStep.values[index + 1];

  /// "이미 안다 → 문제부터"를 보여 주는 걸음 (docs/02 SCR-LESSON).
  bool get canSkipToProblem => this == why || this == explain || this == example;
}

/// 화면이 세는 도움 단계 (docs/05 §21.7). 무엇을 열었는지 서버가 추적하지 않는다.
///
/// 첫 학습에서 예제를 다시 보는 것은 도움으로 치지 않는다 — 방금 본 것이다.
final class HelpTracker {
  HelpLevel _level = HelpLevel.none;

  HelpLevel get level => _level;

  /// 힌트 몇 개를 열었는지. 다음에 열 힌트를 고르는 데 쓴다.
  int hintsOpened = 0;

  void openedHint() {
    hintsOpened++;
    _raise(HelpLevel.hint);
  }

  void askedTheDuck() => _raise(HelpLevel.duck);

  void openedTheAnswerFirst() => _raise(HelpLevel.answer);

  void _raise(HelpLevel level) {
    if (level.index > _level.index) {
      _level = level;
    }
  }
}
