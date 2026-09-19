import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';

// Screen labels of API enums (docs/02 §3.1 "enum 라벨"). `unknown` shows `enum.unknown`.

extension PriorityLabel on Priority {
  String label(AppLocalizations l10n) => switch (this) {
    Priority.must => l10n.enumPriorityMust,
    Priority.should => l10n.enumPriorityShould,
    Priority.later => l10n.enumPriorityLater,
    Priority.unknown => l10n.enumUnknown,
  };
}

extension MilestoneStatusLabel on MilestoneStatus {
  String label(AppLocalizations l10n) => switch (this) {
    MilestoneStatus.planned => l10n.enumMilestoneStatusPlanned,
    MilestoneStatus.inProgress => l10n.enumMilestoneStatusInProgress,
    MilestoneStatus.done => l10n.enumMilestoneStatusDone,
    MilestoneStatus.deferred => l10n.enumMilestoneStatusDeferred,
    MilestoneStatus.dropped => l10n.enumMilestoneStatusDropped,
    MilestoneStatus.unknown => l10n.enumUnknown,
  };
}

extension RiskLevelLabel on RiskLevel {
  String label(AppLocalizations l10n) => switch (this) {
    RiskLevel.low => l10n.enumRiskLevelLow,
    RiskLevel.medium => l10n.enumRiskLevelMedium,
    RiskLevel.high => l10n.enumRiskLevelHigh,
    RiskLevel.critical => l10n.enumRiskLevelCritical,
    RiskLevel.unknown => l10n.enumUnknown,
  };
}

extension PlanStatusLabel on PlanStatus {
  String label(AppLocalizations l10n) => switch (this) {
    PlanStatus.active => l10n.planHistoryCurrent,
    PlanStatus.superseded => l10n.planHistoryPrevious,
    PlanStatus.archived => l10n.planHistoryArchived,
    PlanStatus.unknown => l10n.enumUnknown,
  };
}

extension TargetAdjustmentLabel on TargetAdjustment {
  String label(AppLocalizations l10n) => switch (this) {
    TargetAdjustment.roleDefault => l10n.enumTargetAdjustmentRoleDefault,
    TargetAdjustment.deferred => l10n.enumTargetAdjustmentDeferred,
    TargetAdjustment.targetReduced => l10n.enumTargetAdjustmentTargetReduced,
    TargetAdjustment.userEdited => l10n.enumTargetAdjustmentUserEdited,
    TargetAdjustment.unknown => l10n.enumUnknown,
  };
}

extension TargetRoleLabel on TargetRole {
  String label(AppLocalizations l10n) => switch (this) {
    TargetRole.javaBackend => l10n.enumTargetRoleJavaBackend,
    TargetRole.unknown => l10n.enumUnknown,
  };
}

extension SideProjectStatusLabel on SideProjectStatus {
  String label(AppLocalizations l10n) => switch (this) {
    SideProjectStatus.active => l10n.enumSideProjectStatusActive,
    SideProjectStatus.paused => l10n.enumSideProjectStatusPaused,
    SideProjectStatus.done => l10n.enumSideProjectStatusDone,
    SideProjectStatus.unknown => l10n.enumUnknown,
  };
}

extension SkillAxisLabel on SkillAxis {
  String label(AppLocalizations l10n) => switch (this) {
    SkillAxis.knowledge => l10n.enumSkillAxisKnowledge,
    SkillAxis.implementation => l10n.enumSkillAxisImplementation,
    SkillAxis.explanation => l10n.enumSkillAxisExplanation,
    SkillAxis.debugging => l10n.enumSkillAxisDebugging,
    SkillAxis.unknown => l10n.enumUnknown,
  };
}

extension SkillCategoryLabel on SkillCategory {
  String label(AppLocalizations l10n) => switch (this) {
    SkillCategory.java => l10n.enumSkillCategoryJava,
    SkillCategory.spring => l10n.enumSkillCategorySpring,
    SkillCategory.database => l10n.enumSkillCategoryDatabase,
    SkillCategory.webHttp => l10n.enumSkillCategoryWebHttp,
    SkillCategory.network => l10n.enumSkillCategoryNetwork,
    SkillCategory.cs => l10n.enumSkillCategoryCs,
    SkillCategory.algorithm => l10n.enumSkillCategoryAlgorithm,
    SkillCategory.testing => l10n.enumSkillCategoryTesting,
    SkillCategory.devops => l10n.enumSkillCategoryDevops,
    SkillCategory.security => l10n.enumSkillCategorySecurity,
    SkillCategory.practicalEngineering => l10n.enumSkillCategoryPracticalEngineering,
    SkillCategory.systemDesign => l10n.enumSkillCategorySystemDesign,
    SkillCategory.explanation => l10n.enumSkillCategoryExplanation,
    SkillCategory.unknown => l10n.enumUnknown,
  };
}

