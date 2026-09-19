import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
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
