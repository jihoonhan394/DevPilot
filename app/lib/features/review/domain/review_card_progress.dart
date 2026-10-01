import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_models.dart';
import 'package:flutter/foundation.dart';

/// What the learner has seen of one card before rating it (docs/02 SCR-REVIEW-SESSION,
/// docs/06 §6.1 "복습 화면의 hint 기준").
///
/// Nothing seen → `SELF_EXPLAIN`; "힌트 보기" (first rubric item) → `CONCEPT_HINT`;
/// "모르겠어요, 정답 볼게요" → `FULL_EXAMPLE`. After "답 확인" the hint level no longer changes.
@immutable
final class ReviewCardProgress {
  const ReviewCardProgress({
    required this.item,
    required this.shownAt,
    this.answerText = '',
    this.hintLevel = HintLevel.selfExplain,
    this.revealed = false,
    this.requestEvaluation = false,
  });

  final DueReviewItemView item;

  /// When the card appeared; `responseSeconds` counts from here.
  final DateTime shownAt;
  final String answerText;
  final HintLevel hintLevel;
  final bool revealed;

  /// "AI에게 채점 받기"를 켰는가. 매번 고른다 — 카드를 넘기면 다시 꺼진 상태로 시작한다.
  final bool requestEvaluation;

  /// 채점을 물어볼 수 있는 카드인가: 답을 썼고, 견줄 루브릭이 있다.
  ///
  /// 루브릭이 없으면 AI가 항목별로 짚어 줄 것이 없다 — 물어봐야 답이 "채점함" 한 줄뿐이다.
  bool get canAskEvaluation => answerText.trim().isNotEmpty && item.rubric.isNotEmpty;

  /// "힌트 보기" exists only while nothing was revealed and the card has a rubric.
  bool get canShowHint => !revealed && hintLevel == HintLevel.selfExplain && item.rubric.isNotEmpty;

  /// The first rubric item, shown once the hint was asked for.
  String? get hintText => hintLevel == HintLevel.conceptHint || hintLevel == HintLevel.fullExample
      ? item.rubric.firstOrNull?.criterion
      : null;

  ReviewCardProgress withAnswer(String text) => _copy(answerText: text);

  ReviewCardProgress withHint() => canShowHint ? _copy(hintLevel: HintLevel.conceptHint) : this;

  /// "모르겠어요, 정답 볼게요": reveals with `FULL_EXAMPLE`.
  ReviewCardProgress withAnswerShownFirst() =>
      revealed ? this : _copy(hintLevel: HintLevel.fullExample, revealed: true);

  /// "답 확인": reveals and freezes the hint level.
  ReviewCardProgress withRevealed() => _copy(revealed: true);

  /// "AI에게 채점 받기" 토글. 물어볼 수 없는 카드에서는 아무 일도 없다.
  ReviewCardProgress withEvaluation(bool wanted) =>
      canAskEvaluation ? _copy(requestEvaluation: wanted) : this;

  /// `POST /reviews/{id}/answer` body for [rating]. 채점은 학습자가 그 카드에서 켠 경우에만 요청한다 —
  /// 실제 AI 호출이고 비용이 든다(docs/17 §8).
  ReviewAnswerRequest request(ReviewRating rating, DateTime now) {
    final seconds = now.difference(shownAt).inSeconds;
    return ReviewAnswerRequest(
      answerText: answerText.trim().isEmpty ? null : answerText,
      selfRating: rating,
      hintLevel: hintLevel,
      responseSeconds: seconds.clamp(0, maxResponseSeconds),
      wasVariant: item.wasVariant,
      evaluate: requestEvaluation && canAskEvaluation,
    );
  }

  static const maxResponseSeconds = 86400;

  ReviewCardProgress _copy({
    String? answerText,
    HintLevel? hintLevel,
    bool? revealed,
    bool? requestEvaluation,
  }) => ReviewCardProgress(
    item: item,
    shownAt: shownAt,
    answerText: answerText ?? this.answerText,
    hintLevel: hintLevel ?? this.hintLevel,
    revealed: revealed ?? this.revealed,
    requestEvaluation: requestEvaluation ?? this.requestEvaluation,
  );
}

/// Estimated review minutes for [count] cards: `ceilDiv(n × 3, 2)` (docs/02 SCR-REVIEW-HOME).
int estimatedReviewMinutes(int count) => (count * 3 + 1) ~/ 2;
