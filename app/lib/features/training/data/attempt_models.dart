import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'attempt_models.freezed.dart';
part 'attempt_models.g.dart';

// Attempt view records and requests (docs/05 §2.6, §10.1, §10.7~§10.9).

@freezed
abstract class AttemptView with _$AttemptView {
  const AttemptView._();

  const factory AttemptView({
    required String id,
    required String challengeId,
    String? challengeTitle,
    required int difficulty,
    @JsonKey(unknownEnumValue: ChallengePurpose.unknown) required ChallengePurpose purpose,
    @JsonKey(unknownEnumValue: AttemptStatus.unknown) required AttemptStatus status,
    String? selfExplanation,
    required bool selfExplanationSkipped,
    required int submissionCount,
    required int maxSubmissions,
    @JsonKey(unknownEnumValue: HintLevel.unknown) required HintLevel maxHintLevel,

    /// Disclosed hints, level ASC.
    @Default(<DisclosedHintView>[]) List<DisclosedHintView> hints,
    @JsonKey(unknownEnumValue: EvaluatedOutcome.unknown) EvaluatedOutcome? evaluatedOutcome,
    @JsonKey(unknownEnumValue: AttemptOutcome.unknown) AttemptOutcome? outcome,
    int? rubricCoverageBp,
    int? explanationCoverageBp,

    /// submissionNo ASC.
    @Default(<SubmissionView>[]) List<SubmissionView> submissions,
    @Default(<ScheduledReviewView>[]) List<ScheduledReviewView> reviewScheduled,
    String? evidenceSourceEventId,
    required DateTime startedAt,
    DateTime? completedAt,
    required int version,
  }) = _AttemptView;

  factory AttemptView.fromJson(Map<String, Object?> json) => _$AttemptViewFromJson(json);

  SubmissionView? get latestSubmission => submissions.isEmpty ? null : submissions.last;

  /// A self-explanation was saved or skipped: the hint ladder and submission are unlocked.
  bool get explanationRecorded => selfExplanation != null || selfExplanationSkipped;

  /// The latest submission is still being evaluated (PENDING/RUNNING).
  bool get evaluating => latestSubmission?.evaluationStatus.isActive ?? false;
}

@freezed
abstract class SubmissionView with _$SubmissionView {
  const factory SubmissionView({
    required int submissionNo,
    String? answerText,
    String? code,
    @JsonKey(unknownEnumValue: CodeLanguage.unknown) CodeLanguage? language,
    @JsonKey(unknownEnumValue: AsyncJobStatus.unknown) required AsyncJobStatus evaluationStatus,
    @JsonKey(unknownEnumValue: AsyncFailureCode.unknown) AsyncFailureCode? failureCode,
    DateTime? statusUpdatedAt,
    required bool retryable,
    required DateTime submittedAt,
    DateTime? evaluatedAt,

    /// Only when `evaluationStatus = COMPLETED`.
    EvaluationView? evaluation,
    AiMeta? aiMeta,
  }) = _SubmissionView;

  factory SubmissionView.fromJson(Map<String, Object?> json) => _$SubmissionViewFromJson(json);
}

@freezed
abstract class EvaluationView with _$EvaluationView {
  const factory EvaluationView({
    @JsonKey(unknownEnumValue: EvaluatedOutcome.unknown) required EvaluatedOutcome evaluatedOutcome,
    required int rubricCoverageBp,
    int? explanationCoverageBp,

    /// In the challenge rubric order.
    required List<RubricResultView> rubric,
    @Default(<String>[]) List<String> misconceptions,
    String? followUpQuestion,
  }) = _EvaluationView;

  factory EvaluationView.fromJson(Map<String, Object?> json) => _$EvaluationViewFromJson(json);
}

@freezed
abstract class RubricResultView with _$RubricResultView {
  const factory RubricResultView({
    required String id,
    required String criterion,
    @JsonKey(unknownEnumValue: RubricAxis.unknown) required RubricAxis axis,
    required int weightBp,
    required bool met,
    String? evidenceQuote,
  }) = _RubricResultView;

  factory RubricResultView.fromJson(Map<String, Object?> json) => _$RubricResultViewFromJson(json);
}

@freezed
abstract class ScheduledReviewView with _$ScheduledReviewView {
  const factory ScheduledReviewView({
    required String reviewItemId,
    required String skillCode,
    required String dueDate,
  }) = _ScheduledReviewView;

  factory ScheduledReviewView.fromJson(Map<String, Object?> json) =>
      _$ScheduledReviewViewFromJson(json);
}

@freezed
abstract class DisclosedHintView with _$DisclosedHintView {
  const factory DisclosedHintView({
    @JsonKey(unknownEnumValue: HintLevel.unknown) required HintLevel level,
    required String content,
    @JsonKey(unknownEnumValue: HintContentOrigin.unknown) required HintContentOrigin contentOrigin,
    required DateTime disclosedAt,
  }) = _DisclosedHintView;

  factory DisclosedHintView.fromJson(Map<String, Object?> json) =>
      _$DisclosedHintViewFromJson(json);
}

/// `HintView` of `POST …/hints` (docs/05 §2.6). [level] may be lower than requested (HL-1).
@freezed
abstract class HintView with _$HintView {
  const factory HintView({
    @JsonKey(unknownEnumValue: HintLevel.unknown) required HintLevel level,
    required String content,
    @JsonKey(unknownEnumValue: HintContentOrigin.unknown) required HintContentOrigin contentOrigin,
    @JsonKey(unknownEnumValue: HintLevel.unknown) required HintLevel maxHintLevel,
    @JsonKey(unknownEnumValue: HintLevel.unknown)
    @Default(<HintLevel>[])
    List<HintLevel> skippedLevels,
    AiMeta? aiMeta,
  }) = _HintView;

  factory HintView.fromJson(Map<String, Object?> json) => _$HintViewFromJson(json);
}

/// `HintRequest` for a challenge attempt: `skipSelfExplanation` is coach-only and never sent.
@freezed
abstract class HintRequest with _$HintRequest {
  const factory HintRequest({
    required HintLevel requestedLevel,
    required bool acknowledgeEvidenceImpact,
    required bool giveUp,
  }) = _HintRequest;

  factory HintRequest.fromJson(Map<String, Object?> json) => _$HintRequestFromJson(json);
}

/// `SelfExplanationRequest`: exactly one of a non-blank [text] or `skipped = true`.
@freezed
abstract class SelfExplanationRequest with _$SelfExplanationRequest {
  const factory SelfExplanationRequest({String? text, required bool skipped}) =
      _SelfExplanationRequest;

  factory SelfExplanationRequest.fromJson(Map<String, Object?> json) =>
      _$SelfExplanationRequestFromJson(json);
}

/// `SubmissionRequest`: answer text and/or code; code needs a language (docs/05 §10.9).
@freezed
abstract class SubmissionRequest with _$SubmissionRequest {
  const factory SubmissionRequest({
    String? answerText,
    String? code,
    CodeLanguage? language,
  }) = _SubmissionRequest;

  factory SubmissionRequest.fromJson(Map<String, Object?> json) =>
      _$SubmissionRequestFromJson(json);
}
