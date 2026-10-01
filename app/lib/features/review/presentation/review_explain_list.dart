import 'package:devpilot_app/app/explain_with_duck_button.dart';
import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/features/review/data/review_models.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// "헷갈린 카드, 말로 설명해 볼까요?": up to three AGAIN/HARD cards, in the order they came, each
/// with "설명해 보기" to the rubber duck (docs/02 SCR-REVIEW-SESSION ④). Hidden while the AI is
/// off.
class ReviewExplainList extends ConsumerWidget {
  const ReviewExplainList({super.key, required this.cards});

  static const maxCards = 3;

  final List<DueReviewItemView> cards;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (cards.isEmpty || !ref.watch(aiStatusProvider).allowsAi) {
      return const SizedBox.shrink();
    }
    final l10n = AppLocalizations.of(context);
    return Column(
      key: const Key('review.summary.explainList'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.reviewSummaryExplainTitle),
        const SizedBox(height: AppSpacing.sm),
        for (final (index, card) in cards.take(maxCards).indexed)
          Padding(
            padding: const EdgeInsets.only(bottom: AppSpacing.sm),
            child: Row(
              children: [
                Expanded(child: Text(card.prompt, maxLines: 1, overflow: TextOverflow.ellipsis)),
                const SizedBox(width: AppSpacing.sm),
                IntrinsicWidth(
                  child: ExplainWithDuckButton(
                    buttonKey: Key('review.summary.explain.${index + 1}'),
                    label: l10n.reviewSummaryExplain,
                    semanticsLabel: l10n.reviewSummaryExplainNumbered(index + 1),
                    style: ExplainButtonStyle.outlined,
                    launch: RubberDuckLaunch(
                      targetType: RubberDuckTargetType.reviewItem,
                      targetId: card.reviewItemId,
                      skillCode: card.skillCode,
                      preview: RubberDuckTargetPreview(title: card.skillName, summary: card.prompt),
                    ),
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}