extension EnergyLevelLabel on EnergyLevel {
  String label(AppLocalizations l10n) => switch (this) {
    EnergyLevel.low => l10n.enumEnergyLevelLow,
    EnergyLevel.normal => l10n.enumEnergyLevelNormal,
    EnergyLevel.high => l10n.enumEnergyLevelHigh,
    EnergyLevel.unknown => l10n.enumUnknown,
  };
}

extension TaskTypeLabel on TaskType {
  String label(AppLocalizations l10n) => switch (this) {
    TaskType.recall => l10n.enumTaskTypeRecall,
    TaskType.review => l10n.enumTaskTypeReview,
    TaskType.challenge => l10n.enumTaskTypeChallenge,
    TaskType.projectTask => l10n.enumTaskTypeProjectTask,
    TaskType.coachReview => l10n.enumTaskTypeCoachReview,
    TaskType.reading => l10n.enumTaskTypeReading,
    TaskType.readCode => l10n.enumTaskTypeReadCode,
    TaskType.explain => l10n.enumTaskTypeExplain,
    TaskType.unknown => l10n.enumUnknown,
  };
}

extension TaskStatusLabel on TaskStatus {
  String label(AppLocalizations l10n) => switch (this) {
    TaskStatus.planned => l10n.enumTaskStatusPlanned,
    TaskStatus.inProgress => l10n.enumTaskStatusInProgress,
    TaskStatus.completed => l10n.enumTaskStatusCompleted,
    TaskStatus.skipped => l10n.enumTaskStatusSkipped,
    TaskStatus.deferred => l10n.enumTaskStatusDeferred,
    TaskStatus.unknown => l10n.enumUnknown,
  };
}

extension ReadingFeedbackLabel on ReadingFeedback {
  String label(AppLocalizations l10n) => switch (this) {
    ReadingFeedback.helpful => l10n.enumReadingFeedbackHelpful,
    ReadingFeedback.tooHard => l10n.enumReadingFeedbackTooHard,
    ReadingFeedback.boring => l10n.enumReadingFeedbackBoring,
    ReadingFeedback.unknown => l10n.enumUnknown,
  };
}

extension LearningEventTypeLabel on LearningEventType {
  String label(AppLocalizations l10n) => switch (this) {
    LearningEventType.sessionStarted => l10n.enumLearningEventSessionStarted,
    LearningEventType.sessionCompleted => l10n.enumLearningEventSessionCompleted,
    LearningEventType.selfExplanationSubmitted => l10n.enumLearningEventExplanationSubmitted,
    LearningEventType.selfExplanationSkipped => l10n.enumLearningEventExplanationSkipped,
    LearningEventType.hintDisclosed => l10n.enumLearningEventHintDisclosed,
    LearningEventType.challengeStarted => l10n.enumLearningEventChallengeStarted,
    LearningEventType.challengeSubmitted => l10n.enumLearningEventChallengeSubmitted,
    LearningEventType.challengeEvaluated => l10n.enumLearningEventChallengeEvaluated,
    LearningEventType.reviewAnswered => l10n.enumLearningEventReviewAnswered,
    LearningEventType.leechDetected => l10n.enumLearningEventLeechDetected,
    LearningEventType.rubberDuckCompleted => l10n.enumLearningEventRubberDuckCompleted,
    LearningEventType.coachReviewCompleted => l10n.enumLearningEventCoachReviewCompleted,
    LearningEventType.coachFindingClosed => l10n.enumLearningEventCoachFindingClosed,
    LearningEventType.diagnosticPassed => l10n.enumLearningEventDiagnosticPassed,
    LearningEventType.diagnosticFailed => l10n.enumLearningEventDiagnosticFailed,
    LearningEventType.evidenceAccepted => l10n.enumLearningEventEvidenceAccepted,
    LearningEventType.planReplanned => l10n.enumLearningEventPlanReplanned,
    LearningEventType.unknown => l10n.enumUnknown,
  };
}

/// `SkillLevel` label for an ordinal 0~5 (docs/04 §3, docs/02 §3.1).
String skillLevelLabel(int level, AppLocalizations l10n) => switch (level) {
  0 => l10n.enumSkillLevel0,
  1 => l10n.enumSkillLevel1,
  2 => l10n.enumSkillLevel2,
  3 => l10n.enumSkillLevel3,
  4 => l10n.enumSkillLevel4,
  5 => l10n.enumSkillLevel5,
  _ => l10n.enumUnknown,
};
