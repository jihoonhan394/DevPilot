import 'package:freezed_annotation/freezed_annotation.dart';

part 'api_enums.g.dart';

// API enums shared by several features. Names and values follow the registry in docs/04 §3.
// Every enum ends with `unknown`: a value added on the server later must not crash the app
// (docs/05 §1.1, docs/08 §8.3). Model fields use `@JsonKey(unknownEnumValue: X.unknown)`.
// Requests never send `unknown`.

@JsonEnum(alwaysCreate: true)
enum TargetRole {
  @JsonValue('JAVA_BACKEND')
  javaBackend,
  unknown;

  /// Wire value for query parameters (`GET /skills/tree?role=`).
  String get wireName => _$TargetRoleEnumMap[this]!;
}

enum UserRole {
  @JsonValue('USER')
  user,
  @JsonValue('ADMIN')
  admin,
  unknown,
}

enum UserStatus {
  @JsonValue('ACTIVE')
  active,
  @JsonValue('DELETION_REQUESTED')
  deletionRequested,
  unknown,
}

enum AiStatus {
  @JsonValue('ENABLED')
  enabled,
  @JsonValue('BUDGET_WARNING')
  budgetWarning,
  @JsonValue('BALANCE_EXHAUSTED')
  balanceExhausted,
  @JsonValue('DISABLED')
  disabled,
  unknown;

  /// `aiAvailable` of docs/02 §6.2: AI buttons work unless the AI is off or out of balance.
  /// `BALANCE_EXHAUSTED` behaves like `DISABLED` with its own reason text (docs/04 §3).
  bool get allowsAi => this != disabled && this != balanceExhausted;
}

enum RiskLevel {
  @JsonValue('LOW')
  low,
  @JsonValue('MEDIUM')
  medium,
  @JsonValue('HIGH')
  high,
  @JsonValue('CRITICAL')
  critical,
  unknown,
}

enum Priority {
  @JsonValue('MUST')
  must,
  @JsonValue('SHOULD')
  should,
  @JsonValue('LATER')
  later,
  unknown,
}

enum PlanStatus {
  @JsonValue('ACTIVE')
  active,
  @JsonValue('SUPERSEDED')
  superseded,
  @JsonValue('ARCHIVED')
  archived,
  unknown,
}

enum MilestoneStatus {
  @JsonValue('PLANNED')
  planned,
  @JsonValue('IN_PROGRESS')
  inProgress,
  @JsonValue('DONE')
  done,
  @JsonValue('DEFERRED')
  deferred,
  @JsonValue('DROPPED')
  dropped,
  unknown,
}

enum TargetAdjustment {
  @JsonValue('ROLE_DEFAULT')
  roleDefault,
  @JsonValue('DEFERRED')
  deferred,
  @JsonValue('TARGET_REDUCED')
  targetReduced,
  @JsonValue('USER_EDITED')
  userEdited,
  unknown,
}

/// Declaration order is the display order (docs/02 §3.1, docs/05 §6.1).
enum SkillCategory {
  @JsonValue('JAVA')
  java,
  @JsonValue('SPRING')
  spring,
  @JsonValue('DATABASE')
  database,
  @JsonValue('WEB_HTTP')
  webHttp,
  @JsonValue('NETWORK')
  network,
  @JsonValue('CS')
  cs,
  @JsonValue('ALGORITHM')
  algorithm,
  @JsonValue('TESTING')
  testing,
  @JsonValue('DEVOPS')
  devops,
  @JsonValue('SECURITY')
  security,
  @JsonValue('PRACTICAL_ENGINEERING')
  practicalEngineering,
  @JsonValue('SYSTEM_DESIGN')
  systemDesign,
  @JsonValue('EXPLANATION')
  explanation,
  unknown;

  /// The 13 real categories, without [unknown].
  static List<SkillCategory> get known => [
    for (final category in values)
      if (category != unknown) category,
  ];
}

enum SkillAxis {
  @JsonValue('KNOWLEDGE')
  knowledge,
  @JsonValue('IMPLEMENTATION')
  implementation,
  @JsonValue('EXPLANATION')
  explanation,
  @JsonValue('DEBUGGING')
  debugging,
  unknown;

  /// The 4 real axes in `AxisLevels` order, without [unknown].
  static const known = [knowledge, implementation, explanation, debugging];
}

/// State of an asynchronous AI resource (docs/05 §1.8).
enum AsyncJobStatus {
  @JsonValue('PENDING')
  pending,
  @JsonValue('RUNNING')
  running,
  @JsonValue('COMPLETED')
  completed,
  @JsonValue('FAILED')
  failed,
  unknown;

  /// Still being worked on: the screen keeps polling.
  bool get isActive => this == pending || this == running;
}

/// Who made a challenge or a review card (docs/04 §3).
enum ContentOrigin {
  @JsonValue('SEED')
  seed,
  @JsonValue('MANUAL')
  manual,
  @JsonValue('AI_GENERATED')
  aiGenerated,
  unknown,
}

/// Why an asynchronous AI step did not finish (`failure_code`, `evaluationSkippedReason`).
enum AsyncFailureCode {
  @JsonValue('AI_UNAVAILABLE')
  aiUnavailable,
  @JsonValue('AI_TIMEOUT')
  aiTimeout,
  @JsonValue('AI_REFUSED')
  aiRefused,
  @JsonValue('AI_OUTPUT_INVALID')
  aiOutputInvalid,
  @JsonValue('AI_BUDGET_EXCEEDED')
  aiBudgetExceeded,
  @JsonValue('AI_RATE_LIMITED')
  aiRateLimited,
  @JsonValue('CONFIDENTIAL_SUSPECTED')
  confidentialSuspected,
  @JsonValue('INTERRUPTED')
  interrupted,
  @JsonValue('INTERNAL_ERROR')
  internalError,
  unknown,
}

/// `expansionSuggestions[].kind` of the replan preview (docs/05 §7.7).
enum ExpansionKind {
  @JsonValue('RESTORE_DEFERRED')
  restoreDeferred,
  @JsonValue('RAISE_TARGET')
  raiseTarget,
  unknown,
}

@JsonEnum(alwaysCreate: true)
enum SideProjectStatus {
  @JsonValue('ACTIVE')
  active,
  @JsonValue('PAUSED')
  paused,
  @JsonValue('DONE')
  done,
  unknown;

  /// Wire value for query parameters (`GET /side-projects?status=`).
  String get wireName => _$SideProjectStatusEnumMap[this]!;
}
