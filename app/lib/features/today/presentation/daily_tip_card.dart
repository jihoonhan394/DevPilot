import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/features/tip/data/tip_models.dart';
import 'package:devpilot_app/features/tip/presentation/tip_providers.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-TODAY 오늘의 팁 카드 (docs/02 §3.5, docs/06 §5.12).
///
/// 여기서는 **읽기만 한다** — 세 가지 답은 상세에서 고른다. 카드에서 고르게 하면 제목 두 줄만 보고 "알고 있었어요"를 누르게 되고, 그건
/// 읽은 것도 아는 것도 아니다.
///
/// 팁이 없으면(404) 자리를 비운다. 빈 상태를 두지 않는 이유는 팁이 보조 정보이기 때문이다.
class DailyTipCard extends ConsumerWidget {
  const DailyTipCard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tip = ref.watch(todayTipProvider).value;
    if (tip == null) {
      return const SizedBox.shrink();
    }
    return _Card(tip: tip);
  }
}

class _Card extends StatelessWidget {
  const _Card({required this.tip});

  final DailyTipView tip;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final feedback = tip.feedback;
    return Card(
      key: const Key('today.tipCard'),
      margin: const EdgeInsets.only(top: AppSpacing.md),
      // 카드 전체가 하나의 터치 대상이다 (docs/02 A-1): "자세히"는 그 안의 보조 표시다.
      child: InkWell(
        onTap: () => context.push(AppRoutes.tip(tip.tipKey)),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      '${l10n.todayTipTitle} · ${formatMinutes(tip.estimatedMinutes, l10n)}',
                      style: theme.textTheme.labelMedium,
                    ),
                  ),
                  if (feedback != null)
                    Text(feedback.label(l10n), style: theme.textTheme.labelSmall),
                ],
              ),
              const SizedBox(height: AppSpacing.xs),
              Text(tip.title, style: theme.textTheme.titleSmall),
              const SizedBox(height: AppSpacing.xs),
              Text(tip.symptom, maxLines: 2, overflow: TextOverflow.ellipsis),
              const SizedBox(height: AppSpacing.xs),
              Align(
                alignment: Alignment.centerRight,
                child: Text(
                  l10n.todayTipMore,
                  style: theme.textTheme.labelMedium?.copyWith(color: theme.colorScheme.primary),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// TIP-6 실험 후보 한 줄. 과제가 아니라 남는 시간에 해 볼 것이라 계획 줄과 떨어뜨려 둔다.
class TipExperimentTile extends StatelessWidget {
  const TipExperimentTile({super.key, required this.experiment});

  final TipExperimentView experiment;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Card(
      key: const Key('today.tipExperiment'),
      margin: const EdgeInsets.only(top: AppSpacing.md),
      child: ListTile(
        leading: const Icon(Icons.science_outlined),
        title: Text(
          '${l10n.todayTipExperiment} · ${formatMinutes(experiment.estimatedMinutes, l10n)}',
          style: theme.textTheme.labelMedium,
        ),
        subtitle: Text(experiment.experiment),
        onTap: () => context.push(AppRoutes.tip(experiment.tipKey)),
      ),
    );
  }
}
