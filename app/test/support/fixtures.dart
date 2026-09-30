import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/features/plan/data/learning_goal_models.dart';
import 'package:devpilot_app/features/plan/data/plan_buildable_models.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/features/project/data/side_project_models.dart';
import 'package:devpilot_app/features/settings/data/me_response.dart';
import 'package:devpilot_app/features/skill/data/skill_models.dart';

// Test data shaped like the docs/05 examples. Dates are fixed: "today" is 2026-09-19.

const testToday = '2026-09-19';
final testInstant = DateTime.utc(2026, 9, 18, 3);

const planId = '9e2b1c7a-0f0e-4d7b-8e59-0c3e1f6b2a01';
const previousPlanId = '4b7d1c7a-0f0e-4d7b-8e59-0c3e1f6b2a00';
const milestoneFoundationId = 'a1000000-0000-4000-8000-000000000001';
const milestoneAuthId = 'a1000000-0000-4000-8000-000000000002';
const milestoneOrderId = 'a1000000-0000-4000-8000-000000000003';
const buildableGapSkillId = 'b1000000-0000-4000-8000-000000000001';

MeResponse testMe({
  bool onboardingCompleted = true,
  UserStatus status = UserStatus.active,
  String displayName = 'MT',
  String timezone = 'Asia/Seoul',
  int dayStartHour = 4,
  int version = 3,
  AiStatus aiStatus = AiStatus.disabled,
  AiUsageView aiUsage = const AiUsageView(
    todayCalls: 0,
    dailyCallLimit: 60,
    monthCostUsd: '0.00',
    monthlyBudgetUsd: '25.00',
  ),
}) => MeResponse(
  id: '5a1d7c1e-3f4b-4f39-9a0b-6e9f4c2b8d10',
  displayName: displayName,
  role: UserRole.user,
  status: status,
  timezone: timezone,
  dayStartHour: dayStartHour,
  weekdayStudyMinutes: 45,
  weekendStudyMinutes: 240,
  onboardingCompleted: onboardingCompleted,
  onboardingCompletedAt: onboardingCompleted ? testInstant : null,
  today: testToday,
  calendarSubscribed: false,
  deletionRequestedAt: null,
  aiStatus: aiStatus,
  aiUsage: aiUsage,
  createdAt: testInstant,
  version: version,
);

MilestoneView testMilestone({
  required String id,
  required String title,
  required int sortOrder,
  String startDate = '2026-09-01',
  String endDate = '2026-09-30',
  Priority priority = Priority.must,
  MilestoneStatus status = MilestoneStatus.planned,
  String? description,
  int version = 0,
}) => MilestoneView(
  id: id,
  title: title,
  description: description,
  startDate: startDate,
  endDate: endDate,
  priority: priority,
  status: status,
  sortOrder: sortOrder,
  skillCodes: const ['SPRING.MVC_REST'],
  updatedAt: testInstant,
  version: version,
);

PlanView testPlan({
  String id = planId,
  int planVersion = 1,
  int version = 0,
  PlanStatus status = PlanStatus.active,
  bool replanRecommended = false,
  List<MilestoneView>? milestones,
}) => PlanView(
  id: id,
  planVersion: planVersion,
  status: status,
  title: 'Java 백엔드 성장 계획',
  supersedesPlanId: null,
  changeReason: null,
  replanRecommended: replanRecommended,
  milestones:
      milestones ??
      [
        testMilestone(id: milestoneFoundationId, title: '기반 다지기', sortOrder: 0),
        testMilestone(
          id: milestoneAuthId,
          title: '회원과 인증',
          sortOrder: 1,
          startDate: '2026-10-01',
          endDate: '2026-10-31',
        ),
        testMilestone(
          id: milestoneOrderId,
          title: '주문 생성',
          sortOrder: 2,
          startDate: '2026-11-01',
          endDate: '2026-11-30',
          priority: Priority.should,
        ),
      ],
  skillTargets: const [],
  latestSnapshot: null,
  createdAt: testInstant,
  supersededAt: null,
  version: version,
);

