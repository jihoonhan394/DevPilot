import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';

// Labels of the review enums (docs/02 §3.1 "enum 라벨", SCR-REVIEW-SESSION 문구).

extension ReviewRatingLabel on ReviewRating {
  String label(AppLocalizations l10n) => switch (this) {
    ReviewRating.again => l10n.enumReviewRatingAgain,
    ReviewRating.hard => l10n.enumReviewRatingHard,
    ReviewRating.good => l10n.enumReviewRatingGood,
    ReviewRating.easy => l10n.enumReviewRatingEasy,
    ReviewRating.unknown => l10n.enumUnknown,
  };
}

extension ReviewTypeLabel on ReviewType {
  String label(AppLocalizations l10n) => switch (this) {
    ReviewType.recall => l10n.enumReviewTypeRecall,
    ReviewType.bugSpot => l10n.enumReviewTypeBugSpot,
    ReviewType.explain => l10n.enumReviewTypeExplain,
    ReviewType.choice => l10n.enumReviewTypeChoice,
    ReviewType.unknown => l10n.enumUnknown,
  };
}

/// Why the rating was lowered (`review.adjust.<RatingAdjustment>`).
extension RatingAdjustmentText on RatingAdjustment {
  String text(AppLocalizations l10n) => switch (this) {
    RatingAdjustment.evaluatedIncorrect => l10n.reviewAdjustEvaluatedIncorrect,
    RatingAdjustment.evaluatedPartial => l10n.reviewAdjustEvaluatedPartial,
    RatingAdjustment.hintCapAgain => l10n.reviewAdjustHintCapAgain,
    RatingAdjustment.hintCapHard => l10n.reviewAdjustHintCapHard,
    RatingAdjustment.hintCapGood => l10n.reviewAdjustHintCapGood,
    RatingAdjustment.unknown => l10n.enumUnknown,
  };
}

extension ReviewItemStatusLabel on ReviewItemStatus {
  String label(AppLocalizations l10n) => switch (this) {
    ReviewItemStatus.active => l10n.enumReviewItemStatusActive,
    ReviewItemStatus.suspended => l10n.enumReviewItemStatusSuspended,
    ReviewItemStatus.archived => l10n.enumReviewItemStatusArchived,
    ReviewItemStatus.unknown => l10n.enumUnknown,
  };
}

/// "출처 라벨" of SCR-REVIEW-ITEMS.
extension ReviewItemSourceLabel on ReviewItemSourceType {
  String label(AppLocalizations l10n) => switch (this) {
    ReviewItemSourceType.seedCard => l10n.reviewItemsSourceSeedCard,
    ReviewItemSourceType.manual => l10n.reviewItemsSourceManual,
    ReviewItemSourceType.challengeAttempt => l10n.reviewItemsSourceChallengeAttempt,
    ReviewItemSourceType.coachFinding => l10n.reviewItemsSourceCoachFinding,
    ReviewItemSourceType.evidence => l10n.reviewItemsSourceEvidence,
    ReviewItemSourceType.rubberDuck => l10n.reviewItemsSourceRubberDuck,
    ReviewItemSourceType.unknown => l10n.enumUnknown,
  };
}
