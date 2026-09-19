import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/dashboard/data/dashboard_models.dart';
import 'package:devpilot_app/features/plan/data/plan_budget_models.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_models.dart';
import 'package:devpilot_app/features/today/data/learning_session_models.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';

import 'fixtures.dart';

// Today, review, dashboard and budget data shaped like the docs/05 §7~§13 examples.
// The test clock is [testNow]: 2026-09-19 12:00 in Seoul, plan-day [testToday].

final testNow = DateTime.utc(2026, 9, 19, 3);

const mainTaskId = 'e1000000-0000-4000-8000-000000000001';
const reviewTaskId = 'e1000000-0000-4000-8000-000000000000';

const _reasons = [
  ReasonView(code: ReasonCode.milestoneCore, text: '기반 다지기 milestone 핵심 항목'),
  ReasonView(code: ReasonCode.highPracticalImportance, text: '실무에서 중요도가 높은 기술'),
  ReasonView(code: ReasonCode.largeSkillGap, text: '목표 수준과 차이가 큼 (구현 1/4)'),
];

MainTaskView testMainTask({
  String id = mainTaskId,
  TaskType taskType = TaskType.explain,
  String title = 'Spring Transaction 내 말로 설명하기',
  TaskStatus status = TaskStatus.planned,
  int estimatedMinutes = 25,
  List<ReasonView> reasons = _reasons,
  int version = 0,
}) => MainTaskView(
  id: id,
  taskType: taskType,
  skillCode: 'SPRING.TRANSACTION',
  skillName: 'Spring Transaction',
  milestoneId: milestoneFoundationId,
  title: title,
  description: '5문장 이내로 설명하고 예시를 하나 드세요.',
  estimatedMinutes: estimatedMinutes,
  status: status,
  reasons: reasons,
  completedAt: status == TaskStatus.completed ? testNow : null,
  version: version,
);

ReviewTaskView testReviewTask({int due = 3, int minutes = 5}) => ReviewTaskView(
  id: reviewTaskId,
  estimatedMinutes: minutes,
  dueReviewCount: due,
  status: TaskStatus.planned,
  version: 0,
);

TodayView testTodayView({
  int availableMinutes = 30,
  EnergyLevel energyLevel = EnergyLevel.normal,
  RiskLevel? deadlineRisk = RiskLevel.medium,
  bool comebackMode = false,
  MainTaskView? mainTask,
  bool noMainTask = false,
  ReviewTaskView? reviewTask,
  bool noReviewTask = false,
  List<MainTaskView> earlierMainTasks = const [],
}) => TodayView(
  dailyPlanId: 'd7f10000-0000-4000-8000-000000000001',
  planDate: testToday,
  availableMinutes: availableMinutes,
  energyLevel: energyLevel,
  deadlineRisk: deadlineRisk,
  comebackMode: comebackMode,
  generationCount: 1,
  generatedAt: testNow,
  mainTask: noMainTask ? null : (mainTask ?? testMainTask()),
  reviewTask: noReviewTask ? null : (reviewTask ?? testReviewTask()),
  earlierMainTasks: earlierMainTasks,
);

SessionView testSession({
  required String id,
  String? taskId = mainTaskId,
  SessionStatus status = SessionStatus.inProgress,
  Duration elapsed = const Duration(minutes: 30),
  int? actualMinutes,
}) => SessionView(
  id: id,
  learningTaskId: taskId,
  planDate: testToday,
  startedAt: testNow.subtract(elapsed),
  completedAt: status == SessionStatus.completed ? testNow : null,
  actualMinutes: actualMinutes,
  selfReflection: null,
  status: status,
  version: 0,
);

DueReviewItemView testDueItem({
  required String id,
  String skillName = 'Spring Transaction',
  String prompt = '같은 클래스 안에서 @Transactional 메서드를 this로 호출하면 트랜잭션이 적용되지 않을 수 있다. 이유는?',
  List<ReviewRubricItemView> rubric = const [
    ReviewRubricItemView(id: 'R1', criterion: '프록시 기반 AOP 언급'),
    ReviewRubricItemView(id: 'R2', criterion: '내부 호출은 프록시를 우회한다는 점'),
  ],
}) => DueReviewItemView(
  reviewItemId: id,
  skillCode: 'SPRING.TRANSACTION',
  skillName: skillName,
  reviewType: ReviewType.recall,
  wasVariant: false,
  prompt: prompt,
  expectedAnswer: 'Spring의 선언적 트랜잭션은 프록시 기반이다. 내부 호출은 프록시를 거치지 않는다.',
  rubric: rubric,
  dueDate: testToday,
  overdueDays: 0,
);

String dueItemId(int index) => 'f1000000-0000-4000-8000-00000000000$index';

DueReviewsResponse testDueReviews({int count = 3}) => DueReviewsResponse(
  planDate: testToday,
  cap: 20,
  comebackMode: false,
  totalDueCount: count,
  items: [
    for (var index = 1; index <= count; index++)
      testDueItem(
        id: dueItemId(index),
        skillName: index.isEven ? 'Collection' : 'Spring Transaction',
        prompt: '복습 문항 $index',
      ),
  ],
);

