import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/presentation/review_labels.dart';
import 'package:devpilot_app/features/review/presentation/review_session_controller.dart';
import 'package:devpilot_app/features/review/presentation/review_session_state.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// A rating tap: saves the answer, then shows the adjustment note, the skipped-card toast, or the
/// save failure with "다시 시도" (docs/02 SCR-REVIEW-SESSION ③, "상태").
Future<void> rateReviewCard(
  BuildContext context,
  WidgetRef ref,
  String? taskId,
  ReviewRating rating,
) async {
  final l10n = AppLocalizations.of(context);
  final outcome = await ref.read(reviewSessionControllerProvider(taskId).notifier).rate(rating);
  if (!context.mounted) {
    return;
  }
  switch (outcome) {
    case ReviewRated(:final selfRating, :final response):
      _showAdjustment(context, selfRating, response.finalRating, response.adjustedBy, [
        if (response.leechDetected) l10n.reviewAdjustLeech,
        if (response.evaluationSkippedReason != null) l10n.reviewAdjustEvaluationSkipped,
        l10n.reviewAdjustNextDue(formatPlanDate(LocalDate.parse(response.nextDueDate), l10n)),
      ]);
    case ReviewCardSkipped():
      showToast(context, l10n.reviewSessionCardChanged);
    case ReviewRateFailed():
      showToast(
        context,
        l10n.reviewSaveFailed,
        actionLabel: l10n.commonErrorRetry,
        onAction: () => rateReviewCard(context, ref, taskId, rating),
      );
    case ReviewRateIgnored():
      return;
  }
}

/// ③ "'알맞음' → '다시'로 조정했어요" + reasons + "다음 복습: {date}" for 4 s; without an
/// adjustment only the next date, for 2 s. Text only, no color coding (AC-10 S4).
void _showAdjustment(
  BuildContext context,
  ReviewRating selfRating,
  ReviewRating finalRating,
  List<RatingAdjustment> adjustedBy,
  List<String> trailingLines,
) {
  final l10n = AppLocalizations.of(context);
  final adjusted = adjustedBy.isNotEmpty;
  final lines = [
    if (adjusted) l10n.reviewAdjustChanged(selfRating.label(l10n), finalRating.label(l10n)),
    for (final adjustment in adjustedBy) adjustment.text(l10n),
    ...trailingLines,
  ];
  final messenger = ScaffoldMessenger.maybeOf(context);
  messenger
    ?..hideCurrentSnackBar()
    ..showSnackBar(
      SnackBar(
        key: const Key('review.session.adjustment'),
        behavior: SnackBarBehavior.floating,
        duration: Duration(seconds: adjusted || trailingLines.length > 1 ? 4 : 2),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [for (final line in lines) Text(line)],
        ),
      ),
    );
}
