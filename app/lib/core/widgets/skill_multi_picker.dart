import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/form_modal.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// One choice of [showSkillMultiPicker]. Features map their skill models to this.
final class PickableSkill {
  const PickableSkill({required this.code, required this.name, required this.groupLabel});

  final String code;
  final String name;

  /// Category label the list is grouped by.
  final String groupLabel;
}

/// Searchable, grouped skill list with at most [maxCount] selections (docs/02 `SkillMultiPicker`).
///
/// Returns the chosen codes in list order, or null when cancelled.
Future<List<String>?> showSkillMultiPicker(
  BuildContext context, {
  required String title,
  required List<PickableSkill> skills,
  required List<String> initialCodes,
  required int maxCount,
}) => showFormModal<List<String>>(
  context,
  builder: (_) => _SkillMultiPicker(
    title: title,
    skills: skills,
    initialCodes: initialCodes,
    maxCount: maxCount,
  ),
);

class _SkillMultiPicker extends StatefulWidget {
  const _SkillMultiPicker({
    required this.title,
    required this.skills,
    required this.initialCodes,
    required this.maxCount,
  });

  final String title;
  final List<PickableSkill> skills;
  final List<String> initialCodes;
  final int maxCount;

  @override
  State<_SkillMultiPicker> createState() => _SkillMultiPickerState();
}

class _SkillMultiPickerState extends State<_SkillMultiPicker> {
  late final Set<String> _selected = {...widget.initialCodes};
  String _query = '';
  bool _limitReached = false;

  List<PickableSkill> get _visibleSkills {
    final query = _query.trim().toLowerCase();
    if (query.isEmpty) {
      return widget.skills;
    }
    return [
      for (final skill in widget.skills)
        if (skill.name.toLowerCase().contains(query) || skill.code.toLowerCase().contains(query))
          skill,
    ];
  }

  void _toggle(String code, bool selected) {
    setState(() {
      if (!selected) {
        _selected.remove(code);
        _limitReached = false;
      } else if (_selected.length >= widget.maxCount) {
        _limitReached = true;
      } else {
        _selected.add(code);
      }
    });
  }

  /// Pops the chosen codes in catalog order.
  void _done() => Navigator.of(context).pop([
    for (final skill in widget.skills)
      if (_selected.contains(skill.code)) skill.code,
  ]);

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final visible = _visibleSkills;
    return SizedBox(
      height: MediaQuery.sizeOf(context).height * 0.75,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(widget.title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: AppSpacing.sm),
            TextField(
              key: const Key('skillPicker.searchField'),
              decoration: InputDecoration(
                labelText: l10n.commonSearch,
                prefixIcon: const Icon(Icons.search),
              ),
              onChanged: (value) => setState(() => _query = value),
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              _limitReached
                  ? l10n.validationMaxItems(widget.maxCount)
                  : l10n.commonSelectedCount(_selected.length, widget.maxCount),
              style: TextStyle(color: _limitReached ? Theme.of(context).colorScheme.error : null),
            ),
            Expanded(
              child: ListView.builder(
                itemCount: visible.length,
                itemBuilder: (context, index) => _SkillOption(
                  skill: visible[index],
                  showGroup:
                      index == 0 || visible[index - 1].groupLabel != visible[index].groupLabel,
                  selected: _selected.contains(visible[index].code),
                  onChanged: (selected) => _toggle(visible[index].code, selected),
                ),
              ),
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: Text(l10n.commonCancel),
                ),
                const SizedBox(width: AppSpacing.sm),
                FilledButton(
                  key: const Key('skillPicker.doneButton'),
                  onPressed: _done,
                  child: Text(l10n.commonDone),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

/// One checkbox row, preceded by its category label when the category changes.
class _SkillOption extends StatelessWidget {
  const _SkillOption({
    required this.skill,
    required this.showGroup,
    required this.selected,
    required this.onChanged,
  });

  final PickableSkill skill;
  final bool showGroup;
  final bool selected;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (showGroup)
          Padding(
            padding: const EdgeInsets.only(top: AppSpacing.md),
            child: Text(skill.groupLabel, style: Theme.of(context).textTheme.labelLarge),
          ),
        CheckboxListTile(
          key: Key('skillPicker.option.${skill.code}'),
          value: selected,
          title: Text(skill.name),
          onChanged: (value) => onChanged(value ?? false),
        ),
      ],
    );
  }
}
