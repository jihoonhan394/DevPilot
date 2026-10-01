import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/features/plan/data/plan_budget_models.dart';
import 'package:devpilot_app/features/plan/data/plan_buildable_models.dart';
import 'package:devpilot_app/features/plan/domain/budget_display.dart';
import 'package:devpilot_app/features/plan/presentation/budget_risk_card.dart';
import 'package:devpilot_app/features/plan/presentation/buildable_providers.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-TODAY 위쪽의 가이드 (ADR-062, docs/06 §3.4).
///
/// 목표일을 못 지킬 때 이 자리가 하는 말은 **"못 지킵니다"가 아니다.** 첫날 빨간 배지는 아무것도 바꿔 주지 않으면서
/// 시작할 마음만 가져간다. 대신 셋을 말한다:
///
/// 1. 방향은 맞다
/// 2. 지금 실력으로 어디까지 해 볼 수 있다
/// 3. 전부 하려면 언제쯤이다 — 그리고 **꾸준히 하면 그 날짜가 당겨진다**
///
/// 3번은 격려가 아니라 계산이다. 완료율은 최근 28일의 실제/가능 비율이고(docs/06 §3.3), 그게 오르면 역산 날짜가
/// 실제로 앞으로 온다.
///
/// 예산을 못 읽으면 **아무것도 그리지 않는다.** 계획이 없는 사람이나 서버가 오래된 사람에게 오류를 보여 줄 자리가
/// 아니다 — 오류는 SCR-PLAN의 `BudgetRiskCard`가 맡는다.
class GoalGuide extends ConsumerWidget {
  const GoalGuide({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final budget = ref.watch(activeBudgetProvider).value;
    if (budget == null) {
      return const SizedBox.shrink();
    }
    return _GuideBody(budget: budget, buildable: ref.watch(buildableProvider).value);
  }
}

class _GuideBody extends StatelessWidget {
  const _GuideBody({required this.budget, this.buildable});

  final BudgetView budget;
  final BuildableView? buildable;

  /// 지금 근거만으로 닿는 가장 먼 단계 (ADR-060). 없으면 null — 그때는 "어디까지" 줄을 뺀다.
  String? get _reach {
    final steps = buildable?.steps;
    if (steps == null) {
      return null;
    }
    for (final step in steps.reversed) {
      if (step.status == BuildableStatus.buildable) {
        return step.title;
      }
    }
    return null;
  }

  /// 목표일 안에 끝나는가. `feasibleCompletionDate`가 목표일(horizon)을 넘지 않으면 빠듯하지 않다.
  bool _onTime(LocalDate feasible) {
    final horizon = LocalDate.tryParse(budget.horizonDate);
    return horizon == null || feasible.compareTo(horizon) <= 0;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final feasible = LocalDate.tryParse(budget.feasibleCompletionDate);
    final reach = _reach;
    // 날짜가 넉넉하면 날짜 이야기를 꺼내지 않는다. 할 말이 없을 때 말하지 않는 것도 가이드다.
    final late = feasible == null || !_onTime(feasible);
    return Card(
      key: const Key('today.goalGuide'),
      elevation: 0,
      color: theme.colorScheme.surfaceContainerLow,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.guideDirectionOk,
              key: const Key('today.guideDirection'),
              style: theme.textTheme.titleSmall,
            ),
            if (reach != null) ...[
              const SizedBox(height: AppSpacing.xs),
              Text(l10n.guideNowReach(reach), key: const Key('today.guideReach')),
            ],
            const SizedBox(height: AppSpacing.xs),
            Text(_dateLine(l10n, feasible), key: const Key('today.guideDate')),
            if (late) ...[
              const SizedBox(height: AppSpacing.xs),
              Text(
                l10n.guidePullsDateIn,
                key: const Key('today.guidePull'),
                style: theme.textTheme.bodySmall,
              ),
            ],
            // 날짜가 자기평가 위에 서 있으면 그렇다고 말한다. 빨간 배지의 반대 방향 거짓말도 거짓말이다 —
            // 한 번도 해 보지 않은 사람에게 "안에 들어와요"라고만 하면 그 말을 믿고 아무것도 안 하게 된다.
            if (budget.completionRateEstimated) ...[
              const SizedBox(height: AppSpacing.xs),
              Text(
                l10n.guideFromSelfAssessment,
                key: const Key('today.guideEstimated'),
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              // 주장을 기록으로 바꾸는 문이 확인 문제다 (ADR-063). 화면에 "(선택)"으로만 두면 안 누른다.
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(
                  key: const Key('today.guideCheckLevel'),
                  onPressed: () => context.go(AppRoutes.diagnostics),
                  child: Text(l10n.guideCheckLevel),
                ),
              ),
            ],
            if (budget.requiredMustLaterMinutes > 0) ...[
              const SizedBox(height: AppSpacing.xs),
              Text(
                l10n.guideLaterHours(BudgetDisplay.hours(budget.requiredMustLaterMinutes)),
                key: const Key('today.guideLater'),
                style: theme.textTheme.bodySmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
            if (late) _GuideActions(),
          ],
        ),
      ),
    );
  }

  String _dateLine(AppLocalizations l10n, LocalDate? feasible) {
    if (feasible == null) {
      // 5년 안에 닿지 않는다 — 날짜로 답할 문제가 아니다 (docs/06 §3.4)
      return l10n.guideNoDate;
    }
    if (_onTime(feasible)) {
      return l10n.guideFeasibleOnTime;
    }
    return l10n.guideFeasibleDate(l10n.commonLongDate(feasible.toDateTime()));
  }
}

/// 사용자가 할 수 있는 선택 둘. 도구가 고르지 않는다 — 범위를 줄이든 날짜를 미루든 그대로 가든 사용자의 몫이다.
///
/// 이동은 `go`다 — 이 라우터에서는 `push`가 주소를 바꾸지 않는다(docs/02 §3.14).
class _GuideActions extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Wrap(
      spacing: AppSpacing.sm,
      children: [
        TextButton(
          key: const Key('today.guideShrink'),
          onPressed: () => context.go(AppRoutes.replanFrom(AppRoutes.replanFromGoal)),
          child: Text(l10n.guideShrink),
        ),
        TextButton(
          key: const Key('today.guideChangeDate'),
          onPressed: () => context.go(AppRoutes.learningGoal),
          child: Text(l10n.guideChangeDate),
        ),
      ],
    );
  }
}
