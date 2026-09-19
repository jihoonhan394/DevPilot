import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// Memo line with an inline editor (저장/취소, 0~2000 characters).
class MilestoneMemo extends StatefulWidget {
  const MilestoneMemo({
    super.key,
    required this.milestone,
    required this.readOnly,
    required this.busy,
    required this.onSave,
  });

  final MilestoneView milestone;
  final bool readOnly;
  final bool busy;

  /// Returns whether the memo was saved (the editor closes only then).
  final Future<bool> Function(String text) onSave;

  @override
  State<MilestoneMemo> createState() => _MilestoneMemoState();
}

class _MilestoneMemoState extends State<MilestoneMemo> {
  TextEditingController? _editor;

  void _startEditing() =>
      setState(() => _editor = TextEditingController(text: widget.milestone.description ?? ''));

  void _stopEditing() {
    _editor?.dispose();
    setState(() => _editor = null);
  }

  @override
  void dispose() {
    _editor?.dispose();
    super.dispose();
  }

  Future<void> _save(String text) async {
    if (await widget.onSave(text) && mounted) {
      _stopEditing();
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final editor = _editor;
    final memo = widget.milestone.description;
    if (editor != null && !widget.readOnly) {
      return _MemoEditor(
        milestoneId: widget.milestone.id,
        controller: editor,
        busy: widget.busy,
        onCancel: _stopEditing,
        onSave: _save,
      );
    }
    return Row(
      children: [
        Expanded(
          child: Text(
            memo == null || memo.isEmpty
                ? l10n.planMilestoneMemoEmpty
                : l10n.planMilestoneMemo(memo),
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ),
        if (!widget.readOnly)
          IconButton(
            key: Key('plan.milestone.${widget.milestone.id}.memoEdit'),
            tooltip: l10n.planMilestoneMemoEdit(widget.milestone.title),
            onPressed: widget.busy ? null : _startEditing,
            icon: const Icon(Icons.edit_outlined),
          ),
      ],
    );
  }
}

/// Memo text field with 취소 / 저장 (0~2000 characters).
class _MemoEditor extends StatelessWidget {
  const _MemoEditor({
    required this.milestoneId,
    required this.controller,
    required this.busy,
    required this.onCancel,
    required this.onSave,
  });

  final String milestoneId;
  final TextEditingController controller;
  final bool busy;
  final VoidCallback onCancel;
  final ValueChanged<String> onSave;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return ValueListenableBuilder<TextEditingValue>(
      valueListenable: controller,
      builder: (context, value, _) {
        final tooLong = value.text.length > InputRules.milestoneDescriptionMaxLength;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            TextField(
              key: Key('plan.milestone.$milestoneId.memoField'),
              controller: controller,
              minLines: 2,
              maxLines: 6,
              maxLength: InputRules.milestoneDescriptionMaxLength,
              maxLengthEnforcement: MaxLengthEnforcement.none,
              decoration: InputDecoration(
                labelText: l10n.planMilestoneMemoLabel,
                errorText: tooLong
                    ? l10n.validationMaxLength(InputRules.milestoneDescriptionMaxLength)
                    : null,
              ),
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  key: Key('plan.milestone.$milestoneId.memoCancel'),
                  onPressed: busy ? null : onCancel,
                  child: Text(l10n.commonCancel),
                ),
                const SizedBox(width: AppSpacing.sm),
                OutlinedButton(
                  key: Key('plan.milestone.$milestoneId.memoSave'),
                  onPressed: busy || tooLong ? null : () => onSave(controller.text),
                  child: Text(l10n.commonSave),
                ),
              ],
            ),
          ],
        );
      },
    );
  }
}
