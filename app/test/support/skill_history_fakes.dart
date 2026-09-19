import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/skill/data/skill_history_models.dart';

import 'fixtures.dart';

// Skill level change history of `GET /skills/{skillId}/history` (docs/05 §6.3).

const springTransactionSkillId = 'b1000000-0000-4000-8000-000000000001';

EvidenceEventView testEvidenceEvent({
  String id = 'e2000000-0000-4000-8000-000000000001',
  LearningEventType eventType = LearningEventType.reviewAnswered,
  String planDate = '2026-09-17',
  bool invalidated = false,
}) => EvidenceEventView(
  id: id,
  eventType: eventType,
  planDate: planDate,
  occurredAt: testInstant,
  invalidated: invalidated,
);

SkillStateChangeView testSkillStateChange({
  String id = 'e1000000-0000-4000-8000-000000000001',
  SkillAxis axis = SkillAxis.explanation,
  int fromLevel = 2,
  int toLevel = 3,
  String ruleCode = 'E3_COVERAGE',
  List<EvidenceEventView>? evidenceEvents,
}) => SkillStateChangeView(
  id: id,
  axis: axis,
  fromLevel: fromLevel,
  toLevel: toLevel,
  ruleCode: ruleCode,
  evidenceEvents: evidenceEvents ?? [testEvidenceEvent()],
  changedAt: testInstant,
);

/// Three changes: the first page holds two, "기록 더 보기" brings the third.
List<SkillStateChangeView> testSkillHistory() => [
  testSkillStateChange(
    evidenceEvents: [
      testEvidenceEvent(),
      testEvidenceEvent(
        id: 'e2000000-0000-4000-8000-000000000002',
        eventType: LearningEventType.rubberDuckCompleted,
        planDate: '2026-09-16',
        invalidated: true,
      ),
    ],
  ),
  testSkillStateChange(
    id: 'e1000000-0000-4000-8000-000000000002',
    axis: SkillAxis.knowledge,
    fromLevel: 1,
    toLevel: 2,
    ruleCode: 'K2_RECALL_GUIDED',
  ),
  testSkillStateChange(
    id: 'e1000000-0000-4000-8000-000000000003',
    axis: SkillAxis.knowledge,
    fromLevel: 0,
    toLevel: 1,
    ruleCode: 'NEW_RULE_ADDED_LATER',
    evidenceEvents: const [],
  ),
];
