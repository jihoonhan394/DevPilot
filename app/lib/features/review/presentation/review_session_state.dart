import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_models.dart';
import 'package:devpilot_app/features/review/domain/review_card_progress.dart';
import 'package:flutter/foundation.dart';

/// SCR-REVIEW-SESSION: the cards fixed at entry, the current one, and the session this screen
/// started (docs/02 SCR-REVIEW-SESSION, §4.3).
@immutable
final class ReviewSessionState {
  const ReviewSessionState({
    required this.planDate,
    required this.cards,
    required this.current,
    this.index = 0,
    this.finalRatings = const [],
    this.struggled = const [],
    this.submitting = false,
    this.sessionId,
    this.sessionStartedAt,
    this.recorded = false,
  });

  final String planDate;

  /// `GET /reviews/due` order (RV-INTERLEAVE); never re-sorted here.
  final List<DueReviewItemView> cards;

  /// Position of [current]; equal to `cards.length` once every card was handled.
  final int index;

  /// The card on screen; null on the summary.
  final ReviewCardProgress? current;

  /// Final rating of every answered card, in answer order.
  final List<ReviewRating> finalRatings;

  /// Cards whose final rating was AGAIN or HARD, in answer order: the summary offers to explain
  /// them with the rubber duck (docs/02 SCR-REVIEW-SESSION ④, U-9).
  final List<DueReviewItemView> struggled;
  final bool submitting;

  /// The learning session this screen started, or the running one of its Today task.
  final String? sessionId;
  final DateTime? sessionStartedAt;

  /// The session was completed or abandoned here.
  final bool recorded;

  bool get finished => current == null;

  int get answeredCount => finalRatings.length;

  /// Leaving asks for the partial record first (docs/02 SCR-REVIEW-SESSION "✕ 또는 뒤로가기").
  bool get needsRecordOnExit => sessionId != null && answeredCount > 0 && !recorded;

  /// Cards per final rating for the summary, in rating order.
  Map<ReviewRating, int> get ratingCounts => {
    for (final rating in ReviewRating.known)
      rating: finalRatings.where((finalRating) => finalRating == rating).length,
  };

  ReviewSessionState copyWith({
    int? index,
    ReviewCardProgress? Function()? current,
    List<ReviewRating>? finalRatings,
    List<DueReviewItemView>? struggled,
    bool? submitting,
    String? Function()? sessionId,
    DateTime? Function()? sessionStartedAt,
    bool? recorded,
  }) => ReviewSessionState(
    planDate: planDate,
    cards: cards,
    index: index ?? this.index,
    current: current == null ? this.current : current(),
    finalRatings: finalRatings ?? this.finalRatings,
    struggled: struggled ?? this.struggled,
    submitting: submitting ?? this.submitting,
    sessionId: sessionId == null ? this.sessionId : sessionId(),
    sessionStartedAt: sessionStartedAt == null ? this.sessionStartedAt : sessionStartedAt(),
    recorded: recorded ?? this.recorded,
  );
}

/// How a rating tap ended.
sealed class ReviewRateOutcome {
  const ReviewRateOutcome();
}

/// Saved; the adjustment note shows [selfRating] against the response.
final class ReviewRated extends ReviewRateOutcome {
  const ReviewRated({required this.selfRating, required this.response});

  final ReviewRating selfRating;
  final ReviewAnswerResponse response;
}

/// `409 INVALID_STATE_TRANSITION` / `CONCURRENT_MODIFICATION`: the card changed elsewhere and was
/// skipped (toast `review.session.cardChanged`).
final class ReviewCardSkipped extends ReviewRateOutcome {
  const ReviewCardSkipped();
}

/// Not saved; the card and the choice stay (toast `review.saveFailed` + "다시 시도").
final class ReviewRateFailed extends ReviewRateOutcome {
  const ReviewRateFailed(this.error);

  final Object error;
}

final class ReviewRateIgnored extends ReviewRateOutcome {
  const ReviewRateIgnored();
}