/// `GET /plans/active/buildable`: step 1 buildable, step 2 the one to build now.
BuildableView testBuildable({
  List<BuildableStepView>? steps,
  String? nextStepId = milestoneAuthId,
  int buildableStepCount = 1,
}) => BuildableView(
  planId: planId,
  planVersion: 1,
  today: '2026-10-05',
  buildableStepCount: buildableStepCount,
  stepCount: steps?.length ?? 3,
  nextStepId: nextStepId,
  steps:
      steps ??
      [
        testBuildableStep(
          milestoneId: milestoneFoundationId,
          title: '기반 다지기',
          sortOrder: 0,
          status: BuildableStatus.buildable,
          metSkillCount: 4,
          gateSkillCount: 4,
        ),
        testBuildableStep(
          milestoneId: milestoneAuthId,
          title: '회원과 인증',
          sortOrder: 1,
          status: BuildableStatus.next,
          metSkillCount: 1,
          gateSkillCount: 3,
          gaps: [
            testBuildableGap('SPRING.SECURITY', 'Spring Security', SkillCategory.spring),
            testBuildableGap('WEB_HTTP.CORS', 'CORS', SkillCategory.webHttp),
          ],
        ),
        testBuildableStep(
          milestoneId: milestoneOrderId,
          title: '주문 생성',
          sortOrder: 2,
          status: BuildableStatus.notYet,
          metSkillCount: 0,
          gateSkillCount: 2,
          gaps: [
            testBuildableGap('SPRING.TRANSACTION', 'Spring Transaction', SkillCategory.spring),
          ],
        ),
      ],
);

BuildableStepView testBuildableStep({
  required String milestoneId,
  required String title,
  required int sortOrder,
  required BuildableStatus status,
  required int metSkillCount,
  required int gateSkillCount,
  String? description = '이 단계에서 무엇을 만드는지 적어 둔 안내.',
  List<BuildableGapView> gaps = const [],
}) => BuildableStepView(
  milestoneId: milestoneId,
  title: title,
  description: description,
  sortOrder: sortOrder,
  startDate: '2026-09-01',
  endDate: '2026-09-30',
  status: status,
  metSkillCount: metSkillCount,
  gateSkillCount: gateSkillCount,
  gaps: gaps,
);

BuildableGapView testBuildableGap(
  String code,
  String name,
  SkillCategory category, {
  String id = buildableGapSkillId,
}) => BuildableGapView(
  skill: SkillRef(id: id, code: code, name: name, category: category),
  evidenceLevels: const AxisLevels(
    knowledge: 1,
    implementation: 0,
    explanation: 0,
    debugging: 0,
  ),
  targets: const AxisLevels(knowledge: 4, implementation: 4, explanation: 3, debugging: 3),
);

PlanSummaryView testPlanSummary({
  String id = planId,
  int planVersion = 1,
  PlanStatus status = PlanStatus.active,
  String? changeReason,
}) => PlanSummaryView(
  id: id,
  planVersion: planVersion,
  status: status,
  title: 'Java 백엔드 성장 계획',
  changeReason: changeReason,
  milestoneCount: 3,
  latestRiskLevel: null,
  latestRatioBp: null,
  createdAt: testInstant,
  supersededAt: null,
);

LearningGoalView testGoal({
  String completion = '2027-04-01',
  int version = 0,
}) => LearningGoalView(
  id: 'c0a10000-0000-4000-8000-000000000001',
  targetRole: TargetRole.javaBackend,
  targetCompletionDate: completion,
  focusSkills: const [],
  replanRecommended: false,
  createdAt: testInstant,
  updatedAt: testInstant,
  version: version,
);

const _springTransactionId = 'b1000000-0000-4000-8000-000000000001';
const _javaCollectionId = 'b1000000-0000-4000-8000-000000000002';

