import 'package:devpilot_app/app/explain_with_duck_button.dart';
import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';
import 'package:devpilot_app/features/today/presentation/reading_duck_record.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// Where "시작" continues for a task with its own screen (docs/02 SCR-TODAY "행동·검증"):
/// CHALLENGE → SCR-CHALLENGE-DETAIL, READ_CODE → SCR-READ-CODE. Other tasks stay on Today.
String? startLocationOf(MainTaskView task) {
  final challengeId = task.challengeId;
  final readingKey = task.readingKey;
  return switch (task.taskType) {
    TaskType.challenge when challengeId != null => AppRoutes.challengeDetail(
      challengeId,
      taskId: task.id,
    ),
    TaskType.readCode when readingKey != null => AppRoutes.readCode(readingKey, taskId: task.id),
    _ => null,
  };
}

/// The rubber duck an IN_PROGRESS task can explain with (docs/02 SCR-TODAY): the side project
/// for PROJECT_TASK, the skill as a concept for EXPLAIN and READING. Null when there is none (a
/// deleted project, a task without a skill).
RubberDuckLaunch? duckLaunchOf(MainTaskView task) {
  final skillCode = task.skillCode;
  final projectId = task.sideProjectId;
  final preview = RubberDuckTargetPreview(title: task.title, summary: task.description);
  return switch (task.taskType) {
    TaskType.projectTask when projectId != null => RubberDuckLaunch(
      targetType: RubberDuckTargetType.projectWork,
      targetId: projectId,
      skillCode: skillCode,
      taskId: task.id,
      preview: preview,
    ),
    TaskType.readCode => RubberDuckLaunch(
      targetType: RubberDuckTargetType.codeReading,
      targetId: task.id,
      skillCode: skillCode,
      taskId: task.id,
      preview: preview,
    ),
    TaskType.explain || TaskType.reading when skillCode != null => RubberDuckLaunch(
      targetType: RubberDuckTargetType.concept,
      conceptKey: skillCode,
      skillCode: skillCode,
      taskId: task.id,
      preview: preview,
    ),
    _ => null,
  };
}

/// The type-specific secondary action of an IN_PROGRESS main task: back to the challenge, or
/// the rubber duck (for READ_CODE only until an explanation was seen finished).
class TaskTypeLinks extends ConsumerWidget {
  const TaskTypeLinks({super.key, required this.task, required this.enabled});

  final MainTaskView task;
  final bool enabled;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final challengeId = task.challengeId;
    if (task.taskType == TaskType.challenge && challengeId != null) {
      return TextButton(
        key: const Key('today.backToChallengeButton'),
        onPressed: enabled
            ? () => context.go(AppRoutes.challengeDetail(challengeId, taskId: task.id))
            : null,
        child: Text(l10n.todayBackToChallenge),
      );
    }
    final launch = duckLaunchOf(task);
    final explained =
        task.taskType == TaskType.readCode &&
        (ref.watch(readingDuckDoneProvider(task.id)).value ?? false);
    if (launch == null || explained) {
      return const SizedBox.shrink();
    }
    return ExplainWithDuckButton(
      buttonKey: const Key('today.explainButton'),
      label: l10n.todayExplainWithDuck,
      launch: launch,
    );
  }
}
