import 'package:freezed_annotation/freezed_annotation.dart';

part 'learning_enums.g.dart';

// Today, learning session and task enums (docs/04 §3), shared by Today, Review and Dashboard.
// Every enum ends with `unknown` for values the server adds later; requests never send it.

enum EnergyLevel {
  @JsonValue('LOW')
  low,
  @JsonValue('NORMAL')
  normal,
  @JsonValue('HIGH')
  high,
  unknown;

  /// The three choices of the energy segment, without [unknown].
  static const known = [low, normal, high];
}

enum TaskType {
  @JsonValue('RECALL')
  recall,
  @JsonValue('REVIEW')
  review,
  @JsonValue('CHALLENGE')
  challenge,
  @JsonValue('PROJECT_TASK')
  projectTask,
  @JsonValue('COACH_REVIEW')
  coachReview,
  @JsonValue('READING')
  reading,
  @JsonValue('READ_CODE')
  readCode,
  @JsonValue('EXPLAIN')
  explain,
  unknown,
}

enum TaskStatus {
  @JsonValue('PLANNED')
  planned,
  @JsonValue('IN_PROGRESS')
  inProgress,
  @JsonValue('COMPLETED')
  completed,
  @JsonValue('SKIPPED')
  skipped,
  @JsonValue('DEFERRED')
  deferred,
  unknown,
}

/// Why the planner chose a task (docs/04 §5.1, docs/06 §5.8). The server sends the text too.
enum ReasonCode {
  @JsonValue('MILESTONE_CORE')
  milestoneCore,
  @JsonValue('MILESTONE_NEXT')
  milestoneNext,
  @JsonValue('HIGH_PRACTICAL_IMPORTANCE')
  highPracticalImportance,
  @JsonValue('LARGE_SKILL_GAP')
  largeSkillGap,
  @JsonValue('REVIEW_OVERDUE')
  reviewOverdue,
  @JsonValue('RECENT_RECALL_FAILURE')
  recentRecallFailure,
  @JsonValue('PROJECT_FOCUS')
  projectFocus,
  @JsonValue('READ_REAL_CODE')
  readRealCode,
  @JsonValue('CONTINUE_YESTERDAY')
  continueYesterday,
  @JsonValue('DEADLINE_RISK_MUST')
  deadlineRiskMust,
  @JsonValue('LOW_ENERGY_LIGHT_TASK')
  lowEnergyLightTask,
  @JsonValue('COMEBACK_EASY_START')
  comebackEasyStart,
  unknown,
}

enum SessionStatus {
  @JsonValue('IN_PROGRESS')
  inProgress,
  @JsonValue('COMPLETED')
  completed,
  @JsonValue('ABANDONED')
  abandoned,
  unknown,
}

/// The reader's optional rating of a finished `READ_CODE` task (docs/04 §3, docs/05 §8.4).
enum ReadingFeedback {
  @JsonValue('HELPFUL')
  helpful,
  @JsonValue('TOO_HARD')
  tooHard,
  @JsonValue('BORING')
  boring,
  unknown;

  /// The three chips of the completion sheet, without [unknown].
  static const known = [helpful, tooHard, boring];
}

/// Language of submitted code (docs/04 §3).
@JsonEnum(alwaysCreate: true)
enum CodeLanguage {
  @JsonValue('JAVA')
  java,
  @JsonValue('KOTLIN')
  kotlin,
  @JsonValue('SQL')
  sql,
  @JsonValue('DART')
  dart,
  @JsonValue('YAML')
  yaml,
  @JsonValue('PROPERTIES')
  properties,
  @JsonValue('XML')
  xml,
  @JsonValue('SHELL')
  shell,
  @JsonValue('OTHER')
  other,
  unknown;

  /// Choices of the language dropdown, without [unknown].
  static const known = [java, kotlin, sql, dart, yaml, properties, xml, shell, other];

  /// Wire value, also shown as the dropdown label (identifiers stay in English, docs/02 §9).
  String get wireName => _$CodeLanguageEnumMap[this]!;
}
