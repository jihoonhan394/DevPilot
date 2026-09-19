import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/presentation/review_labels.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// `RatingButtonRow`: 다시 · 어려움 · 알맞음 · 쉬움 in one row, each at least 56 high
/// (docs/02 SCR-REVIEW-SESSION ②). Desktop shows the shortcut digit under the label; large text
/// or a very narrow screen switches to a 2×2 grid (A-10).
class RatingButtonRow extends StatelessWidget {
  const RatingButtonRow({super.key, required this.enabled, required this.onRate});

  static const minHeight = 56.0;

  final bool enabled;
  final ValueChanged<ReviewRating> onRate;

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    final largeText = MediaQuery.textScalerOf(context).scale(1) > 1.3;
    final showDigits = width >= AppBreakpoints.desktop;
    final buttons = [
      for (final (index, rating) in ReviewRating.known.indexed)
        _RatingButton(
          rating: rating,
          shortcut: index + 1,
          showDigit: showDigits,
          onPressed: enabled ? () => onRate(rating) : null,
        ),
    ];
    if (largeText || width < 360) {
      return Column(
        children: [
          Row(children: [for (final button in buttons.take(2)) Expanded(child: button)]),
          const SizedBox(height: AppSpacing.sm),
          Row(children: [for (final button in buttons.skip(2)) Expanded(child: button)]),
        ],
      );
    }
    return Row(children: [for (final button in buttons) Expanded(child: button)]);
  }
}

class _RatingButton extends StatelessWidget {
  const _RatingButton({
    required this.rating,
    required this.shortcut,
    required this.showDigit,
    required this.onPressed,
  });

  final ReviewRating rating;
  final int shortcut;
  final bool showDigit;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xs / 2),
      child: OutlinedButton(
        key: Key('review.session.rate.${rating.name}'),
        style: OutlinedButton.styleFrom(
          minimumSize: const Size.fromHeight(RatingButtonRow.minHeight),
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xs),
        ),
        onPressed: onPressed,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(rating.label(l10n), textAlign: TextAlign.center),
            if (showDigit) ExcludeSemantics(child: Text('$shortcut', style: textTheme.labelSmall)),
          ],
        ),
      ),
    );
  }
}
