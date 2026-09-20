import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/widgets/status_badge.dart';
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
            if (planned && task.reasons.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.md),
              _ReasonList(reasons: task.reasons),
            ],
            const SizedBox(height: AppSpacing.lg),
            MainTaskActions(task: task, data: data),
          ],
        ),
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
            child: Text(
              widget.text,
              maxLines: _expanded ? null : 2,
              overflow: _expanded ? TextOverflow.visible : TextOverflow.ellipsis,
            ),
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
