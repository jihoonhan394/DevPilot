import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/core/widgets/status_badge.dart';
import 'package:devpilot_app/features/plan/data/plan_buildable_models.dart';
import 'package:devpilot_app/features/plan/presentation/buildable_providers.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-BUILDABLE (docs/02 §3.21, docs/05 §7.10). 계획의 milestone은 과목 목록이 아니라 **사이드 프로젝트를 만드는
/// 순서**다. 그 순서를 그대로 펼치고, 각 단계가 열렸는지를 근거 레벨로만 표시한다(ADR-060).
///
/// 화면 밖으로 나가는 이동은 모두 `go`다 — 이 라우터에서는 `push`가 주소를 바꾸지 않는다(docs/02 §3.14).
class BuildableScreen extends ConsumerWidget {
  const BuildableScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.buildableTitle))),
      body: ScreenBody(
        child: ref
            .watch(buildableProvider)
            .when(
              loading: () => const SkeletonList(count: 3, lines: 3),
              error: (error, _) => _error(context, ref, error),
              data: (buildable) => _Steps(buildable: buildable),
            ),
      ),
    );
  }

  Widget _error(BuildContext context, WidgetRef ref, Object error) {
    final l10n = AppLocalizations.of(context);
    if (error is ApiException && error.code == ApiErrorCode.planNotFound) {
      return EmptyState(icon: Icons.construction, message: l10n.buildableEmpty);
    }
    return ErrorView(error: error, onRetry: () => ref.invalidate(buildableProvider));
  }
}

class _Steps extends StatelessWidget {
  const _Steps({required this.buildable});

  final BuildableView buildable;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          _summary(l10n),
          key: const Key('buildable.summary'),
          style: theme.textTheme.titleMedium,
        ),
        const SizedBox(height: AppSpacing.xs),
        Text(
          l10n.buildableIntro,
          key: const Key('buildable.intro'),
          style: theme.textTheme.bodySmall?.copyWith(color: DevPilotColors.of(context).neutral),
        ),
        const SizedBox(height: AppSpacing.xs),
        _Axes(counted: buildable.countedAxes, uncounted: buildable.uncountedAxes),
        const SizedBox(height: AppSpacing.lg),
        for (final step in buildable.steps) _StepCard(step: step),
      ],
    );
  }

  String _summary(AppLocalizations l10n) {
    if (buildable.buildableStepCount == 0) {
      return l10n.buildableSummaryNone;
    }
    if (buildable.buildableStepCount == buildable.stepCount) {
      return l10n.buildableSummaryAll(buildable.stepCount);
    }
    return l10n.buildableSummary(buildable.buildableStepCount, buildable.stepCount);
  }
}

/// 무엇을 세고 무엇을 빼는지 (docs/06 §7.6, ADR-061). 조용히 빼면 "왜 9/10에서 안 움직이나"를 알 수 없다.
class _Axes extends StatelessWidget {
  const _Axes({required this.counted, required this.uncounted});

  final List<SkillAxis> counted;
  final List<SkillAxis> uncounted;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final style = Theme.of(
      context,
    ).textTheme.bodySmall?.copyWith(color: DevPilotColors.of(context).neutral);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          l10n.buildableCountedAxes(_labels(l10n, counted)),
          key: const Key('buildable.countedAxes'),
          style: style,
        ),
        if (uncounted.isNotEmpty)
          Text(
            l10n.buildableUncountedAxes(_labels(l10n, uncounted)),
            key: const Key('buildable.uncountedAxes'),
            style: style,
          ),
      ],
    );
  }

  String _labels(AppLocalizations l10n, List<SkillAxis> axes) =>
      axes.map((axis) => axis.label(l10n)).join(' · ');
}

/// 한 단계. 지금 만들 차례인 단계만 색을 쓴다 — 다 칠하면 어디부터 볼지 알 수 없다.
class _StepCard extends StatelessWidget {
  const _StepCard({required this.step});

