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
  @JsonValue('JAVA_BACKEND_STARTER')
  javaBackendStarter,
  @JsonValue('INTEGRATION_ENGINEER')
  integrationEngineer,
  unknown;

  /// The 3 real tracks, without [unknown]. Declaration order is the display order (docs/02 §3.4).
  static List<TargetRole> get known => [
    for (final role in values)
      if (role != unknown) role,
  ];

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

/// `BuildableView.steps[].status` (docs/05 §7.10, docs/06 §11.4).
enum BuildableStatus {
  /// Every gate skill reached its target on evidence alone.
  @JsonValue('BUILDABLE')
  buildable,

  /// The first step that has not: the one to build now. At most one per plan.
  @JsonValue('NEXT')
  next,

  @JsonValue('NOT_YET')
  notYet,
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
@JsonEnum(alwaysCreate: true)
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
  @JsonValue('INTEGRATION')
  integration,
  @JsonValue('PRACTICAL_ENGINEERING')
  practicalEngineering,
  @JsonValue('SYSTEM_DESIGN')
  systemDesign,
  @JsonValue('EXPLANATION')
  explanation,
  unknown;

  /// Wire value for request bodies (`PUT /skills/me/self-assessment`, docs/05 §6.5).
  String get wireName => _$SkillCategoryEnumMap[this]!;

  /// The 14 real categories, without [unknown].
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

/// 한 기술을 한 바퀴 도는 여섯 단계 (docs/04 §3, docs/06 §5.11).
///
/// **만들기가 먼저다** — 무엇을 만들다 막혀 봐야 읽을 이유가 생긴다. 이 선언 순서가 화면의 6칸 순서이고, 순서는 표시 순서이지 선행 조건이 아니다(ST-2).
enum LearningStage {
  @JsonValue('BUILD')
  build,
  @JsonValue('READ_CONCEPT')
  readConcept,
  @JsonValue('READ_CODE')
  readCode,
  @JsonValue('EXPLAIN')
  explain,
  @JsonValue('REVIEW')
  review,
  @JsonValue('REDO')
  redo,
  unknown,
}

/// 사이드 프로젝트 분류 (docs/04 §3, I-23). 상태가 아니라 분류라 전이표가 없다.
///
/// `pastWork`는 Today의 프로젝트 과제 대상에서 빠진다 — 이미 끝난 일에 오늘 할 과제를 붙일 수는 없다(docs/06 SP-3).
enum SideProjectKind {
  @JsonValue('SIDE')
  side,
  @JsonValue('PAST_WORK')
  pastWork,
  unknown,
}
