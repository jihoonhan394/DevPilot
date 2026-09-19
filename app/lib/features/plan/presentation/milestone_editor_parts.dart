import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/core/widgets/date_field.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/skill_multi_picker.dart';
import 'package:devpilot_app/features/plan/domain/milestone_ordering.dart';
import 'package:devpilot_app/features/plan/domain/replan_draft.dart';
import 'package:devpilot_app/features/skill/data/skill_models.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

// Parts of MilestoneEditorCard (SCR-REPLAN): priority, period, skills and the card actions.

/// `PriorityChips`: 필수 / 권장 / 나중.
class MilestonePriorityChips extends StatelessWidget {
  const MilestonePriorityChips({
    super.key,
    required this.prefix,
    required this.value,
    required this.enabled,
    required this.onChanged,
  });

  static const _priorities = [Priority.must, Priority.should, Priority.later];

  final String prefix;
  final Priority value;
  final bool enabled;
  final ValueChanged<Priority> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(l10n.replanFieldPriority, style: Theme.of(context).textTheme.labelLarge),
        Wrap(
          spacing: AppSpacing.sm,
          children: [
            for (final priority in _priorities)
              ChoiceChip(
                key: Key('$prefix.priority.${priority.name}'),
                label: Text(priority.label(l10n)),
                selected: value == priority,
                onSelected: enabled ? (_) => onChanged(priority) : null,
              ),
          ],
        ),
      ],
    );
  }
}

/// Start and end date of one milestone (today − 1 year ~ today + 3 years, start ≤ end).
class MilestonePeriodFields extends StatelessWidget {
  const MilestonePeriodFields({
    super.key,
    required this.prefix,
    required this.milestone,
    required this.today,
    required this.enabled,
    required this.violations,
    required this.onStart,
    required this.onEnd,
  });

  final String prefix;
  final MilestoneDraft milestone;
  final LocalDate today;
  final bool enabled;
  final Set<MilestoneFieldViolation> violations;
  final ValueChanged<LocalDate> onStart;
  final ValueChanged<LocalDate> onEnd;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final first = ReplanRules.earliestDate(today);
    final last = ReplanRules.latestDate(today);
    final error = violations.contains(MilestoneFieldViolation.dateOrder)
        ? l10n.validationDateRange
        : violations.contains(MilestoneFieldViolation.dateOutOfRange)
        ? l10n.validationDateOutOfRange
        : null;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        DateField(
          key: Key('$prefix.start'),
          label: l10n.replanFieldStart,
          value: milestone.startDate,
          firstDate: first,
          lastDate: last,
          enabled: enabled,
          onChanged: onStart,
        ),
        const SizedBox(height: AppSpacing.sm),
        DateField(
          key: Key('$prefix.end'),
          label: l10n.replanFieldEnd,
          value: milestone.endDate,
          firstDate: first,
          lastDate: last,
          enabled: enabled,
          errorText: error,
          onChanged: onEnd,
        ),
      ],
    );
  }
}

/// Skills of one milestone with the picker (at most 30).
class MilestoneSkillsLine extends ConsumerWidget {
  const MilestoneSkillsLine({
    super.key,
    required this.prefix,
    required this.codes,
    required this.enabled,
    required this.tooMany,
    required this.onChanged,
  });

  final String prefix;
  final List<String> codes;
  final bool enabled;
  final bool tooMany;
  final ValueChanged<List<String>> onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final tree = ref.watch(skillTreeProvider).value;
    final names = {
      for (final skill in tree?.skills ?? const <SkillNodeView>[]) skill.code: skill.name,
    };
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l10n.replanFieldSkills, style: Theme.of(context).textTheme.labelLarge),
        Text(
          codes.isEmpty
              ? l10n.replanFieldSkillsNone
              : codes.map((code) => names[code] ?? code).join(' · '),
        ),
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton.icon(
            key: Key('$prefix.skillsButton'),
            icon: const Icon(Icons.add),
            label: Text(l10n.replanFieldSkillsPick),
            onPressed: !enabled || tree == null
                ? null
                : () async {
                    final picked = await showSkillMultiPicker(
                      context,
                      title: l10n.replanFieldSkills,
                      skills: pickableSkills(tree, l10n),
                      initialCodes: codes,
                      maxCount: InputRules.milestoneMaxSkills,
                    );
                    if (picked != null) {
                      onChanged(picked);
                    }
                  },
          ),
        ),
        if (tooMany) InlineError(message: l10n.validationMaxItems(InputRules.milestoneMaxSkills)),
      ],
    );
  }
}

/// ↑/↓ and delete of one milestone card.
class MilestoneCardActions extends StatelessWidget {
  const MilestoneCardActions({
    super.key,
    required this.prefix,
    required this.title,
    required this.canMoveUp,
    required this.canMoveDown,
    required this.enabled,
    required this.onMove,
    required this.onDelete,
  });

  final String prefix;
  final String title;
  final bool canMoveUp;
  final bool canMoveDown;
  final bool enabled;
  final ValueChanged<MoveDirection> onMove;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Row(
      children: [
        IconButton(
          key: Key('$prefix.moveUp'),
          tooltip: l10n.planMilestoneMoveUp(title),
          onPressed: canMoveUp ? () => onMove(MoveDirection.up) : null,
          icon: const Icon(Icons.arrow_upward),
        ),
        IconButton(
          key: Key('$prefix.moveDown'),
          tooltip: l10n.planMilestoneMoveDown(title),
          onPressed: canMoveDown ? () => onMove(MoveDirection.down) : null,
          icon: const Icon(Icons.arrow_downward),
        ),
        const Spacer(),
        TextButton.icon(
          key: Key('$prefix.delete'),
          style: TextButton.styleFrom(foregroundColor: DevPilotColors.of(context).danger),
          onPressed: enabled ? onDelete : null,
          icon: const Icon(Icons.delete_outline),
          label: Text(l10n.replanDelete),
        ),
      ],
    );
  }
}