DashboardView testDashboard({
  bool generated = true,
  String? mainTaskId = mainTaskId,
  TaskStatus mainStatus = TaskStatus.inProgress,
  int dueReviewCount = 6,
  bool replanRecommended = false,
}) => DashboardView(
  today: testToday,
  todaySummary: TodaySummaryView(
    generated: generated,
    mainTaskId: generated ? mainTaskId : null,
    mainTaskTitle: generated && mainTaskId != null ? 'Spring Transaction 내 말로 설명하기' : null,
    mainTaskType: generated && mainTaskId != null ? TaskType.explain : null,
    mainTaskStatus: generated && mainTaskId != null ? mainStatus : null,
    mainTaskEstimatedMinutes: generated && mainTaskId != null ? 15 : null,
    reviewTaskStatus: generated ? TaskStatus.planned : null,
  ),
  dueReviewCount: dueReviewCount,
  weekStartDate: '2026-09-14',
  weekStudyMinutes: 190,
  weekCompletedSessions: 4,
  aiStatus: AiStatus.disabled,
  replanRecommended: replanRecommended,
);

BudgetView testBudget({RiskLevel risk = RiskLevel.high, int? ratioBp = 11950}) => BudgetView(
  planId: planId,
  today: testToday,
  horizonDate: '2027-04-01',
  nominalBudgetMinutes: 7020,
  completionRateBp: 7000,
  effectiveBudgetMinutes: 4914,
  requiredMustMinutes: 5880,
  requiredShouldMinutes: 1810,
  ratioBp: ratioBp,
  riskLevel: risk,
);

SkillRef testSkillRef(String code, String name, SkillCategory category) =>
    SkillRef(id: 'skill-$code', code: code, name: name, category: category);

/// A HIGH-risk preview with one deferral and one MUST target reduction (docs/05 §7.7 example).
ReplanPreviewResponse testShrinkPreview({RiskLevel risk = RiskLevel.high}) => ReplanPreviewResponse(
  planId: planId,
  today: testToday,
  horizonDate: '2027-04-01',
  nominalBudgetMinutes: 2820,
  completionRateBp: 6800,
  effectiveBudgetMinutes: 1917,
  requiredMustMinutes: 2240,
  requiredShouldMinutes: 900,
  ratioBp: 11684,
  riskLevel: risk,
  deferSuggestions: [
    DeferSuggestionView(
      skill: testSkillRef('DEVOPS.KUBERNETES_BASICS', 'Kubernetes 기초', SkillCategory.devops),
      priority: Priority.should,
      practicalImportanceBp: 3000,
      requiredMinutes: 480,
    ),
  ],
  mustTargetReductionSuggestions: [
    TargetReductionSuggestionView(
      skill: testSkillRef('DATABASE.EXECUTION_PLAN', '실행계획 읽기', SkillCategory.database),
      axis: SkillAxis.implementation,
      currentTarget: 4,
      newTarget: 3,
      planningLevel: 1,
      savedMinutes: 172,
    ),
  ],
  expansionSuggestions: const [],
  riskAfterSuggestions: const RiskEstimateView(
    requiredMustMinutes: 1896,
    requiredShouldMinutes: 0,
    ratioBp: 9890,
    riskLevel: RiskLevel.medium,
  ),
);

/// A LOW-risk preview with a restore and a MUST target raise (docs/06 §4.4 vector 3).
ReplanPreviewResponse testExpandPreview() => ReplanPreviewResponse(
  planId: planId,
  today: testToday,
  horizonDate: '2027-04-01',
  nominalBudgetMinutes: 7143,
  completionRateBp: 7000,
  effectiveBudgetMinutes: 5000,
  requiredMustMinutes: 3400,
  requiredShouldMinutes: 900,
  ratioBp: 6800,
  riskLevel: RiskLevel.low,
  deferSuggestions: const [],
  mustTargetReductionSuggestions: const [],
  expansionSuggestions: [
    ExpansionSuggestionView(
      kind: ExpansionKind.restoreDeferred,
      skill: testSkillRef('SYSTEM_DESIGN.CACHE', 'Cache', SkillCategory.systemDesign),
      priority: Priority.should,
      practicalImportanceBp: 8000,
      addedMinutes: 600,
    ),
    ExpansionSuggestionView(
      kind: ExpansionKind.raiseTarget,
      skill: testSkillRef('SPRING.TRANSACTION', 'Spring Transaction', SkillCategory.spring),
      priority: Priority.must,
      practicalImportanceBp: 9000,
      axis: SkillAxis.explanation,
      currentTarget: 3,
      newTarget: 4,
      addedMinutes: 500,
    ),
  ],
  riskAfterSuggestions: const RiskEstimateView(
    requiredMustMinutes: 3900,
    requiredShouldMinutes: 900,
    ratioBp: 7800,
    riskLevel: RiskLevel.low,
  ),
);
