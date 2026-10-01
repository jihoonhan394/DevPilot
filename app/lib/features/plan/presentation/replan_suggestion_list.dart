import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/features/plan/data/plan_budget_models.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/features/plan/domain/budget_display.dart';
import 'package:devpilot_app/features/plan/domain/replan_suggestion_selection.dart';
import 'package:devpilot_app/features/plan/presentation/replan_preview_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// The suggestion area chosen by which lists are non-empty (docs/02 SCR-REPLAN "제안 영역 규칙"),
/// then the deferred skills that can be restored.
class ReplanSuggestionList extends StatelessWidget {
  const ReplanSuggestionList({
    super.key,
    required this.response,
    required this.preview,
    required this.plan,
  });

  final ReplanPreviewResponse response;
  final ReplanPreviewState preview;
  final PlanView plan;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final shrink =
        response.deferSuggestions.isNotEmpty || response.mustTargetReductionSuggestions.isNotEmpty;
    // Both directions never come together (server rule); if they did, only shrinking is shown.
    final expand = !shrink && response.expansionSuggestions.isNotEmpty;
    final restoreSuggested = {
      for (final suggestion in response.expansionSuggestions)
        if (suggestion.kind == ExpansionKind.restoreDeferred) suggestion.skill.code,
    };
    final deferred = [
      for (final target in plan.skillTargets)
        if (target.deferred && !(expand && restoreSuggested.contains(target.skill.code))) target,
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (shrink) _ShrinkSuggestions(response: response, preview: preview),
        if (expand) _ExpandSuggestions(response: response, preview: preview),
        if (!shrink && !expand)
          Text(l10n.replanPreviewNoSuggestions, key: const Key('replan.noSuggestions')),
        if (deferred.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.lg),
          SectionTitle(l10n.replanDeferredTitle),
          for (final target in deferred)
            _SuggestionTile(
              identity: SuggestionIdentity.restore(target.skill.code),
              checkKey: 'replan.deferred.${target.skill.code}',
              title: l10n.replanDeferredRestoreItem(target.skill.name),
              selected: preview.selection.restorations.contains(target.skill.code),
              onToggle: (selection) => selection.toggleRestoration(target.skill.code),
              preview: preview,
            ),
        ],
      ],
    );
  }
}

/// ② "빠듯해요 — 필수 위주로 줄이는 안": defer SHOULD items, lower MUST targets.
class _ShrinkSuggestions extends StatelessWidget {
  const _ShrinkSuggestions({required this.response, required this.preview});

  final ReplanPreviewResponse response;
  final ReplanPreviewState preview;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Column(
      key: const Key('replan.shrinkSuggestions'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.replanPreviewTight),
        if (response.deferSuggestions.isNotEmpty) Text(l10n.replanPreviewDefer),
        for (final defer in response.deferSuggestions)
          _SuggestionTile(
            identity: SuggestionIdentity.defer(defer.skill.code),
            checkKey: 'replan.defer.${defer.skill.code}',
            title: defer.skill.name,
            subtitle: l10n.replanPreviewSaved(BudgetDisplay.hours(defer.requiredMinutes)),
            selected: preview.selection.deferrals.contains(defer.skill.code),
            onToggle: (selection) => selection.toggleDeferral(defer.skill.code),
            preview: preview,
          ),
        if (response.mustTargetReductionSuggestions.isNotEmpty) Text(l10n.replanPreviewReduce),
        for (final reduce in response.mustTargetReductionSuggestions)
          _ReductionTile(suggestion: reduce, preview: preview),
        const SizedBox(height: AppSpacing.sm),
        Text(
          l10n.replanPreviewAfterAll(response.riskAfterSuggestions.riskLevel.label(l10n)),
          key: const Key('replan.afterAll'),
        ),
        Text(l10n.replanPreviewOnlySelected),
      ],
    );
  }
}

class _ReductionTile extends StatelessWidget {
  const _ReductionTile({required this.suggestion, required this.preview});

  final TargetReductionSuggestionView suggestion;
  final ReplanPreviewState preview;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final change = TargetChangeInput(
      skillCode: suggestion.skill.code,
      axis: suggestion.axis,
      newTarget: suggestion.newTarget,
    );
    return _SuggestionTile(
      identity: SuggestionIdentity.reduce(change),
      checkKey: 'replan.reduce.${change.skillCode}.${change.axis.name}',
      title: l10n.replanPreviewReduceItem(
        suggestion.skill.name,
        suggestion.axis.label(l10n),
        suggestion.currentTarget,
        suggestion.newTarget,
      ),
      subtitle: l10n.replanPreviewSaved(BudgetDisplay.hours(suggestion.savedMinutes)),
      selected: preview.selection.reductions.containsKey(
        ReplanSuggestionSelection.targetKey(change.skillCode, change.axis),
      ),
      onToggle: (selection) => selection.toggleReduction(change),
      preview: preview,
    );
  }
}

