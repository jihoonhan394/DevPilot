import 'package:devpilot_app/app/explain_with_duck_button.dart';
import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// Where SCR-SKILL-DETAIL leads next (docs/02 §3.10): the cards of this skill, its practice
/// challenges, and explaining the concept to the rubber duck (`CONCEPT`, AI only).
class SkillDetailActions extends StatelessWidget {
  const SkillDetailActions({
    super.key,
    required this.skillId,
    required this.skillCode,
    required this.skillName,
  });

  final String skillId;
  final String skillCode;
  final String skillName;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Wrap(
      spacing: AppSpacing.sm,
      runSpacing: AppSpacing.xs,
      children: [
        OutlinedButton(
          key: const Key('skillDetail.reviewCardsButton'),
          onPressed: () => context.go(AppRoutes.reviewItemsFor(skillId: skillId)),
          child: Text(l10n.skillDetailReviewCards),
        ),
        OutlinedButton(
          key: const Key('skillDetail.practiceButton'),
          onPressed: () => context.go(AppRoutes.trainingFor(skillId)),
          child: Text(l10n.skillDetailPractice),
        ),
        ExplainWithDuckButton(
          buttonKey: const Key('skillDetail.explainButton'),
          label: l10n.skillDetailExplain,
          style: ExplainButtonStyle.outlined,
          launch: RubberDuckLaunch(
            targetType: RubberDuckTargetType.concept,
            conceptKey: skillCode,
            skillCode: skillCode,
            preview: RubberDuckTargetPreview(title: skillName),
          ),
        ),
      ],
    );
  }
}
