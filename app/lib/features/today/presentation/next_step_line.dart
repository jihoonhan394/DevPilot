import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/features/today/data/today_models.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// 오늘의 핵심 카드 맨 위의 **한 줄 지시** (docs/02 SCR-TODAY "지금 할 일").
///
/// 카드에는 제목·설명·왜 오늘·확인 목록·개념 노트 버튼이 함께 있어, 처음 쓰는 사람은 그중 무엇을 먼저 눌러야 하는지
/// 알 수 없다. 이 줄은 **지금 이 순간 할 동작 하나**를 명령문으로 말한다 — 과제 종류와 상태로만 정해지므로 늘 있다.
class NextStepLine extends StatelessWidget {
  const NextStepLine({super.key, required this.task});

  final MainTaskView task;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final text = _text(l10n);
    if (text == null) {
      return const SizedBox.shrink();
    }
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.md),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: scheme.secondaryContainer,
          borderRadius: BorderRadius.circular(AppSpacing.xs),
        ),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.sm),
          child: Text(
            text,
            key: const Key('today.nextStep'),
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: scheme.onSecondaryContainer,
            ),
          ),
        ),
      ),
    );
  }

  String? _text(AppLocalizations l10n) {
    if (task.status == TaskStatus.completed) {
      return null;
    }
    if (task.status == TaskStatus.inProgress) {
      return l10n.todayNextStepInProgress;
    }
    return switch (task.taskType) {
      TaskType.reading => _readingText(l10n),
      TaskType.challenge => l10n.todayNextStepChallenge,
      TaskType.readCode => l10n.todayNextStepReadCode,
      TaskType.projectTask => l10n.todayNextStepProject,
      TaskType.explain => l10n.todayNextStepExplain,
      TaskType.redo => l10n.todayNextStepRedo,
      TaskType.recall || TaskType.review || TaskType.coachReview => l10n.todayNextStepStart,
      TaskType.unknown => l10n.todayNextStepStart,
    };
  }

  /// 개념 노트(`LESSON.*`)와 읽을거리(`DOC.*`)는 같은 `READING`이지만 하는 일이 다르다 (docs/06 §5.3 D-1).
  String _readingText(AppLocalizations l10n) {
    final key = task.readingKey;
    if (key != null && key.startsWith('LESSON.')) {
      return l10n.todayNextStepLesson;
    }
    return l10n.todayNextStepReading;
  }
}
