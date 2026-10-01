import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/time_zone_support.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/skill/data/skill_models.dart';
import 'package:devpilot_app/features/skill/presentation/skill_detail_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-SKILL-DETAIL 학습 단계 6칸 (docs/02 §3.10, docs/05 §6.4, docs/06 §5.11).
///
/// 칸은 **표시 순서**이지 잠금이 아니다 — 앞 칸이 비어 있어도 뒤 칸이 채워질 수 있다(ST-2). 그래서 "진행"은 다음에 하면 좋은 한 가지를 가리키는
/// 표시일 뿐이고 자물쇠 아이콘을 쓰지 않는다.
///
/// 저장하지 않는 파생 값이라(ADR-042) 여기서 체크하거나 되돌릴 수 없다.
class LearningStageSection extends ConsumerWidget {
  const LearningStageSection({super.key, required this.skillId});

  final String skillId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final detail = ref.watch(skillDetailProvider(skillId));
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.skillDetailStageTitle),
        const SizedBox(height: AppSpacing.sm),
        detail.when(
          loading: () => const SkeletonList(count: 1, lines: 3),
          // 단계만 실패하면 이 영역만 인라인 오류다 — 나머지 화면은 그대로 쓴다 (docs/02 §3.10)
          error: (error, _) => ErrorView(
            error: error,
            onRetry: () => ref.invalidate(skillDetailProvider(skillId)),
          ),
          data: (data) => _Stages(stages: data.learningStages),
        ),
      ],
    );
  }
}

class _Stages extends ConsumerWidget {
  const _Stages({required this.stages});

  final List<LearningStageView> stages;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final done = stages.where((stage) => stage.completed).length;
    final current = stages.indexWhere((stage) => !stage.completed);
    // 폭 360에서 6칸이 좁다. 글자를 키운 사람에게는 3+3으로 접는다 (docs/02 A-10)
    final wrap = MediaQuery.textScalerOf(context).scale(1) > 1.3;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l10n.skillDetailStageCount(done), style: theme.textTheme.labelLarge),
        const SizedBox(height: AppSpacing.sm),
        _Boxes(stages: stages, current: current, wrap: wrap),
        const SizedBox(height: AppSpacing.sm),
        if (current < 0)
          Text(l10n.skillDetailStageAllDone, key: const Key('skillDetail.stageAllDone'))
        else
          Text(
            l10n.skillDetailStageNext(stages[current].stage.label(l10n)),
            key: const Key('skillDetail.stageNext'),
          ),
        const SizedBox(height: AppSpacing.xs),
        Text(l10n.skillDetailStageNote, style: theme.textTheme.bodySmall),
        ExpansionTile(
          key: const Key('skillDetail.stageExpand'),
          tilePadding: EdgeInsets.zero,
          title: Text(l10n.skillDetailStageExpand),
          children: [
            for (var index = 0; index < stages.length; index++)
              _StageRow(stage: stages[index], isCurrent: index == current),
          ],
        ),
      ],
    );
  }
}

class _Boxes extends StatelessWidget {
  const _Boxes({required this.stages, required this.current, required this.wrap});

  final List<LearningStageView> stages;
  final int current;
  final bool wrap;

  @override
  Widget build(BuildContext context) {
    final boxes = [
      for (var index = 0; index < stages.length; index++)
        Expanded(
          child: _Box(stage: stages[index], isCurrent: index == current),
        ),
    ];
    if (!wrap) {
      return Row(crossAxisAlignment: CrossAxisAlignment.start, children: boxes);
    }
    return Column(
      children: [
        Row(crossAxisAlignment: CrossAxisAlignment.start, children: boxes.sublist(0, 3)),
        const SizedBox(height: AppSpacing.sm),
        Row(crossAxisAlignment: CrossAxisAlignment.start, children: boxes.sublist(3)),
      ],
    );
  }
}

/// 한 칸. 색만으로 구분하지 않는다 — 칸 아래에 단계 이름이 항상 글자로 있다 (docs/02 A-3).
class _Box extends StatelessWidget {
  const _Box({required this.stage, required this.isCurrent});

  final LearningStageView stage;
  final bool isCurrent;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final state = stage.completed
        ? l10n.skillDetailStageCompleted
        : isCurrent
        ? l10n.skillDetailStageCurrent
        : l10n.skillDetailStageTodo;
    return Semantics(
      label: '${stage.stage.label(l10n)} · $state',
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 2),
        child: Column(
          children: [
            Container(
              key: Key('skillDetail.stageBox.${stage.stage.name}'),
              height: 10,
              decoration: BoxDecoration(
                color: stage.completed ? theme.colorScheme.primary : Colors.transparent,
                border: Border.all(
                  color: isCurrent || stage.completed
                      ? theme.colorScheme.primary
                      : theme.colorScheme.outlineVariant,
                  width: isCurrent ? 2 : 1,
                ),
                borderRadius: BorderRadius.circular(AppRadius.sm),
              ),
            ),
            const SizedBox(height: AppSpacing.xs),
            Text(
              stage.stage.label(l10n),
              textAlign: TextAlign.center,
              style: theme.textTheme.labelSmall,
            ),
          ],
        ),
      ),
    );
  }
}

/// 펼쳤을 때의 한 줄: 단계 · 상태 · 채운 날짜, 미완료면 채우는 방법과 갈 곳.
class _StageRow extends ConsumerWidget {
  const _StageRow({required this.stage, required this.isCurrent});

  final LearningStageView stage;
  final bool isCurrent;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final completedAt = stage.completedAt;
    final state = stage.completed
        ? l10n.skillDetailStageCompleted
        : isCurrent
        ? l10n.skillDetailStageCurrent
        : l10n.skillDetailStageTodo;
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('${stage.stage.label(l10n)} · $state', style: theme.textTheme.titleSmall),
          if (completedAt != null)
            Text(
              l10n.skillDetailStageCompletedAt(
                formatInstantMonthDay(
                  completedAt,
                  ref.watch(userTimeZoneProvider),
                  ref.watch(timeZoneSupportProvider),
                  l10n,
                ),
              ),
              style: theme.textTheme.bodySmall,
            )
          else ...[
            Text(stage.stage.how(l10n), style: theme.textTheme.bodySmall),
            ?_destination(context, l10n),
          ],
        ],
      ),
    );
  }

  /// 만들기·개념 읽기·코드 읽기·재현은 Today가 내주는 과제라 버튼 없이 안내만 둔다 (docs/02 §3.10).
  Widget? _destination(BuildContext context, AppLocalizations l10n) => switch (stage.stage) {
    LearningStage.explain => TextButton(
      onPressed: () => context.push(AppRoutes.reviewItems),
      child: Text(l10n.skillDetailExplain),
    ),
    LearningStage.review => TextButton(
      onPressed: () => context.push(AppRoutes.reviewItems),
      child: Text(l10n.skillDetailReviewCards),
    ),
    _ => null,
  };
}
