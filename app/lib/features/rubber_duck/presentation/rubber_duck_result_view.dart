import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/l10n/async_failure_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/badges.dart';
import 'package:devpilot_app/core/widgets/markdown_text.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/features/lesson/presentation/lesson_entry_button.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_models.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_conversation.dart';
import 'package:devpilot_app/features/rubber_duck/presentation/rubber_duck_state.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// ④ The docs' `RubberDuckSummaryView`: gaps with their review cards, what was explained well, the
/// one-line note and where to go next (docs/02 SCR-RUBBER-DUCK ④). A skipped summary keeps only
/// the conversation; an abandoned session shows its turns read-only.
class RubberDuckResultView extends StatelessWidget {
  const RubberDuckResultView({
    super.key,
    required this.data,
    required this.taskId,
    required this.onClose,
  });

  final RubberDuckScreenData data;
  final String? taskId;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final session = data.session!;
    final skipped = session.summarySkippedReason;
    final summary = session.summary;
    final title = session.targetTitle;
    return Column(
      key: const Key('rubberDuck.summary'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          title == null
              ? l10n.rubberDuckSummaryTurnsOnly(session.turnCount)
              : l10n.rubberDuckSummaryHeader(title, session.turnCount),
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: AppSpacing.md),
        if (session.status == RubberDuckStatus.abandoned)
          Text(l10n.rubberDuckAbandoned, key: const Key('rubberDuck.abandoned'))
        else if (skipped != null) ...[
          Text(l10n.rubberDuckSummarySkipped, key: const Key('rubberDuck.summarySkipped')),
          Text(skipped.message(l10n), style: Theme.of(context).textTheme.bodySmall),
        ] else if (summary != null)
          _SummaryBody(summary: summary, session: session, completion: data.completion),
        const SizedBox(height: AppSpacing.md),
        if (session.turns.isNotEmpty)
          ExpansionTile(
            key: const Key('rubberDuck.history'),
            tilePadding: EdgeInsets.zero,
            title: Text(l10n.rubberDuckSummaryHistory(session.turns.length)),
            children: [RubberDuckConversation(turns: session.turns)],
          ),
        const SizedBox(height: AppSpacing.lg),
        _SummaryButtons(
          taskId: taskId,
          skillId: session.skill?.id,
          hasGaps: summary?.gaps.isNotEmpty ?? false,
          onClose: onClose,
        ),
      ],
    );
  }
}

class _SummaryBody extends StatelessWidget {
  const _SummaryBody({required this.summary, required this.session, required this.completion});

  final RubberDuckSummaryView summary;
  final RubberDuckSessionView session;
  final RubberDuckCompleteResponse? completion;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final note = summary.overallNote;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(child: SectionTitle(l10n.rubberDuckSummaryGaps(summary.gaps.length))),
            const AiBadge(),
          ],
        ),
        if (summary.gaps.isEmpty) Text(l10n.rubberDuckSummaryNoGaps),
        for (final (index, gap) in summary.gaps.indexed)
          _GapCard(gap: gap, number: index + 1, skillId: session.skill?.id),
        if (summary.confirmed.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.md),
          SectionTitle(l10n.rubberDuckSummaryConfirmed),
          for (final item in summary.confirmed) MarkdownText('- $item'),
        ],
        if (note != null && note.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.md),
          Row(
            children: [
              Expanded(child: SectionTitle(l10n.rubberDuckSummaryNote)),
              const AiBadge(),
            ],
          ),
          MarkdownText(note, textKey: const Key('rubberDuck.overallNote')),
        ],
        const SizedBox(height: AppSpacing.md),
        _SummaryNotes(summary: summary, session: session, completion: completion),
      ],
    );
  }
}

/// "무엇을 몰랐나" · "왜 중요한가" · "복습 질문" and the link to its review card.
class _GapCard extends StatelessWidget {
  const _GapCard({required this.gap, required this.number, required this.skillId});

  final RubberDuckGapView gap;

  /// 1-based position: keeps the "복습 카드 보기" buttons apart for screen readers.
  final int number;
  final String? skillId;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final titleStyle = Theme.of(context).textTheme.titleSmall;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(l10n.rubberDuckSummaryMissed, style: titleStyle),
            Text(gap.whatWasMissed),
            const SizedBox(height: AppSpacing.sm),
            Text(l10n.rubberDuckSummaryWhy, style: titleStyle),
            Text(gap.whyItMatters),
            const SizedBox(height: AppSpacing.sm),
            Text(l10n.rubberDuckSummaryReviewQuestion, style: titleStyle),
            Text(gap.reviewQuestion),
            if (gap.reviewItemId != null)
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                  key: Key('rubberDuck.viewCard.$number'),
                  onPressed: () => context.go(AppRoutes.reviewItemsFor(skillId: skillId)),
                  child: Text(
                    l10n.rubberDuckSummaryViewCard,
                    semanticsLabel: l10n.rubberDuckSummaryViewCardNumbered(number),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// Cards created or pulled forward, and the explanation evidence line (RD-5, RD-7).
class _SummaryNotes extends StatelessWidget {
  const _SummaryNotes({required this.summary, required this.session, required this.completion});

  final RubberDuckSummaryView summary;
  final RubberDuckSessionView session;
  final RubberDuckCompleteResponse? completion;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final created = completion?.createdReviewItemCount;
    final linked = summary.gaps.where((gap) => gap.reviewItemId != null).length;
    final pulled = created == null ? 0 : linked - created;
    final lines = [
      if (created != null && created > 0) l10n.rubberDuckSummaryCardsCreated(created),
      if (pulled > 0) l10n.rubberDuckSummaryCardsPulled(pulled),
      if (session.skill == null)
        l10n.rubberDuckSummaryNoSkill
      else if (summary.gaps.isEmpty && session.turnCount >= 3)
        l10n.rubberDuckSummaryEvidence,
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final line in lines)
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(Icons.info_outline, size: 16),
              const SizedBox(width: AppSpacing.xs),
              Expanded(child: Text(line)),
            ],
          ),
      ],
    );
  }
}

/// One primary: back to Today's completion sheet with a task, otherwise "닫기"; "복습하러 가기"
/// when gaps became cards.
class _SummaryButtons extends StatelessWidget {
  const _SummaryButtons({
    required this.taskId,
    required this.skillId,
    required this.hasGaps,
    required this.onClose,
  });

  final String? taskId;

  /// 노트로 가는 길. 러버덕은 끝까지 답을 말하지 않으므로 답은 여기에만 있다.
  final String? skillId;

  final bool hasGaps;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final task = taskId;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (task != null)
          FilledButton(
            key: const Key('rubberDuck.toTodayButton'),
            onPressed: () => context.go(AppRoutes.todayComplete(task)),
            child: Text(l10n.rubberDuckToToday),
          )
        else
          FilledButton(
            key: const Key('rubberDuck.closeButton'),
            onPressed: onClose,
            child: Text(l10n.rubberDuckClose),
          ),
        // 러버덕은 질문만 한다 — 여기서 끝나면 답을 모른 채로 끝난다. 노트가 있으면 답까지 가는 길을 남긴다.
        LessonEntryButton(
          buttonKey: const Key('rubberDuck.toLessonButton'),
          skillId: skillId,
          label: l10n.rubberDuckToLesson,
        ),
        if (hasGaps)
          TextButton(
            key: const Key('rubberDuck.toReviewButton'),
            onPressed: () => context.go(AppRoutes.review),
            child: Text(l10n.rubberDuckToReview),
          ),
      ],
    );
  }
}
