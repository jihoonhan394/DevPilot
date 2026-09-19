import 'package:devpilot_app/core/api/error_message_mapper.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/core/widgets/skill_multi_picker.dart';
import 'package:devpilot_app/features/onboarding/domain/onboarding_draft.dart';
import 'package:devpilot_app/features/skill/data/skill_models.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// "지금 프로젝트에 필요한 기술" on step 3: collapsed; opening it loads `GET /skills/tree`.
///
/// Loading shows a skeleton inside the section and a failure an inline error with retry; the
/// user can still go to the next step (docs/02 step "상태").
class OnboardingFocusSkills extends ConsumerStatefulWidget {
  const OnboardingFocusSkills({
    super.key,
    required this.selectedCodes,
    required this.onChanged,
    this.errorText,
  });

  final List<String> selectedCodes;
  final ValueChanged<List<String>> onChanged;
  final String? errorText;

  @override
  ConsumerState<OnboardingFocusSkills> createState() => _OnboardingFocusSkillsState();
}

class _OnboardingFocusSkillsState extends ConsumerState<OnboardingFocusSkills> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final errorText = widget.errorText;
    return Card(
      child: ExpansionTile(
        key: const Key('onboarding.focusToggle'),
        initiallyExpanded: widget.selectedCodes.isNotEmpty,
        title: Text(l10n.onboardingLevelFocus),
        onExpansionChanged: (expanded) => setState(() => _expanded = expanded),
        childrenPadding: const EdgeInsets.fromLTRB(
          AppSpacing.lg,
          0,
          AppSpacing.lg,
          AppSpacing.lg,
        ),
        expandedCrossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (_expanded || widget.selectedCodes.isNotEmpty)
            _FocusSkillsContent(selectedCodes: widget.selectedCodes, onChanged: widget.onChanged),
          if (errorText != null) InlineError(message: errorText),
        ],
      ),
    );
  }
}

class _FocusSkillsContent extends ConsumerWidget {
  const _FocusSkillsContent({required this.selectedCodes, required this.onChanged});

  final List<String> selectedCodes;
  final ValueChanged<List<String>> onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final tree = ref.watch(skillTreeProvider);
    return tree.when(
      loading: () => const LoadingSkeleton(
        child: Column(children: [SkeletonBox(), SkeletonBox(width: 200)]),
      ),
      error: (error, _) => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          InlineError(message: messageFor(error, l10n)),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton(
              key: const Key('onboarding.focusRetryButton'),
              onPressed: () => ref.invalidate(skillTreeProvider),
              child: Text(l10n.commonErrorRetry),
            ),
          ),
        ],
      ),
      data: (tree) =>
          _FocusSkillChips(tree: tree, selectedCodes: selectedCodes, onChanged: onChanged),
    );
  }
}

/// Chosen skills as removable chips plus the picker button.
class _FocusSkillChips extends StatelessWidget {
  const _FocusSkillChips({
    required this.tree,
    required this.selectedCodes,
    required this.onChanged,
  });

  final SkillTreeResponse tree;
  final List<String> selectedCodes;
  final ValueChanged<List<String>> onChanged;

  Future<void> _pick(BuildContext context, AppLocalizations l10n) async {
    final codes = await showSkillMultiPicker(
      context,
      title: l10n.onboardingLevelFocus,
      skills: pickableSkills(tree, l10n),
      initialCodes: selectedCodes,
      maxCount: OnboardingDefaults.maxFocusSkills,
    );
    if (codes != null) {
      onChanged(codes);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final names = {for (final skill in tree.skills) skill.code: skill.name};
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Wrap(
          spacing: AppSpacing.xs,
          runSpacing: AppSpacing.xs,
          children: [
            for (final code in selectedCodes)
              InputChip(
                label: Text(names[code] ?? code),
                onDeleted: () => onChanged([
                  for (final selected in selectedCodes)
                    if (selected != code) selected,
                ]),
              ),
          ],
        ),
        Align(
          alignment: Alignment.centerLeft,
          child: OutlinedButton.icon(
            key: const Key('onboarding.focusPickButton'),
            icon: const Icon(Icons.add),
            label: Text(l10n.commonPickSkills),
            onPressed: () => _pick(context, l10n),
          ),
        ),
      ],
    );
  }
}
