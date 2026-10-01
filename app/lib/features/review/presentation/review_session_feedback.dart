import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_models.dart';
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
      if (response.rubricResults.isNotEmpty) {
        // 항목별 채점은 4초짜리 스낵바에 담기지 않는다 — 읽고 닫을 시트로 보여 준다.
        await _showEvaluation(context, response);
        if (!context.mounted) {
          return;
        }
      }
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

/// AI 채점 결과 (docs/05 §11.3): 루브릭 항목마다 짚었는지와 한 줄 총평. 저장하지 않는 값이라 이 자리에서만 보인다.
///
/// 등급 조정은 서버가 이미 했고(`adjustedBy`), 이 시트는 **왜 그렇게 됐는지**를 보여 준다.
Future<void> _showEvaluation(BuildContext context, ReviewAnswerResponse response) {
  final l10n = AppLocalizations.of(context);
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    builder: (sheetContext) => SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Semantics(
              header: true,
              child: Text(
                l10n.reviewSessionEvaluationTitle,
                key: const Key('review.session.evaluationTitle'),
                style: Theme.of(sheetContext).textTheme.titleMedium,
              ),
            ),
            const SizedBox(height: AppSpacing.md),
            for (final result in response.rubricResults)
              Padding(
                padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // 아이콘은 장식이고, 짚었는지는 아래 글로 읽어 준다.
                    ExcludeSemantics(
                      child: Icon(result.met ? Icons.check : Icons.remove, size: 20),
                    ),
                    const SizedBox(width: AppSpacing.sm),
                    Expanded(
                      child: Text(
                        result.met
                            ? l10n.reviewSessionRubricMet(result.criterion)
                            : l10n.reviewSessionRubricMissed(result.criterion),
                      ),
                    ),
                  ],
                ),
              ),
            if (response.evaluationFeedback case final feedback?
                when feedback.trim().isNotEmpty) ...[
              const Divider(height: AppSpacing.xl),
              Text(feedback, key: const Key('review.session.evaluationFeedback')),
            ],
            const SizedBox(height: AppSpacing.lg),
            FilledButton(
              key: const Key('review.session.evaluationClose'),
              onPressed: () => Navigator.of(sheetContext).pop(),
              child: Text(l10n.commonClose),
            ),
          ],
        ),
      ),
    ),
  );
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