/// ③ "여유가 있어요 — 더 깊이 하는 안": restore deferred items, raise MUST targets.
class _ExpandSuggestions extends StatelessWidget {
  const _ExpandSuggestions({required this.response, required this.preview});

  final ReplanPreviewResponse response;
  final ReplanPreviewState preview;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final restores = [
      for (final item in response.expansionSuggestions)
        if (item.kind == ExpansionKind.restoreDeferred) item,
    ];
    final raises = [
      for (final item in response.expansionSuggestions)
        if (item.kind == ExpansionKind.raiseTarget &&
            item.axis != null &&
            item.currentTarget != null &&
            item.newTarget != null)
          item,
    ];
    return Column(
      key: const Key('replan.expandSuggestions'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.replanPreviewRoomy),
        if (restores.isNotEmpty) Text(l10n.replanPreviewRestore),
        for (final restore in restores)
          _SuggestionTile(
            identity: SuggestionIdentity.restore(restore.skill.code),
            checkKey: 'replan.restore.${restore.skill.code}',
            title: restore.skill.name,
            subtitle: l10n.replanPreviewAdded(BudgetDisplay.hours(restore.addedMinutes)),
            selected: preview.selection.restorations.contains(restore.skill.code),
            onToggle: (selection) => selection.toggleRestoration(restore.skill.code),
            preview: preview,
          ),
        if (raises.isNotEmpty) Text(l10n.replanPreviewRaise),
        for (final raise in raises) _RaiseTile(suggestion: raise, preview: preview),
        const SizedBox(height: AppSpacing.sm),
        Text(
          l10n.replanPreviewExpandAfterAll(response.riskAfterSuggestions.riskLevel.label(l10n)),
          key: const Key('replan.afterAll'),
        ),
        Text(l10n.replanPreviewExpandNote),
      ],
    );
  }
}

/// `RAISE_TARGET`; the list only holds items with `axis`, `currentTarget` and `newTarget`.
class _RaiseTile extends StatelessWidget {
  const _RaiseTile({required this.suggestion, required this.preview});

  final ExpansionSuggestionView suggestion;
  final ReplanPreviewState preview;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final change = TargetChangeInput(
      skillCode: suggestion.skill.code,
      axis: suggestion.axis!,
      newTarget: suggestion.newTarget!,
    );
    return _SuggestionTile(
      identity: SuggestionIdentity.raise(change),
      checkKey: 'replan.raise.${change.skillCode}.${change.axis.name}',
      title: l10n.replanPreviewRaiseItem(
        suggestion.skill.name,
        change.axis.label(l10n),
        suggestion.currentTarget!,
        change.newTarget,
      ),
      subtitle: l10n.replanPreviewAdded(BudgetDisplay.hours(suggestion.addedMinutes)),
      selected: preview.selection.raises.containsKey(
        ReplanSuggestionSelection.targetKey(change.skillCode, change.axis),
      ),
      onToggle: (selection) => selection.toggleRaise(change),
      preview: preview,
    );
  }
}

/// A checkable suggestion with the server's error under it, if any.
class _SuggestionTile extends ConsumerWidget {
  const _SuggestionTile({
    required this.identity,
    required this.checkKey,
    required this.title,
    required this.selected,
    required this.onToggle,
    required this.preview,
    this.subtitle,
  });

  final String identity;
  final String checkKey;
  final String title;
  final String? subtitle;
  final bool selected;
  final ReplanSuggestionSelection Function(ReplanSuggestionSelection selection) onToggle;
  final ReplanPreviewState preview;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final error = preview.rowErrors[identity];
    final subtitleText = subtitle;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        CheckboxListTile(
          key: Key(checkKey),
          contentPadding: EdgeInsets.zero,
          controlAffinity: ListTileControlAffinity.leading,
          value: selected,
          title: Text(title),
          subtitle: subtitleText == null ? null : Text(subtitleText),
          onChanged: preview.busy
              ? null
              : (_) => ref.read(replanPreviewControllerProvider.notifier).toggle(onToggle),
        ),
        if (error != null) InlineError(message: error.isEmpty ? l10n.errorValidationFailed : error),
      ],
    );
  }
}
