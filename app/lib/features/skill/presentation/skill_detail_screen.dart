import 'package:devpilot_app/app/not_found_screen.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/badges.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/skill/data/skill_models.dart';
import 'package:devpilot_app/features/skill/data/skill_repository.dart';
import 'package:devpilot_app/features/skill/domain/skill_overview.dart';
import 'package:devpilot_app/features/skill/presentation/learning_stage_section.dart';
import 'package:devpilot_app/features/skill/presentation/skill_detail_actions.dart';
import 'package:devpilot_app/features/skill/presentation/skill_detail_controller.dart';
import 'package:devpilot_app/features/skill/presentation/skill_history_section.dart';
import 'package:devpilot_app/features/skill/presentation/skill_tree_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// SCR-SKILL-DETAIL: header, axis table, self-assessment line, the buttons to this skill's cards,
/// challenges and the rubber duck, and the level change history (docs/02 §3.10).
class SkillDetailScreen extends ConsumerWidget {
  const SkillDetailScreen({super.key, required this.skillId});

  final String skillId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final data = ref.watch(skillOverviewDataProvider);
    final row = data.value == null
        ? null
        : SkillOverview.find(data.requireValue.$1, data.requireValue.$2, skillId);
    if (data.hasValue && row == null) {
      return const NotFoundScreen();
    }
    return Scaffold(
      appBar: AppBar(
        title: Semantics(header: true, child: Text(row?.node.name ?? l10n.skillTreeTitle)),
      ),
      body: ScreenBody(
        child: data.when(
          loading: () => const SkeletonList(count: 2, lines: 4),
          error: (error, _) => ErrorView(
            error: error,
            onRetry: () {
              ref.invalidate(skillTreeProvider);
              ref.invalidate(mySkillStatesProvider);
            },
          ),
          data: (_) => _SkillDetail(row: row!),
        ),
      ),
    );
  }
}

class _SkillDetail extends StatelessWidget {
  const _SkillDetail({required this.row});

  final SkillRow row;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final priority = row.priority;
    final description = row.node.description;
    final state = row.state;
    final selfLevel = state?.selfAssessedLevel;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Wrap(
          spacing: AppSpacing.sm,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Text(row.node.category.label(l10n), style: textTheme.bodyMedium),
            if (priority != null) PriorityBadge(priority: priority),
          ],
        ),
        if (description != null && description.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.sm),
          Text(description),
        ],
        _WhyItMatters(skillId: row.node.id),
        const SizedBox(height: AppSpacing.lg),
        LearningStageSection(skillId: row.node.id),
        const SizedBox(height: AppSpacing.lg),
        _AxisTable(row: row),
        const SizedBox(height: AppSpacing.lg),
        if (selfLevel != null)
          Text(
            l10n.skillDetailSelf(skillLevelLabel(selfLevel, l10n)),
            key: const Key('skillDetail.selfLine'),
          ),
        if (state != null && selfLevel != null) _Calibration(state: state, selfLevel: selfLevel),
        const SizedBox(height: AppSpacing.lg),
        SkillDetailActions(
          skillId: row.node.id,
          skillCode: row.node.code,
          skillName: row.node.name,
        ),
        const SizedBox(height: AppSpacing.xl),
        SkillHistorySection(skillId: row.node.id),
      ],
    );
  }
}

/// 말한 수준과 기록을 **한 문장으로 나란히** 놓는다 (ADR-065).
///
/// 위의 축 표가 증거·계획용·목표를 숫자로 보여 주지만, 숫자 세 열만으로는 "내가 2라고 했는데 기록은 0"이라는
/// 사실이 읽히지 않는다. Open Learner Model 연구는 자기평가와 시스템 모델의 **일치/불일치를 보여 주면**
/// 자기 점검과 성취가 개선되고 **특히 낮은 성취자에게 그렇다**고 본다.
///
/// 판정이 아니다. 세 문구 모두 다음에 무엇이 달라지는지로 끝난다 — 틀렸다고 말하는 자리가 아니다(ADR-062).
class _Calibration extends StatelessWidget {
  const _Calibration({required this.state, required this.selfLevel});

  final UserSkillStateView state;
  final int selfLevel;

  /// 자기평가가 planning에 보태는 상한 (docs/06 §7.5 {@code selfCap}).
  static const _maxClaim = 3;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final style = Theme.of(context).textTheme.bodySmall;
    final claim = selfLevel < _maxClaim ? selfLevel : _maxClaim;
    // 기록이 주장을 따라잡았는지는 **가장 높은 축**으로 본다 — 한 축이라도 닿으면 그 축에서는 기록이 쓰인다
    var best = 0;
    for (final axis in SkillAxis.known) {
      final level = state.evidenceLevels.of(axis);
      if (level > best) {
        best = level;
      }
    }
    final self = skillLevelLabel(selfLevel, l10n);
    final evidence = skillLevelLabel(best, l10n);
    final String message;
    if (!state.selfAssessmentActive) {
      message = l10n.skillDetailCalibrationWithdrawn(self, evidence);
    } else if (best >= claim) {
      message = l10n.skillDetailCalibrationMet(self);
    } else {
      message = l10n.skillDetailCalibrationAhead(self, evidence);
    }
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.xs),
      child: Text(message, key: const Key('skillDetail.calibration'), style: style),
    );
  }
}

/// 이 기술을 왜 하는지 한 줄 (docs/05 §6.4). 노트가 없으면 줄 자체를 숨긴다 — 빈 자리를 남기지 않는다.
class _WhyItMatters extends ConsumerWidget {
  const _WhyItMatters({required this.skillId});

  final String skillId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final why = ref.watch(skillDetailProvider(skillId)).value?.whyItMatters;
    if (why == null || why.isEmpty) {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: const EdgeInsets.only(top: AppSpacing.sm),
      child: Text(
        why,
        key: const Key('skillDetail.whyItMatters'),
        style: Theme.of(context).textTheme.bodyMedium,
      ),
    );
  }
}

/// `축 | 증거 레벨 | 계획용 레벨 | 목표`, each level as a number and its `SkillLevel` label.
class _AxisTable extends StatelessWidget {
  const _AxisTable({required this.row});

  final SkillRow row;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final headerStyle = Theme.of(context).textTheme.labelLarge;
    String level(int value) => l10n.skillDetailLevel(value, skillLevelLabel(value, l10n));
    final targets = row.targets;
    return Table(
      key: const Key('skillDetail.axisTable'),
      columnWidths: const {0: IntrinsicColumnWidth()},
      defaultVerticalAlignment: TableCellVerticalAlignment.middle,
      children: [
        TableRow(
          children: [
            for (final title in [
              l10n.skillDetailAxis,
              l10n.skillDetailEvidence,
              l10n.skillDetailPlanning,
              l10n.skillDetailTarget,
            ])
              Padding(
                padding: const EdgeInsets.all(AppSpacing.xs),
                child: Text(title, style: headerStyle),
              ),
          ],
        ),
        for (final axis in SkillAxis.known)
          TableRow(
            children: [
              for (final cell in [
                axis.label(l10n),
                level(row.evidence.of(axis)),
                level(row.planning.of(axis)),
                if (targets == null) '—' else level(targets.of(axis)),
              ])
                Padding(padding: const EdgeInsets.all(AppSpacing.xs), child: Text(cell)),
            ],
          ),
      ],
    );
  }
}
