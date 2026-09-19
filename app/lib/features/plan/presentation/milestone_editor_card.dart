import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/features/plan/domain/replan_draft.dart';
import 'package:devpilot_app/features/plan/presentation/milestone_editor_parts.dart';
import 'package:devpilot_app/features/plan/presentation/replan_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// `MilestoneEditorCard` of SCR-REPLAN: title, period, priority, skills, memo, ↑/↓ and delete.
class MilestoneEditorCard extends ConsumerStatefulWidget {
  const MilestoneEditorCard({
    super.key,
    required this.milestone,
    required this.index,
    required this.count,
    required this.today,
    required this.enabled,
    required this.saveError,
  });

  final MilestoneDraft milestone;
  final int index;
  final int count;
  final LocalDate today;
  final bool enabled;
  final ApiException? saveError;

  @override
  ConsumerState<MilestoneEditorCard> createState() => _MilestoneEditorCardState();
}

class _MilestoneEditorCardState extends ConsumerState<MilestoneEditorCard> {
  late final _titleController = TextEditingController(text: widget.milestone.title);
  late final _memoController = TextEditingController(text: widget.milestone.description);

  @override
  void dispose() {
    _titleController.dispose();
    _memoController.dispose();
    super.dispose();
  }

  void _update(MilestoneDraft Function(MilestoneDraft milestone) change) => ref
      .read(replanControllerProvider.notifier)
      .updateMilestone(widget.milestone.localKey, change);

  void _delete() {
    final l10n = AppLocalizations.of(context);
    final controller = ref.read(replanControllerProvider.notifier);
    showToast(
      context,
      l10n.replanDeleted,
      actionLabel: l10n.commonUndo,
      onAction: controller.undoRemove,
    );
    controller.removeMilestone(widget.milestone.localKey);
  }

  static String? _titleError(Set<MilestoneFieldViolation> violations, AppLocalizations l10n) {
    if (violations.contains(MilestoneFieldViolation.titleRequired)) {
      return l10n.validationRequired;
    }
    if (violations.contains(MilestoneFieldViolation.titleTooLong)) {
      return l10n.validationMaxLength(InputRules.milestoneTitleMaxLength);
    }
    return null;
  }

  /// Server field errors whose path points at this card (`milestones[i].…`).
  List<String> _serverMessages(AppLocalizations l10n) => [
    for (final error in widget.saveError?.fieldErrors ?? const <ApiFieldError>[])
      if (ReplanRules.milestoneIndexOf(error.field) == widget.index)
        error.message ?? l10n.errorValidationFailed,
  ];

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final milestone = widget.milestone;
    final violations = ReplanRules.violations(milestone, widget.today);
    final prefix = 'replan.milestone.${widget.index}';
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          spacing: AppSpacing.md,
          children: [
            _EditorTextField(
              fieldKey: Key('$prefix.title'),
              controller: _titleController,
              label: l10n.replanFieldTitle,
              enabled: widget.enabled,
              errorText: _titleError(violations, l10n),
              onChanged: (title) => _update((milestone) => milestone.copyWith(title: title)),
            ),
            _StructureFields(
              prefix: prefix,
              milestone: milestone,
              today: widget.today,
              enabled: widget.enabled,
              violations: violations,
              onChanged: _update,
            ),
            _EditorTextField(
              fieldKey: Key('$prefix.memo'),
              controller: _memoController,
              label: l10n.replanFieldMemo,
              enabled: widget.enabled,
              maxLines: 4,
              errorText: violations.contains(MilestoneFieldViolation.descriptionTooLong)
                  ? l10n.validationMaxLength(InputRules.milestoneDescriptionMaxLength)
                  : null,
              onChanged: (memo) => _update((milestone) => milestone.copyWith(description: memo)),
            ),
            for (final message in _serverMessages(l10n)) InlineError(message: message),
            MilestoneCardActions(
              prefix: prefix,
              title: milestone.title,
              canMoveUp: widget.enabled && widget.index > 0,
              canMoveDown: widget.enabled && widget.index < widget.count - 1,
              enabled: widget.enabled,
              onMove: (direction) =>
                  ref.read(replanControllerProvider.notifier).move(milestone.localKey, direction),
              onDelete: _delete,
            ),
          ],
        ),
      ),
    );
  }
}

/// Period, priority and skills: the parts only a replan can change (docs/05 §7.6).
class _StructureFields extends StatelessWidget {
  const _StructureFields({
    required this.prefix,
    required this.milestone,
    required this.today,
    required this.enabled,
    required this.violations,
    required this.onChanged,
  });

  final String prefix;
  final MilestoneDraft milestone;
  final LocalDate today;
  final bool enabled;
  final Set<MilestoneFieldViolation> violations;
  final void Function(MilestoneDraft Function(MilestoneDraft milestone) change) onChanged;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: AppSpacing.md,
      children: [
        MilestonePeriodFields(
          prefix: prefix,
          milestone: milestone,
          today: today,
          enabled: enabled,
          violations: violations,
          onStart: (date) => onChanged((milestone) => milestone.copyWith(startDate: date)),
          onEnd: (date) => onChanged((milestone) => milestone.copyWith(endDate: date)),
        ),
        MilestonePriorityChips(
          prefix: prefix,
          value: milestone.priority,
          enabled: enabled,
          onChanged: (priority) => onChanged((milestone) => milestone.copyWith(priority: priority)),
        ),
        MilestoneSkillsLine(
          prefix: prefix,
          codes: milestone.skillCodes,
          enabled: enabled,
          tooMany: violations.contains(MilestoneFieldViolation.tooManySkills),
          onChanged: (codes) => onChanged((milestone) => milestone.copyWith(skillCodes: codes)),
        ),
      ],
    );
  }
}

class _EditorTextField extends StatelessWidget {
  const _EditorTextField({
    required this.fieldKey,
    required this.controller,
    required this.label,
    required this.enabled,
    required this.errorText,
    required this.onChanged,
    this.maxLines = 1,
  });

  final Key fieldKey;
  final TextEditingController controller;
  final String label;
  final bool enabled;
  final String? errorText;
  final ValueChanged<String> onChanged;
  final int maxLines;

  @override
  Widget build(BuildContext context) {
    return TextField(
      key: fieldKey,
      controller: controller,
      enabled: enabled,
      minLines: 1,
      maxLines: maxLines,
      decoration: InputDecoration(labelText: label, errorText: errorText),
      onChanged: onChanged,
    );
  }
}
