import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/lesson/data/lesson_models.dart';
import 'package:devpilot_app/features/lesson/data/lesson_repository.dart';
import 'package:devpilot_app/features/lesson/presentation/lesson_step.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// 한 단위를 걸어가는 상태 (docs/02 SCR-LESSON). 걸음은 화면에만 있고 서버에는 마친 단위만 남는다.
@immutable
class UnitWalkState {
  const UnitWalkState({
    required this.unitKey,
    this.step = LessonStep.why,
    this.predictAnswer,
    this.predictResult,
    this.completeResult,
    this.hintsOpened = 0,
    this.answerRevealed = false,
    this.modelAnswer,
    this.selfChecks = const <String>[],
    this.selfChecksMet = const <bool>[],
    this.finished = false,
    this.busy = false,
    this.failure,
  });

  final String unitKey;
  final LessonStep step;

  /// 고른 보기 또는 쓴 한 줄. 채점 뒤에도 남겨 화면에 보인다.
  final String? predictAnswer;
  final PredictResult? predictResult;
  final CompleteResult? completeResult;
  final int hintsOpened;
  final bool answerRevealed;
  final String? modelAnswer;
  final List<String> selfChecks;
  final List<bool> selfChecksMet;
  final bool finished;
  final bool busy;
  final ApiException? failure;

  int get metCount => selfChecksMet.where((met) => met).length;

  UnitWalkState copyWith({
    LessonStep? step,
    String? predictAnswer,
    PredictResult? predictResult,
    CompleteResult? completeResult,
    int? hintsOpened,
    bool? answerRevealed,
    String? modelAnswer,
    List<String>? selfChecks,
    List<bool>? selfChecksMet,
    bool? finished,
    bool? busy,
    ApiException? failure,
    bool clearFailure = false,
  }) => UnitWalkState(
    unitKey: unitKey,
    step: step ?? this.step,
    predictAnswer: predictAnswer ?? this.predictAnswer,
    predictResult: predictResult ?? this.predictResult,
    completeResult: completeResult ?? this.completeResult,
    hintsOpened: hintsOpened ?? this.hintsOpened,
    answerRevealed: answerRevealed ?? this.answerRevealed,
    modelAnswer: modelAnswer ?? this.modelAnswer,
    selfChecks: selfChecks ?? this.selfChecks,
    selfChecksMet: selfChecksMet ?? this.selfChecksMet,
    finished: finished ?? this.finished,
    busy: busy ?? this.busy,
    failure: clearFailure ? null : (failure ?? this.failure),
  );
}

/// 단위 하나를 걸어간다. 도움 단계는 여기서 센다 — 서버는 추적하지 않는다(docs/05 §21.7).
final class UnitWalkController extends Notifier<UnitWalkState> {
  UnitWalkController(this.target);

  final ({String lessonKey, String unitKey}) target;
  final _help = HelpTracker();
  final _finishKeys = IdempotencyKeyCache();

  @override
  UnitWalkState build() => UnitWalkState(unitKey: target.unitKey);

  /// 걸음을 하나 앞으로. 마지막이면 그대로 둔다.
  void next() {
    final following = state.step.following;
    if (following != null) {
      state = state.copyWith(step: following, clearFailure: true);
    }
  }

  void back() {
    final previous = state.step.previous;
    if (previous != null) {
      state = state.copyWith(step: previous, clearFailure: true);
    }
  }

  /// "이미 안다 → 문제부터" (docs/02 SCR-LESSON). 도움으로 치지 않는다.
  void skipToProblem() => state = state.copyWith(step: LessonStep.problem, clearFailure: true);

  void chooseCandidate(String answer) => state = state.copyWith(predictAnswer: answer);

  Future<void> checkPredict(String answer) async {
    await _run(() async {
      final result = await _repository.checkPredict(target.lessonKey, target.unitKey, answer);
      state = state.copyWith(predictAnswer: answer, predictResult: result);
    });
  }

  Future<void> checkComplete(List<String> answers) async {
    await _run(() async {
      final result = await _repository.checkComplete(target.lessonKey, target.unitKey, answers);
      state = state.copyWith(completeResult: result);
    });
  }

  /// 힌트를 하나 더 연다. 도움 단계가 `HINT` 이상이 된다.
  void openHint() {
    _help.openedHint();
    state = state.copyWith(hintsOpened: state.hintsOpened + 1);
  }

  /// 러버덕으로 갔다. 도움 단계가 `DUCK` 이상이 된다.
  void askedTheDuck() => _help.askedTheDuck();

  /// 모범 답안을 본다. 아직 힌트도 안 봤다면 `ANSWER`다.
  Future<void> revealAnswer() async {
    if (_help.level == HelpLevel.none) {
      _help.openedTheAnswerFirst();
    }
    await _run(() async {
      final answer = await _repository.fetchAnswer(target.lessonKey, target.unitKey);
      state = state.copyWith(
        answerRevealed: true,
        modelAnswer: answer.modelAnswer,
        selfChecks: answer.selfChecks,
        selfChecksMet: List<bool>.filled(answer.selfChecks.length, false),
        step: LessonStep.compare,
      );
    });
  }

  void toggleSelfCheck(int index, bool met) {
    final updated = [...state.selfChecksMet];
    updated[index] = met;
    state = state.copyWith(selfChecksMet: updated);
  }

  /// 단위를 마쳤다고 기록한다. 사용자가 쓴 답은 보내지 않는다.
  Future<void> finish() async {
    final met = state.selfChecks.isEmpty ? null : state.metCount;
    final body = <String, Object?>{
      'helpLevel': _help.level.name.toUpperCase(),
      'selfChecksMet': ?met,
    };
    await _run(() async {
      // 응답을 잃고 다시 보내도 이벤트가 둘이 되지 않게 같은 키를 쓴다.
      await _repository.finishUnit(
        target.lessonKey,
        target.unitKey,
        helpLevel: _help.level,
        selfChecksMet: met,
        idempotencyKey: _finishKeys.keyFor(body),
      );
      _finishKeys.settle(null);
      ref.invalidate(lessonProvider(target.lessonKey));
      state = state.copyWith(finished: true, step: LessonStep.next);
    });
  }

  LessonRepository get _repository => ref.read(lessonRepositoryProvider);

  Future<void> _run(Future<void> Function() action) async {
    state = state.copyWith(busy: true, clearFailure: true);
    try {
      await action();
    } on ApiException catch (failure) {
      state = state.copyWith(failure: failure);
    } finally {
      state = state.copyWith(busy: false);
    }
  }
}

final unitWalkProvider = NotifierProvider.autoDispose
    .family<UnitWalkController, UnitWalkState, ({String lessonKey, String unitKey})>(
      UnitWalkController.new,
    );