SkillTreeResponse testSkillTree() => const SkillTreeResponse(
  role: TargetRole.javaBackend,
  catalogVersion: 1,
  skills: [
    SkillNodeView(
      id: _javaCollectionId,
      code: 'JAVA.COLLECTION',
      name: 'Collection',
      category: SkillCategory.java,
      parentCode: 'JAVA',
      description: '자료구조와 컬렉션',
      minutesPerLevelStep: 120,
      sortOrder: 1,
      prerequisiteCodes: [],
      roleTarget: RoleTargetView(
        priority: Priority.must,
        practicalImportanceBp: 8000,
        targets: AxisLevels(knowledge: 4, implementation: 4, explanation: 3, debugging: 3),
      ),
    ),
    SkillNodeView(
      id: _springTransactionId,
      code: 'SPRING.TRANSACTION',
      name: 'Spring Transaction',
      category: SkillCategory.spring,
      parentCode: 'SPRING',
      description: '트랜잭션 경계',
      minutesPerLevelStep: 150,
      sortOrder: 1,
      prerequisiteCodes: [],
      roleTarget: RoleTargetView(
        priority: Priority.must,
        practicalImportanceBp: 9000,
        targets: AxisLevels(knowledge: 4, implementation: 4, explanation: 4, debugging: 3),
      ),
    ),
  ],
);

UserSkillStatesResponse testSkillStates() => const UserSkillStatesResponse(
  items: [
    UserSkillStateView(
      skill: SkillRef(
        id: _javaCollectionId,
        code: 'JAVA.COLLECTION',
        name: 'Collection',
        category: SkillCategory.java,
      ),
      evidenceLevels: AxisLevels(knowledge: 4, implementation: 4, explanation: 3, debugging: 3),
      planningLevels: AxisLevels(knowledge: 4, implementation: 4, explanation: 3, debugging: 3),
      selfAssessedLevel: null,
      selfAssessmentActive: true,
      evidenceCount: 5,
    ),
    UserSkillStateView(
      skill: SkillRef(
        id: _springTransactionId,
        code: 'SPRING.TRANSACTION',
        name: 'Spring Transaction',
        category: SkillCategory.spring,
      ),
      evidenceLevels: AxisLevels.zero,
      planningLevels: AxisLevels(knowledge: 2, implementation: 1, explanation: 3, debugging: 0),
      selfAssessedLevel: 3,
      selfAssessmentActive: true,
      evidenceCount: 0,
    ),
  ],
);

SideProjectView testProject({
  required String id,
  String name = '주문 시스템',
  SideProjectStatus status = SideProjectStatus.active,
  String? description = '회원가입 · 상품 · 주문 · 취소까지 직접 만드는 학습용 백엔드',
  String? repoUrl = 'https://github.com/example/order-service',
  String? stack = 'Spring Boot, PostgreSQL',
  int version = 0,
}) => SideProjectView(
  id: id,
  name: name,
  description: description,
  repoUrl: repoUrl,
  stack: stack,
  status: status,
  createdAt: testInstant,
  updatedAt: testInstant,
  version: version,
);

/// 학습 단계 6칸. 선언 순서를 그대로 쓴다 — 화면이 정렬하지 않는지 보려면 응답이 그 순서여야 한다.
List<LearningStageView> testLearningStages({Set<LearningStage> completed = const {}}) => [
  for (final stage in LearningStage.values)
    if (stage != LearningStage.unknown)
      LearningStageView(
        stage: stage,
        completed: completed.contains(stage),
        completedAt: completed.contains(stage) ? DateTime.utc(2026, 9, 20, 3) : null,
      ),
];

SkillDetailView testSkillDetail({
  String skillId = _javaCollectionId,
  String? whyItMatters = '컬렉션 선택이 틀리면 데이터가 많아졌을 때 느려지는 자리가 여기다.',
  Set<LearningStage> completed = const {},
}) => SkillDetailView(
  skill: SkillRef(
    id: skillId,
    code: 'JAVA.COLLECTION',
    name: 'Collection',
    category: SkillCategory.java,
  ),
  parentCode: 'JAVA',
  description: '자료구조와 컬렉션',
  whyItMatters: whyItMatters,
  minutesPerLevelStep: 120,
  evidenceLevels: const AxisLevels(knowledge: 1, implementation: 1, explanation: 0, debugging: 0),
  planningLevels: const AxisLevels(knowledge: 1, implementation: 1, explanation: 0, debugging: 0),
  learningStages: testLearningStages(completed: completed),
);
