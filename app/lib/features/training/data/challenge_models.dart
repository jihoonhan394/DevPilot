import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'challenge_models.freezed.dart';
part 'challenge_models.g.dart';

// Challenge view records (docs/05 §10.1). The body fields are null unless the challenge is
// VALIDATED or RETIRED; the answer fields are null until the user has an evaluated attempt.

@freezed
abstract class ChallengeSummaryView with _$ChallengeSummaryView {
  const factory ChallengeSummaryView({
    required String id,
    required String title,
    required int difficulty,
    int? estimatedMinutes,
    @JsonKey(unknownEnumValue: ChallengePurpose.unknown) required ChallengePurpose purpose,
    @JsonKey(unknownEnumValue: ContentOrigin.unknown) required ContentOrigin origin,
    required bool isTransfer,
    required List<SkillRef> skills,

    /// The user's latest attempt; null when there is none.
    LastAttemptView? lastAttempt,
    required DateTime createdAt,
  }) = _ChallengeSummaryView;

  factory ChallengeSummaryView.fromJson(Map<String, Object?> json) =>
      _$ChallengeSummaryViewFromJson(json);
}

@freezed
abstract class LastAttemptView with _$LastAttemptView {
  const factory LastAttemptView({
    required String attemptId,
    @JsonKey(unknownEnumValue: AttemptStatus.unknown) required AttemptStatus status,
    @JsonKey(unknownEnumValue: AttemptOutcome.unknown) AttemptOutcome? outcome,
    required DateTime startedAt,
  }) = _LastAttemptView;

  factory LastAttemptView.fromJson(Map<String, Object?> json) => _$LastAttemptViewFromJson(json);
}

@freezed
abstract class ChallengeView with _$ChallengeView {
  const factory ChallengeView({
    required String id,
    @JsonKey(unknownEnumValue: ContentOrigin.unknown) required ContentOrigin origin,
    @JsonKey(unknownEnumValue: ChallengeStatus.unknown) required ChallengeStatus status,
    @JsonKey(unknownEnumValue: AsyncJobStatus.unknown) AsyncJobStatus? generationStatus,
    @JsonKey(unknownEnumValue: AsyncFailureCode.unknown) AsyncFailureCode? failureCode,
    DateTime? statusUpdatedAt,
    @JsonKey(unknownEnumValue: ChallengePurpose.unknown) required ChallengePurpose purpose,
    required bool isTransfer,
    String? title,
    required int difficulty,
    int? estimatedMinutes,
    String? scenario,
    String? prompt,
    @Default(<String>[]) List<String> constraints,
    required List<SkillRef> skills,
    @Default(<String>[]) List<String> transferTargetSkillCodes,
    required bool answerRevealed,
    List<String>? expectedConcepts,
    List<RubricItemView>? rubric,
    List<String>? commonMistakes,
    AiMeta? aiMeta,

    /// The user's STARTED/SUBMITTED/EVALUATED attempt (latest); null when there is none.
    String? activeAttemptId,
    required DateTime createdAt,
  }) = _ChallengeView;

  factory ChallengeView.fromJson(Map<String, Object?> json) => _$ChallengeViewFromJson(json);
}

@freezed
abstract class RubricItemView with _$RubricItemView {
  const factory RubricItemView({
    required String id,
    required String criterion,
    required int weightBp,
    @JsonKey(unknownEnumValue: RubricAxis.unknown) required RubricAxis axis,
  }) = _RubricItemView;

  factory RubricItemView.fromJson(Map<String, Object?> json) => _$RubricItemViewFromJson(json);
}
