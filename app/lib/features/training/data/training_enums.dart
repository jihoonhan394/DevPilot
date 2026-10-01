import 'package:freezed_annotation/freezed_annotation.dart';

part 'training_enums.g.dart';

// Training module enums (docs/04 §3). Every enum ends with `unknown`; requests never send it.

enum ChallengeStatus {
  @JsonValue('DRAFT')
  draft,
  @JsonValue('VALIDATED')
  validated,
  @JsonValue('REJECTED')
  rejected,
  @JsonValue('RETIRED')
  retired,
  unknown,
}

@JsonEnum(alwaysCreate: true)
enum ChallengePurpose {
  @JsonValue('PRACTICE')
  practice,
  @JsonValue('DIAGNOSTIC')
  diagnostic,
  unknown;

  /// Wire value for `GET /challenges?purpose=`.
  String get wireName => _$ChallengePurposeEnumMap[this]!;
}

enum AttemptStatus {
  @JsonValue('STARTED')
  started,
  @JsonValue('SUBMITTED')
  submitted,
  @JsonValue('EVALUATED')
  evaluated,
  @JsonValue('ABANDONED')
  abandoned,
  unknown,
}

enum AttemptOutcome {
  @JsonValue('SOLVED_INDEPENDENTLY')
  solvedIndependently,
  @JsonValue('SOLVED_WITH_HINTS')
  solvedWithHints,
  @JsonValue('PARTIAL')
  partial,
  @JsonValue('FAILED')
  failed,
  @JsonValue('ABANDONED')
  abandoned,
  unknown,
}

enum RubricAxis {
  @JsonValue('IMPLEMENTATION')
  implementation,
  @JsonValue('EXPLANATION')
  explanation,
  @JsonValue('DEBUGGING')
  debugging,
  unknown,
}

/// Where a disclosed hint came from (docs/04 §3). `AI_GENERATED` hints carry the AI badge.
enum HintContentOrigin {
  @JsonValue('SEED')
  seed,
  @JsonValue('PREGENERATED')
  pregenerated,
  @JsonValue('AI_GENERATED')
  aiGenerated,
  unknown,
}