  final BuildableStepView step;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final colors = DevPilotColors.of(context);
    final isNext = step.status == BuildableStatus.next;
    final description = step.description;
    return Card(
      key: Key('buildable.step.${step.milestoneId}'),
      color: isNext ? theme.colorScheme.primaryContainer : null,
      margin: const EdgeInsets.only(bottom: AppSpacing.md),
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(
              spacing: AppSpacing.sm,
              runSpacing: AppSpacing.xs,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                Text(
                  l10n.buildableStepNumber(step.sortOrder + 1),
                  style: theme.textTheme.labelMedium?.copyWith(color: colors.neutral),
                ),
                Text(step.title, style: theme.textTheme.titleSmall),
                _StatusBadge(status: step.status),
              ],
            ),
            if (description != null) ...[
              const SizedBox(height: AppSpacing.sm),
              Text(description, style: theme.textTheme.bodyMedium),
            ],
            const SizedBox(height: AppSpacing.sm),
            Text(
              step.gateSkillCount == 0
                  ? l10n.buildableGateNone
                  : l10n.buildableGateProgress(step.metSkillCount, step.gateSkillCount),
              style: theme.textTheme.bodySmall?.copyWith(color: colors.neutral),
            ),
            if (step.gaps.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.md),
              Text(l10n.buildableGapsTitle, style: theme.textTheme.labelLarge),
              const SizedBox(height: AppSpacing.xs),
              for (final gap in step.gaps) _GapRow(gap: gap),
              if (step.gateSkillCount - step.metSkillCount > step.gaps.length)
                Text(
                  l10n.buildableGapMore,
                  style: theme.textTheme.bodySmall?.copyWith(color: colors.neutral),
                ),
            ],
            // ADR-070: 지금 할 수 있는 것을 다 해도 원래 목표가 남을 수 있다. `gaps`가 비어 BUILDABLE이어도
            // 이 줄은 남는다 — "끝났다"와 "여기까지가 지금 할 수 있는 끝이다"는 다른 말이다.
            if (step.capabilityPending.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.md),
              Text(l10n.buildablePendingTitle, style: theme.textTheme.labelLarge),
              const SizedBox(height: AppSpacing.xs),
              Text(
                l10n.buildablePendingBody,
                key: const Key('buildable.capabilityPending'),
                style: theme.textTheme.bodySmall?.copyWith(color: colors.neutral),
              ),
              for (final pending in step.capabilityPending)
                Text(
                  l10n.buildablePendingSkill(
                    pending.skill.name,
                    _pendingAxes(pending, l10n).join(' · '),
                  ),
                  key: Key('buildable.pending.${pending.skill.code}'),
                  style: theme.textTheme.bodySmall?.copyWith(color: colors.neutral),
                ),
            ],
            if (isNext) ...[
              const SizedBox(height: AppSpacing.md),
              Align(
                alignment: Alignment.centerLeft,
                child: FilledButton(
                  key: const Key('buildable.openToday'),
                  onPressed: () => context.go(AppRoutes.today),
                  child: Text(l10n.buildableOpenToday),
                ),
              ),
              // 모자란 축을 다 채운 뒤에 시작하면 남는 게 없다 (ADR-062). 지금 단계에서 실력이 모자라도
              // 할 수 있는 일을 함께 말한다 — 이 화면이 "아직 안 된다"로만 끝나면 가이드가 아니다.
              if (step.gaps.isNotEmpty) _StartAnyway(),
            ],
          ],
        ),
      ),
    );
  }
}

/// 게이트가 덜 찬 "지금 만들 단계"에만 붙는다. 기술이 다 차기를 기다리는 동안에도 남길 것은 있다.
/// 보류된 축만 골라 "축 이름 레벨"로 만든다. 0인 축은 보류가 없다는 뜻이라 뺀다.
List<String> _pendingAxes(CapabilityPendingView pending, AppLocalizations l10n) {
  final levels = pending.pending;
  return [
    if (levels.knowledge > 0) '${l10n.enumSkillAxisKnowledge} ${levels.knowledge}',
    if (levels.implementation > 0) '${l10n.enumSkillAxisImplementation} ${levels.implementation}',
    if (levels.explanation > 0) '${l10n.enumSkillAxisExplanation} ${levels.explanation}',
    if (levels.debugging > 0) '${l10n.enumSkillAxisDebugging} ${levels.debugging}',
  ];
}

class _StartAnyway extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.md),
      child: Column(
        key: const Key('buildable.startAnyway'),
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(l10n.buildableStartAnywayTitle, style: theme.textTheme.labelLarge),
          const SizedBox(height: AppSpacing.xs),
          Text(l10n.buildableStartAnywayBody, style: theme.textTheme.bodySmall),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton(
              key: const Key('buildable.startAnywayAction'),
              onPressed: () => context.go(AppRoutes.projects),
              child: Text(l10n.buildableStartAnywayAction),
            ),
          ),
        ],
      ),
    );
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.status});

  final BuildableStatus status;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return switch (status) {
      BuildableStatus.buildable => StatusBadge(
        label: l10n.buildableStatusBuildable,
        icon: Icons.check_circle,
        tone: AppTone.success,
      ),
      BuildableStatus.next => StatusBadge(
        label: l10n.buildableStatusNext,
        icon: Icons.play_circle,
        tone: AppTone.primary,
      ),
      BuildableStatus.notYet || BuildableStatus.unknown => StatusBadge(
        label: l10n.buildableStatusNotYet,
        tone: AppTone.neutral,
      ),
    };
  }
}

/// 모자란 skill 한 줄. 부족한 축만 적는다 — 채워진 축까지 늘어놓으면 무엇이 남았는지가 묻힌다.
class _GapRow extends StatelessWidget {
  const _GapRow({required this.gap});

  final BuildableGapView gap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListTile(
      key: Key('buildable.gap.${gap.skill.code}'),
      onTap: () => context.go(AppRoutes.skillDetail(gap.skill.id)),
      dense: true,
      contentPadding: EdgeInsets.zero,
      visualDensity: VisualDensity.compact,
      title: Text(gap.skill.name, style: theme.textTheme.bodyMedium),
      subtitle: Wrap(
        spacing: AppSpacing.sm,
        children: [
          for (final axis in _shortAxes(AppLocalizations.of(context)))
            Text(
              axis,
              style: theme.textTheme.bodySmall?.copyWith(
                color: DevPilotColors.of(context).neutral,
              ),
            ),
        ],
      ),
    );
  }

  List<String> _shortAxes(AppLocalizations l10n) => [
    for (final axis in SkillAxis.values)
      if (axis != SkillAxis.unknown && _level(gap.evidenceLevels, axis) < _level(gap.targets, axis))
        l10n.buildableGapAxis(
          axis.label(l10n),
          _level(gap.evidenceLevels, axis),
          _level(gap.targets, axis),
        ),
  ];

  static int _level(AxisLevels levels, SkillAxis axis) => switch (axis) {
    SkillAxis.knowledge => levels.knowledge,
    SkillAxis.implementation => levels.implementation,
    SkillAxis.explanation => levels.explanation,
    SkillAxis.debugging || SkillAxis.unknown => levels.debugging,
  };
}
