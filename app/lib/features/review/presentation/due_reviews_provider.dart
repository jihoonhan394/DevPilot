import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/features/review/data/review_models.dart';
import 'package:devpilot_app/features/review/data/review_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// `GET /reviews/due`, shared by SCR-REVIEW-HOME and SCR-REVIEW-SESSION (docs/02 §3.6).
///
/// Kept while the app runs so the session uses the list the home screen just showed; the home
/// screen re-reads it on entry and the session invalidates it when it ends. A new sign-in drops it.
final dueReviewsProvider = FutureProvider<DueReviewsResponse>((ref) {
  ref.watch(authStateProvider.select((authState) => authState.accessToken));
  return ref.watch(reviewRepositoryProvider).fetchDue();
});
