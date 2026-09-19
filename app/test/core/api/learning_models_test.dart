import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_models.dart';
import 'package:devpilot_app/features/today/data/learning_session_models.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';
import 'package:flutter_test/flutter_test.dart';

/// S2 models against the docs/05 §7~§13 examples. Unknown enum values become `unknown`.
void main() {
  Map<String, Object?> mainTaskJson({String type = 'EXPLAIN', String status = 'PLANNED'}) => {
    'id': 'e1000000-0000-4000-8000-000000000001',
    'taskType': type,
    'skillCode': 'SPRING.TRANSACTION',
    'skillName': 'Spring Transaction',
    'milestoneId': 'a2000000-0000-4000-8000-000000000001',
    'challengeId': null,
    'sideProjectId': null,
    'readingKey': null,
    'title': 'Spring Transaction 내 말로 설명하기',
    'description': '5문장 이내로 설명하고 예시를 하나 드세요.',
    'estimatedMinutes': 15,
    'status': status,
    'reasons': [
      {'code': 'MILESTONE_CORE', 'text': 'Spring Boot/JPA milestone 핵심 항목'},
      {'code': 'SOMETHING_NEW', 'text': '새 이유'},
    ],
    'completedAt': null,
    'version': 0,
  };

  test('shouldReadTodayViewAndMapUnknownValues', () {
    final today = TodayView.fromJson({
      'dailyPlanId': 'd7f10000-0000-4000-8000-000000000001',
      'planDate': '2026-10-12',
      'availableMinutes': 45,
      'energyLevel': 'NORMAL',
      'deadlineRisk': null,
      'comebackMode': false,
      'generationCount': 1,
      'generatedAt': '2026-10-12T11:00:03Z',
      'mainTask': mainTaskJson(type: 'PAIR_PROGRAMMING', status: 'PAUSED'),
      'reviewTask': {
        'id': 'e1000000-0000-4000-8000-000000000000',
        'estimatedMinutes': 5,
        'dueReviewCount': 3,
        'status': 'PLANNED',
        'version': 0,
      },
      'earlierMainTasks': [mainTaskJson(status: 'COMPLETED')],
    });

    expect(today.deadlineRisk, isNull);
    expect(today.energyLevel, EnergyLevel.normal);
    expect(today.mainTask?.taskType, TaskType.unknown);
    expect(today.mainTask?.status, TaskStatus.unknown);
    expect(today.mainTask?.reasons.map((reason) => reason.code), [
      ReasonCode.milestoneCore,
      ReasonCode.unknown,
    ]);
    expect(today.reviewTask?.dueReviewCount, 3);
    expect(today.earlierMainTasks.single.status, TaskStatus.completed);
  });

  test('shouldWriteGenerateAndPatchRequestsAsDocumented', () {
    expect(
      const TodayGenerateRequest(
        availableMinutes: 45,
        energyLevel: EnergyLevel.normal,
        force: false,
      ).toJson(),
      {'availableMinutes': 45, 'energyLevel': 'NORMAL', 'force': false},
    );
    expect(
      const TaskStatusPatchRequest(status: TaskStatus.inProgress, version: 2).toJson(),
      {'status': 'IN_PROGRESS', 'version': 2},
    );
    expect(const SessionCompleteRequest(actualMinutes: 0).toJson(), {'actualMinutes': 0});
  });

  test('shouldReadSessionStartResponse', () {
    final response = SessionStartResponse.fromJson({
      'session': {
        'id': 'a0000000-0000-4000-8000-000000000001',
        'learningTaskId': null,
        'planDate': '2026-10-05',
        'startedAt': '2026-10-05T15:40:00Z',
        'completedAt': null,
        'actualMinutes': null,
        'selfReflection': null,
        'status': 'IN_PROGRESS',
        'version': 0,
      },
      'abandonedSessionId': 'a0000000-0000-4000-8000-000000000000',
    });

    expect(response.session.status, SessionStatus.inProgress);
    expect(response.session.startedAt, DateTime.utc(2026, 10, 5, 15, 40));
    expect(response.abandonedSessionId, isNotNull);
  });

  test('shouldReadDueReviewsAndAnswerResponse', () {
    final due = DueReviewsResponse.fromJson({
      'planDate': '2026-10-12',
      'cap': 20,
      'comebackMode': false,
      'totalDueCount': 3,
      'items': [
        {
          'reviewItemId': 'r1000000-0000-4000-8000-000000000001',
          'skillCode': 'SPRING.TRANSACTION',
          'skillName': 'Spring Transaction',
          'reviewType': 'FLASHCARD',
          'wasVariant': false,
          'prompt': '이유는?',
          'expectedAnswer': '프록시 기반이다.',
          'rubric': [
            {'id': 'R1', 'criterion': '프록시 기반 AOP 언급'},
          ],
          'dueDate': '2026-10-10',
          'overdueDays': 2,
        },
      ],
    });
    expect(due.items.single.reviewType, ReviewType.unknown);
    expect(due.items.single.rubric.single.criterion, '프록시 기반 AOP 언급');

    final answer = ReviewAnswerResponse.fromJson({
      'reviewAnswerId': 'ra1',
      'finalRating': 'HARD',
      'adjustedBy': ['HINT_CAP_HARD', 'NEW_RULE'],
      'evaluatedOutcome': 'NOT_EVALUATED',
      'rubricCoverageBp': null,
      'rubricResults': null,
      'evaluationFeedback': null,
      'intervalBefore': 2,
      'intervalAfter': 2,
      'nextDueDate': '2026-10-14',
      'evaluationSkippedReason': 'AI_UNAVAILABLE',
      'status': 'ACTIVE',
      'leechDetected': false,
      'aiMeta': null,
    });
    expect(answer.finalRating, ReviewRating.hard);
    expect(answer.adjustedBy, [RatingAdjustment.hintCapHard, RatingAdjustment.unknown]);
    expect(answer.evaluationSkippedReason, AsyncFailureCode.aiUnavailable);
  });
}
