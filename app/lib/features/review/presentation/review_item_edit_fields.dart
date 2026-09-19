import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/core/widgets/labeled_dropdown.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/domain/review_item_form.dart';
import 'package:devpilot_app/features/review/presentation/review_labels.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

// Inputs of SCR-REVIEW-ITEM-EDIT (docs/02 §3.6). Skill, type and rubric are chosen on creation
// only; editing shows them read-only.

/// "기술": the JAVA_BACKEND catalog (cached), or the card's skill when editing.
class ReviewItemSkillField extends ConsumerWidget {
  const ReviewItemSkillField({super.key, required this.form, required this.onChanged});

  final ReviewItemForm form;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final editing = form.editing;
    if (editing != null) {
      return _ReadOnlyValue(label: l10n.reviewEditSkill, value: editing.skill.name);
    }
    final tree = ref.watch(skillTreeProvider);
    final skills = tree.value?.skills;
    if (skills == null) {
      return const LoadingSkeleton(child: SkeletonBox(height: AppSizes.input));
    }
    final codes = [for (final skill in skills) skill.code];
    return LabeledDropdown<String?>(
      key: const Key('reviewEdit.skillDropdown'),
      label: l10n.reviewEditSkill,
      value: form.skillCode,
      items: [null, ...codes],
      itemLabel: (code) => code == null
          ? l10n.reviewEditSkillPick
          : skills.firstWhere((skill) => skill.code == code).name,
      onChanged: (code) {
        if (code != null) {
          onChanged(code);
        }
      },
    );
  }
}

/// "유형": 떠올리기 · 설명 · 버그 찾기.
class ReviewItemTypeField extends StatelessWidget {
  const ReviewItemTypeField({super.key, required this.form, required this.onChanged});

  final ReviewItemForm form;
  final ValueChanged<ReviewType> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    if (form.isEdit) {
      return _ReadOnlyValue(label: l10n.reviewEditType, value: form.reviewType.label(l10n));
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(l10n.reviewEditType, style: Theme.of(context).textTheme.titleSmall),
        Wrap(
          spacing: AppSpacing.sm,
          children: [
            for (final type in ReviewType.manual)
              ChoiceChip(
                key: Key('reviewEdit.type.${type.name}'),
                label: Text(type.label(l10n)),
                selected: form.reviewType == type,
                onSelected: (_) => onChanged(type),
              ),
          ],
        ),
      ],
    );
  }
}

/// "핵심 포인트 (1~6개)": one field per point, remove buttons and "포인트 추가". The first point
/// is the hint of the review screen.
class ReviewItemRubricField extends StatelessWidget {
  const ReviewItemRubricField({
    super.key,
    required this.form,
    required this.controllers,
    required this.onAdd,
    required this.onRemove,
  });

  final ReviewItemForm form;
  final List<TextEditingController> controllers;
  final VoidCallback onAdd;
  final ValueChanged<int> onRemove;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    if (form.isEdit) {
      return _ReadOnlyValue(
        label: l10n.reviewEditRubric,
        value: [for (final point in form.rubric) '• $point'].join('\n'),
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l10n.reviewEditRubric, style: Theme.of(context).textTheme.titleSmall),
        for (var index = 0; index < controllers.length; index++)
          Row(
            children: [
              Expanded(
                child: TextField(
                  key: Key('reviewEdit.rubric.${index + 1}'),
                  controller: controllers[index],
                  maxLength: InputRules.reviewItemRubricMaxLength,
                  decoration: InputDecoration(labelText: l10n.reviewEditRubricPoint(index + 1)),
                ),
              ),
              if (controllers.length > 1)
                IconButton(
                  key: Key('reviewEdit.rubricRemove.${index + 1}'),
                  tooltip: l10n.reviewEditRubricRemove(index + 1),
                  icon: const Icon(Icons.close),
                  onPressed: () => onRemove(index),
                ),
            ],
          ),
        if (form.canAddRubricPoint)
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              key: const Key('reviewEdit.rubricAddButton'),
              onPressed: onAdd,
              icon: const Icon(Icons.add),
              label: Text(l10n.reviewEditRubricAdd),
            ),
          ),
        Text(l10n.reviewEditRubricHelp, style: Theme.of(context).textTheme.bodySmall),
      ],
    );
  }
}

class _ReadOnlyValue extends StatelessWidget {
  const _ReadOnlyValue({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return InputDecorator(
      decoration: InputDecoration(labelText: label, enabled: false),
      child: Text(value),
    );
  }
}
