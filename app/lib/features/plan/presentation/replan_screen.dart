import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/plan/domain/replan_draft.dart';
import 'package:devpilot_app/features/plan/presentation/milestone_editor_card.dart';
import 'package:devpilot_app/features/plan/presentation/replan_controller.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-REPLAN, S1 flow "edit → save a new version" (docs/02 §3.9, §4.8). Preview and the
/// shrink/expand suggestions arrive in S2.
class ReplanScreen extends ConsumerWidget {
  const ReplanScreen({super.key, this.fromGoal = false});

  /// Opened after a goal date change (`?from=goal`).
  final bool fromGoal;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final replan = ref.watch(replanControllerProvider);
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.replanTitle))),
      body: replan.when(
        loading: () => const ScreenBody(child: SkeletonList(count: 3, lines: 4)),
        error: (error, _) => ScreenBody(
          child: ErrorView(error: error, onRetry: () => ref.invalidate(replanControllerProvider)),
        ),
        data: (state) => _ReplanEditor(state: state, fromGoal: fromGoal),
      ),
    );
  }
}

class _ReplanEditor extends ConsumerWidget {
  const _ReplanEditor({required this.state, required this.fromGoal});

  final ReplanState state;
  final bool fromGoal;

  Future<void> _save(BuildContext context, WidgetRef ref) async {
    final l10n = AppLocalizations.of(context);
    final outcome = await ref.read(replanControllerProvider.notifier).save();
    if (!context.mounted) {
      return;
    }
    switch (outcome) {
      case ReplanSaved(:final planVersion):
        showToast(context, l10n.replanSaved(planVersion));
        context.go(AppRoutes.plan);
      case ReplanConflict():
        await _showConflict(context, ref);
      case ReplanSaveFailed(:final error):
        // Field errors are shown under their inputs; anything else follows docs/02 §5.1.
        if (error is ApiException &&
            error.code == ApiErrorCode.validationFailed &&
            error.fieldErrors.isNotEmpty) {
          return;
        }
        await presentActionError(context, ref, error);
    }
  }

  Future<void> _showConflict(BuildContext context, WidgetRef ref) async {
    final l10n = AppLocalizations.of(context);
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => AlertDialog(
        title: Text(l10n.replanConflictTitle),
        content: Text(l10n.replanConflictBody),
        actions: [
          TextButton(
            key: const Key('replan.conflictReloadButton'),
            onPressed: () => Navigator.of(dialogContext).pop(),
            child: Text(l10n.replanConflictReload),
          ),
        ],
      ),
    );
    ref.read(replanControllerProvider.notifier).reloadLatest();
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final controller = ref.read(replanControllerProvider.notifier);
    final today = ref.watch(userTodayProvider);
    final draft = state.draft;
    final saveError = state.saveError;
    final countValid = ReplanRules.isCountValid(draft.milestones.length);
    return ScreenBody(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (fromGoal) const _FromGoalNote(),
          _ReasonField(
            reason: draft.reason,
            enabled: !state.isSaving,
            serverError: saveError?.fieldMessage('reason', fallback: l10n.errorValidationFailed),
            onChanged: controller.setReason,
          ),
          const SizedBox(height: AppSpacing.lg),
          SectionTitle(l10n.replanCount(draft.milestones.length)),
          if (!countValid) InlineError(message: l10n.validationMilestoneCount),
          const SizedBox(height: AppSpacing.sm),
          for (var index = 0; index < draft.milestones.length; index++)
            Padding(
              padding: const EdgeInsets.only(bottom: AppSpacing.md),
              child: MilestoneEditorCard(
                // A reloaded plan (conflict) must rebuild the fields, so the plan version is part of
                // the key.
                key: ValueKey(
                  '${draft.basePlan.id}:${draft.basePlan.version}:${draft.milestones[index].localKey}',
                ),
                milestone: draft.milestones[index],
                index: index,
                count: draft.milestones.length,
                today: today,
                enabled: !state.isSaving,
                saveError: saveError,
              ),
            ),
          OutlinedButton.icon(
            key: const Key('replan.addButton'),
            onPressed: state.isSaving || draft.milestones.length >= InputRules.milestoneMaxCount
                ? null
                : () => controller.addMilestone(today),
            icon: const Icon(Icons.add),
            label: Text(l10n.replanAdd),
          ),
          const SizedBox(height: AppSpacing.xl),
          _SaveButton(
            nextVersion: draft.basePlan.planVersion + 1,
            isSaving: state.isSaving,
            onPressed: ReplanRules.canSave(draft, today) && !state.isSaving
                ? () => _save(context, ref)
                : null,
          ),
        ],
      ),
    );
  }
}

/// Shown when opened after a goal date change (`?from=goal`).
class _FromGoalNote extends StatelessWidget {
  const _FromGoalNote();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.lg),
      child: Card(
        key: const Key('replan.fromGoalNote'),
        color: Theme.of(context).colorScheme.primaryContainer,
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.md),
          child: Text(AppLocalizations.of(context).replanFromGoal),
        ),
      ),
    );
  }
}

/// "새 버전(v{n})으로 저장" with an in-button progress indicator.
class _SaveButton extends StatelessWidget {
  const _SaveButton({required this.nextVersion, required this.isSaving, required this.onPressed});

  final int nextVersion;
  final bool isSaving;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return FilledButton(
      key: const Key('replan.saveButton'),
      onPressed: onPressed,
      child: isSaving
          ? SizedBox.square(
              dimension: 20,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                semanticsLabel: l10n.commonSubmitting,
              ),
            )
          : Text(l10n.replanSave(nextVersion)),
    );
  }
}

/// "변경 이유 *": required when saving (1~1000).
class _ReasonField extends StatefulWidget {
  const _ReasonField({
    required this.reason,
    required this.enabled,
    required this.serverError,
    required this.onChanged,
  });

  final String reason;
  final bool enabled;
  final String? serverError;
  final ValueChanged<String> onChanged;

  @override
  State<_ReasonField> createState() => _ReasonFieldState();
}

class _ReasonFieldState extends State<_ReasonField> {
  late final _controller = TextEditingController(text: widget.reason);

  @override
  void didUpdateWidget(_ReasonField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.reason != _controller.text) {
      _controller.text = widget.reason;
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tooLong = widget.reason.length > InputRules.replanReasonMaxLength;
    return TextField(
      key: const Key('replan.reasonField'),
      controller: _controller,
      enabled: widget.enabled,
      minLines: 1,
      maxLines: 4,
      decoration: InputDecoration(
        labelText: l10n.replanReason,
        counterText: '${widget.reason.length}/${InputRules.replanReasonMaxLength}',
        errorText: tooLong
            ? l10n.validationMaxLength(InputRules.replanReasonMaxLength)
            : widget.serverError,
      ),
      onChanged: widget.onChanged,
    );
  }
}
