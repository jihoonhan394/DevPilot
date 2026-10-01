import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';

// Labels of the rubber duck enums (docs/02 §3.1 "enum 라벨").

extension RubberDuckTargetTypeLabel on RubberDuckTargetType {
  String label(AppLocalizations l10n) => switch (this) {
    RubberDuckTargetType.codeReading => l10n.enumRubberDuckTargetTypeCodeReading,
    RubberDuckTargetType.challenge => l10n.enumRubberDuckTargetTypeChallenge,
    RubberDuckTargetType.reviewItem => l10n.enumRubberDuckTargetTypeReviewItem,
    RubberDuckTargetType.concept => l10n.enumRubberDuckTargetTypeConcept,
    RubberDuckTargetType.projectWork => l10n.enumRubberDuckTargetTypeProjectWork,
    RubberDuckTargetType.unknown => l10n.enumUnknown,
  };
}
