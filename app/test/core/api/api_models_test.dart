import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/features/plan/data/plan_models.dart';
import 'package:devpilot_app/features/project/data/side_project_models.dart';
import 'package:devpilot_app/features/settings/data/me_response.dart';
import 'package:devpilot_app/features/settings/data/update_me_request.dart';
import 'package:flutter_test/flutter_test.dart';

/// Hand-written models against docs/05 examples (DEC-12). Unknown enum values become `unknown`.
void main() {
  Map<String, Object?> planJson({String status = 'ACTIVE', String milestoneStatus = 'PLANNED'}) => {
    'id': '9e2b1c7a-0f0e-4d7b-8e59-0c3e1f6b2a01',
    'planVersion': 2,
    'status': status,
    'title': 'Java 백엔드 성장 계획',
    'supersedesPlanId': '4b7d1c7a-0f0e-4d7b-8e59-0c3e1f6b2a00',
    'changeReason': '야근으로 주문 생성 milestone 2주 지연',
    'replanRecommended': false,
    'milestones': [
      {
        'id': 'a1000000-0000-4000-8000-000000000001',
        'title': '기반 다지기',
        'description': null,
        'startDate': '2026-09-30',
        'endDate': '2026-10-25',
        'priority': 'MUST',
        'status': milestoneStatus,
        'sortOrder': 0,
        'skillCodes': ['SPRING.MVC_REST', 'TESTING.JUNIT'],
        'updatedAt': '2026-10-20T11:00:00Z',
        'version': 1,
      },
    ],
    'skillTargets': [
      {
        'skill': {
          'id': 'b1000000-0000-4000-8000-000000000001',
          'code': 'SPRING.TRANSACTION',
          'name': 'Spring Transaction',
          'category': 'SPRING',
        },
        'priority': 'MUST',
        'practicalImportanceBp': 9000,
        'targets': {'knowledge': 4, 'implementation': 4, 'explanation': 4, 'debugging': 3},
        'deferred': false,
        'adjustment': 'ROLE_DEFAULT',
      },
    ],
    'latestSnapshot': {
      'snapshotDate': '2026-10-20',
      'horizonDate': '2027-01-05',
      'nominalBudgetMinutes': 7020,
      'completionRateBp': 7000,
      'effectiveBudgetMinutes': 4914,
      'requiredMustMinutes': 4620,
      'requiredShouldMinutes': 1810,
      'ratioBp': null,
      'riskLevel': 'MEDIUM',
      'generatedAt': '2026-10-19T19:05:02.123456Z',
    },
    'createdAt': '2026-10-20T11:00:00Z',
    'supersededAt': null,
    'version': 0,
  };

  test('shouldParsePlanViewExampleAndWriteItBack', () {
    final plan = PlanView.fromJson(planJson());

    expect(plan.planVersion, 2);
    expect(plan.milestones.single.status, MilestoneStatus.planned);
    expect(plan.skillTargets.single.adjustment, TargetAdjustment.roleDefault);
    expect(plan.latestSnapshot?.riskLevel, RiskLevel.medium);
    expect(plan.latestSnapshot?.ratioBp, isNull);
    expect(PlanView.fromJson(plan.toJson()), plan);
  });

  test('shouldMapUnknownEnumValuesToUnknownInsteadOfFailing', () {
    final plan = PlanView.fromJson(planJson(status: 'ARCHIVED_V2', milestoneStatus: 'PAUSED'));

    expect(plan.status, PlanStatus.unknown);
    expect(plan.milestones.single.status, MilestoneStatus.unknown);
  });

  test('shouldParseCursorPageOfSideProjects', () {
    final page = CursorPage.fromJson(
      {
        'items': [
          {
            'id': '3f7c0000-0000-4000-8000-000000000001',
            'name': '주문 시스템',
            'description': null,
            'repoUrl': null,
            'stack': null,
            'status': 'ARCHIVED',
            'createdAt': '2026-09-30T12:10:44Z',
            'updatedAt': '2026-09-30T12:10:44Z',
            'version': 0,
          },
        ],
        'nextCursor': null,
      },
      (item) => SideProjectView.fromJson(item! as Map<String, Object?>),
    );

    expect(page.items.single.status, SideProjectStatus.unknown);
    expect(page.nextCursor, isNull);
  });

  test('shouldParseMeResponseExample', () {
    final me = MeResponse.fromJson({
      'id': '5a1d7c1e-3f4b-4f39-9a0b-6e9f4c2b8d10',
      'displayName': 'MT',
      'role': 'USER',
      'status': 'ACTIVE',
      'timezone': 'Asia/Seoul',
      'dayStartHour': 4,
      'weekdayStudyMinutes': 45,
      'weekendStudyMinutes': 240,
      'onboardingCompleted': true,
      'onboardingCompletedAt': '2026-09-30T12:10:44Z',
      'today': '2026-11-20',
      'calendarSubscribed': false,
      'deletionRequestedAt': null,
      'aiStatus': 'SOMETHING_NEW',
      'aiUsage': {
        'todayCalls': 7,
        'dailyCallLimit': 60,
        'monthCostUsd': '20.41',
        'monthlyBudgetUsd': '25.00',
      },
      'createdAt': '2026-09-30T12:02:01Z',
      'version': 3,
    });

    expect(me.onboardingCompleted, isTrue);
    expect(me.today, '2026-11-20');
    expect(me.aiStatus, AiStatus.unknown);
  });

  test('shouldSendOnlyChangedFieldsInPatchRequests', () {
    expect(const UpdateMeRequest(dayStartHour: 6, version: 3).toJson(), {
      'dayStartHour': 6,
      'version': 3,
    });
    expect(
      const MilestonePatchRequest(status: MilestoneStatus.inProgress, version: 0).toJson(),
      {'status': 'IN_PROGRESS', 'version': 0},
    );
    expect(const SideProjectPatchRequest(repoUrl: '', version: 1).toJson(), {
      'repoUrl': '',
      'version': 1,
    });
  });
}
