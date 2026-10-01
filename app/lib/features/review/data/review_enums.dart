import 'package:freezed_annotation/freezed_annotation.dart';

part 'review_enums.g.dart';

// Review module enums (docs/04 §3). Every enum ends with `unknown`; requests never send it.

enum ReviewType {
  @JsonValue('RECALL')
  recall,
  @JsonValue('BUG_SPOT')
  bugSpot,
  @JsonValue('EXPLAIN')
  explain,
  @JsonValue('CHOICE')
  choice,
  unknown;

  /// Types a manual card can have (docs/02 SCR-REVIEW-ITEM-EDIT: 떠올리기 · 설명 · 버그 찾기).
  static const manual = [recall, explain, bugSpot];
}

/// Ordered `AGAIN(0) < HARD(1) < GOOD(2) < EASY(3)` (docs/06 §6.1).
enum ReviewRating {
  @JsonValue('AGAIN')
  again,
  @JsonValue('HARD')
  hard,
  @JsonValue('GOOD')
  good,
  @JsonValue('EASY')
  easy,
  unknown;

  /// The four self-assessment buttons, in shortcut order 1~4.
  static const known = [again, hard, good, easy];
}

/// What the learner saw before rating (docs/04 §3). The review screen sends only
/// `SELF_EXPLAIN`, `CONCEPT_HINT` and `FULL_EXAMPLE` (docs/05 §11.3).
enum HintLevel {
  @JsonValue('SELF_EXPLAIN')
  selfExplain,
  @JsonValue('QUESTION_ONLY')
  questionOnly,
  @JsonValue('CONCEPT_HINT')
  conceptHint,
  @JsonValue('DIRECTION')
  direction,
  @JsonValue('PSEUDOCODE')
  pseudocode,
  @JsonValue('PARTIAL_CODE')
  partialCode,
  @JsonValue('FULL_EXAMPLE')
  fullExample,
  unknown,
}

/// Rules that lowered the self rating (docs/06 §6.1).
enum RatingAdjustment {
  @JsonValue('EVALUATED_INCORRECT')
  evaluatedIncorrect,
  @JsonValue('EVALUATED_PARTIAL')
  evaluatedPartial,
  @JsonValue('HINT_CAP_AGAIN')
  hintCapAgain,
  @JsonValue('HINT_CAP_HARD')
  hintCapHard,
  @JsonValue('HINT_CAP_GOOD')
  hintCapGood,
  unknown,
}

enum EvaluatedOutcome {
  @JsonValue('CORRECT')
  correct,
  @JsonValue('PARTIAL')
  partial,
  @JsonValue('INCORRECT')
  incorrect,
  @JsonValue('NOT_EVALUATED')
  notEvaluated,
  unknown,
}

@JsonEnum(alwaysCreate: true)
enum ReviewItemStatus {
  @JsonValue('ACTIVE')
  active,
  @JsonValue('SUSPENDED')
  suspended,
  @JsonValue('ARCHIVED')
  archived,
  unknown;

  /// The three filter segments of SCR-REVIEW-ITEMS, without [unknown].
  static const known = [active, suspended, archived];

  /// Wire value for `GET /review-items?status=` and the route query.
  String get wireName => _$ReviewItemStatusEnumMap[this]!;

  static ReviewItemStatus? fromWire(String? value) =>
      known.where((status) => status.wireName == value).firstOrNull;
}

/// Where a review card came from (docs/04 §3).
enum ReviewItemSourceType {
  @JsonValue('SEED_CARD')
  seedCard,
  @JsonValue('MANUAL')
  manual,
  @JsonValue('CHALLENGE_ATTEMPT')
  challengeAttempt,
  @JsonValue('COACH_FINDING')
  coachFinding,
  @JsonValue('EVIDENCE')
  evidence,
  @JsonValue('RUBBER_DUCK')
  rubberDuck,
  unknown,
}

enum VariantStatus {
  @JsonValue('NONE')
  none,
  @JsonValue('PENDING')
  pending,
  @JsonValue('RUNNING')
  running,
  @JsonValue('READY')
  ready,
  @JsonValue('FAILED')
  failed,
  unknown,
}
