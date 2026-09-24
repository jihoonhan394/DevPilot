import 'package:devpilot_app/app/explain_with_duck_button.dart' show ExplainButtonStyle;
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/widgets/markdown_text.dart';
import 'package:devpilot_app/core/widgets/status_badge.dart';
import 'package:devpilot_app/features/lesson/presentation/lesson_entry_button.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';
import 'package:devpilot_app/features/today/presentation/concept_reading_section.dart';
import 'package:devpilot_app/features/today/presentation/main_task_actions.dart';
import 'package:devpilot_app/features/today/presentation/today_state.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// `MainTaskCard`: type, title, estimate, description and the reasons of the main task, with
/// the footer of its status (docs/02 SCR-TODAY "main task 상태별 카드 하단").
class MainTaskCard extends StatelessWidget {
  const MainTaskCard({super.key, required this.task, required this.data});

  final MainTaskView task;
  final TodayScreenData data;

  @override
  Widget build(BuildContext context) {
    final description = task.description;
    final planned = task.status == TaskStatus.planned;
    final readingKey = task.readingKey;
    final checklist = task.checklist;
    return Card(
      key: const Key('today.mainCard'),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _TaskHeading(task: task),
            if (description != null && description.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.sm),
              _TaskDescription(
                text: description,
                // Started tasks stay on Today with the description open (docs/02 SCR-TODAY).
                expanded: task.status == TaskStatus.inProgress,
              ),
            ],
            if (task.taskType == TaskType.readCode)
              Text(
                AppLocalizations.of(context).todayReadCodeLocal,
                key: const Key('today.readCodeLocal'),
                style: Theme.of(context).textTheme.bodySmall,
              ),
            // READING with a concept reading: what to read, where to open it and the three
            // points to answer (docs/02 SCR-TODAY). Hidden when the skill has no material.
            if (task.taskType == TaskType.reading && readingKey != null)
              ConceptReadingSection(readingKey: readingKey),
            // 시작 전에는 무엇을 정하고 들어가는지, 하는 중에는 무엇을 보고 끝내는지 (docs/19 §3.11).
            // 마친 과제에는 붙이지 않는다 — 확인할 것이 남아 있지 않다.
            if (checklist != null && planned)
              _ChecklistSection(
                sectionKey: const Key('today.checklistBefore'),
                title: AppLocalizations.of(context).todayChecklistBefore,
                items: checklist.before,
              ),
            if (checklist != null && task.status == TaskStatus.inProgress)
              _ChecklistSection(
                sectionKey: const Key('today.checklistAfter'),
                title: AppLocalizations.of(context).todayChecklistAfter,
                items: checklist.after,
              ),
            if (planned && task.reasons.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.md),
              _ReasonList(reasons: task.reasons),
            ],
            // 문제부터 나오지 않게 노트로 가는 길을 과제 옆에 둔다 (docs/01 §4 Teach before test).
            // 이미 마친 과제에는 없다 — 그때 필요한 것은 복습이다.
            if (task.status != TaskStatus.completed)
              LessonEntryButton(
                buttonKey: const Key('today.lessonButton'),
                skillId: task.skillId,
                label: AppLocalizations.of(context).todayLearnConcept,
                style: ExplainButtonStyle.outlined,
              ),
            const SizedBox(height: AppSpacing.lg),
            MainTaskActions(task: task, data: data),
          ],
        ),
      ),
    );
  }
}

/// 확인 목록 한 덩어리. 질문형 문장이라 체크 상태를 저장하지 않는다 — 읽고 답하면 끝이다(docs/19 §3.11).
class _ChecklistSection extends StatelessWidget {
  const _ChecklistSection({
    required this.sectionKey,
    required this.title,
    required this.items,
  });

  final Key sectionKey;
  final String title;
  final List<String> items;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Padding(
      key: sectionKey,
      padding: const EdgeInsets.only(top: AppSpacing.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: textTheme.labelLarge),
          const SizedBox(height: AppSpacing.xs),
          for (final item in items)
            Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.xs),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('· ', style: textTheme.bodyMedium),
                  Expanded(child: Text(item, style: textTheme.bodyMedium)),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

/// Type badge, title and estimate, read by screen readers as one group (docs/02 SCR-TODAY).
class _TaskHeading extends StatelessWidget {
  const _TaskHeading({required this.task});

  final MainTaskView task;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final skillName = task.skillName;
    return MergeSemantics(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(
            spacing: AppSpacing.sm,
            runSpacing: AppSpacing.xs,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              StatusBadge(label: task.taskType.label(l10n), tone: AppTone.primary),
              if (skillName != null) Text(skillName, style: textTheme.bodySmall),
            ],
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(task.title, key: const Key('today.mainTitle'), style: textTheme.titleMedium),
          const SizedBox(height: AppSpacing.xs),
          Text(
            l10n.todayMainEstimated(formatMinutes(task.estimatedMinutes, l10n)),
            style: textTheme.bodyMedium,
          ),
        ],
      ),
    );
  }
}

/// Description folded to two lines with a toggle.
class _TaskDescription extends StatefulWidget {
  const _TaskDescription({required this.text, required this.expanded});

  final String text;
  final bool expanded;

  @override
  State<_TaskDescription> createState() => _TaskDescriptionState();
}

class _TaskDescriptionState extends State<_TaskDescription> {
  late bool _expanded = widget.expanded;

  @override
  void didUpdateWidget(_TaskDescription oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.expanded && !oldWidget.expanded) {
      _expanded = true;
    }
  }

  @override
  Widget build(BuildContext context) {
    final localizations = MaterialLocalizations.of(context);
    return InkWell(
      key: const Key('today.mainDescription'),
      onTap: () => setState(() => _expanded = !_expanded),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            // Folded: two plain lines, because maxLines needs one Text. Opened: Markdown, so code in
            // the description becomes a code block (docs/02 §2.4).
            child: _expanded
                ? MarkdownText(widget.text)
                : Text(widget.text, maxLines: 2, overflow: TextOverflow.ellipsis),
          ),
          Icon(
            _expanded ? Icons.expand_less : Icons.expand_more,
            semanticLabel: _expanded
                ? localizations.expandedIconTapHint
                : localizations.collapsedIconTapHint,
          ),
        ],
      ),
    );
  }
}

/// "왜 오늘?" with the server's reason texts (1~3, docs/06 §5.8).
class _ReasonList extends StatelessWidget {
  const _ReasonList({required this.reasons});

  final List<ReasonView> reasons;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      key: const Key('today.reasons'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Semantics(
          header: true,
          child: Text(l10n.todayReasonsTitle, style: Theme.of(context).textTheme.titleSmall),
        ),
        const SizedBox(height: AppSpacing.xs),
        for (final reason in reasons)
          Padding(
            padding: const EdgeInsets.only(bottom: AppSpacing.xs),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const ExcludeSemantics(child: Text('•  ')),
                Expanded(child: Text(reason.text)),
              ],
            ),
          ),
      ],
    );
  }
}
