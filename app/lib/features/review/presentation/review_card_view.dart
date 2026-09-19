import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/domain/review_card_progress.dart';
import 'package:devpilot_app/features/review/presentation/rating_button_row.dart';
import 'package:devpilot_app/features/review/presentation/review_labels.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// One review card: ① answer (prompt, optional answer, hint, reveal) and ② the revealed answer
/// with the rubric and the four ratings (docs/02 SCR-REVIEW-SESSION). The expected answer and the
/// rubric are not built before "답 확인" (AC-10 S3).
class ReviewCardView extends StatelessWidget {
  const ReviewCardView({
    super.key,
    required this.card,
    required this.submitting,
    required this.onAnswerChanged,
    required this.onHint,
    required this.onShowAnswerFirst,
    required this.onReveal,
    required this.onRate,
  });

  final ReviewCardProgress card;
  final bool submitting;
  final ValueChanged<String> onAnswerChanged;
  final VoidCallback onHint;
  final VoidCallback onShowAnswerFirst;
  final VoidCallback onReveal;
  final ValueChanged<ReviewRating> onRate;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final item = card.item;
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Wrap(
          spacing: AppSpacing.sm,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Text(
              l10n.reviewSessionCardMeta(item.skillName, item.reviewType.label(l10n)),
              style: textTheme.bodySmall,
            ),
            if (item.wasVariant) Chip(label: Text(l10n.reviewSessionVariant)),
          ],
        ),
        const SizedBox(height: AppSpacing.sm),
        SelectionArea(
          child: Text(
            item.prompt,
            key: const Key('review.session.prompt'),
            style: textTheme.titleMedium,
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        if (card.revealed)
          _RevealedAnswer(card: card, submitting: submitting, onRate: onRate)
        else
          _AnswerStep(
            card: card,
            onAnswerChanged: onAnswerChanged,
            onHint: onHint,
            onShowAnswerFirst: onShowAnswerFirst,
            onReveal: onReveal,
          ),
      ],
    );
  }
}

/// ① "내 답 (선택)", "힌트 보기", "모르겠어요, 정답 볼게요", "답 확인".
class _AnswerStep extends StatefulWidget {
  const _AnswerStep({
    required this.card,
    required this.onAnswerChanged,
    required this.onHint,
    required this.onShowAnswerFirst,
    required this.onReveal,
  });

  final ReviewCardProgress card;
  final ValueChanged<String> onAnswerChanged;
  final VoidCallback onHint;
  final VoidCallback onShowAnswerFirst;
  final VoidCallback onReveal;

  @override
  State<_AnswerStep> createState() => _AnswerStepState();
}

class _AnswerStepState extends State<_AnswerStep> {
  late final _controller = TextEditingController(text: widget.card.answerText);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final hint = widget.card.hintText;
    final tooLong = widget.card.answerText.length > InputRules.reviewAnswerMaxLength;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        CallbackShortcuts(
          // A-7: Ctrl/Cmd+Enter reveals from the field, Esc leaves the field.
          bindings: {
            const SingleActivator(LogicalKeyboardKey.enter, control: true): _revealIfValid,
            const SingleActivator(LogicalKeyboardKey.enter, meta: true): _revealIfValid,
            const SingleActivator(LogicalKeyboardKey.escape): () =>
                FocusScope.of(context).unfocus(),
          },
          child: TextField(
            key: const Key('review.session.answerField'),
            controller: _controller,
            minLines: 3,
            maxLines: 8,
            decoration: InputDecoration(
              labelText: l10n.reviewSessionAnswerLabel,
              hintText: l10n.reviewSessionAnswerHint,
              errorText: tooLong
                  ? l10n.validationMaxLength(InputRules.reviewAnswerMaxLength)
                  : null,
            ),
            onChanged: widget.onAnswerChanged,
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        _HintArea(hint: hint, canShowHint: widget.card.canShowHint, onHint: widget.onHint),
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton(
            key: const Key('review.session.showAnswerButton'),
            onPressed: widget.onShowAnswerFirst,
            child: Text(l10n.reviewSessionShowAnswerFirst),
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        FilledButton(
          key: const Key('review.session.revealButton'),
          onPressed: tooLong ? null : widget.onReveal,
          child: Text(l10n.reviewSessionReveal),
        ),
      ],
    );
  }

  void _revealIfValid() {
    if (widget.card.answerText.length <= InputRules.reviewAnswerMaxLength) {
      widget.onReveal();
    }
  }
}

/// "힌트 보기", replaced by the first rubric item once asked for (docs/02 SCR-REVIEW-SESSION ①).
class _HintArea extends StatelessWidget {
  const _HintArea({required this.hint, required this.canShowHint, required this.onHint});

  final String? hint;
  final bool canShowHint;
  final VoidCallback onHint;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final hintText = hint;
    if (hintText != null) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Semantics(
            liveRegion: true,
            child: Text(l10n.reviewSessionHintLabel, style: Theme.of(context).textTheme.titleSmall),
          ),
          Text(hintText, key: const Key('review.session.hintText')),
        ],
      );
    }
    if (!canShowHint) {
      return const SizedBox.shrink();
    }
    return Align(
      alignment: Alignment.centerLeft,
      child: TextButton(
        key: const Key('review.session.hintButton'),
        onPressed: onHint,
        child: Text(l10n.reviewSessionHint),
      ),
    );
  }
}

/// ② The typed answer, "정답" (focused so screen readers read it), "핵심 포인트", the ratings.
class _RevealedAnswer extends StatefulWidget {
  const _RevealedAnswer({required this.card, required this.submitting, required this.onRate});

  final ReviewCardProgress card;
  final bool submitting;
  final ValueChanged<ReviewRating> onRate;

  @override
  State<_RevealedAnswer> createState() => _RevealedAnswerState();
}

class _RevealedAnswerState extends State<_RevealedAnswer> {
  final _expectedFocus = FocusNode(debugLabel: 'review.session.expected');

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _expectedFocus.requestFocus();
      }
    });
  }

  @override
  void dispose() {
    _expectedFocus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final item = widget.card.item;
    final answer = widget.card.answerText.trim();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (answer.isNotEmpty) ...[
          SectionTitle(l10n.reviewSessionMyAnswer),
          SelectionArea(child: Text(widget.card.answerText)),
          const Divider(height: AppSpacing.xl),
        ],
        Focus(focusNode: _expectedFocus, child: SectionTitle(l10n.reviewSessionExpected)),
        SelectionArea(
          child: Text(item.expectedAnswer, key: const Key('review.session.expectedAnswer')),
        ),
        if (item.rubric.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.md),
          SectionTitle(l10n.reviewSessionRubric),
          for (final rubric in item.rubric)
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const ExcludeSemantics(child: Text('•  ')),
                Expanded(child: Text(rubric.criterion)),
              ],
            ),
        ],
        const SizedBox(height: AppSpacing.xl),
        SectionTitle(l10n.reviewSessionRatePrompt),
        const SizedBox(height: AppSpacing.sm),
        if (widget.submitting)
          SizedBox(
            height: RatingButtonRow.minHeight,
            child: Center(child: CircularProgressIndicator(semanticsLabel: l10n.commonSubmitting)),
          )
        else
          RatingButtonRow(enabled: true, onRate: widget.onRate),
      ],
    );
  }
}
