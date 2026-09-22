import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/time/time_zone_support.dart';
import 'package:devpilot_app/core/widgets/ai_status_widgets.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/status_badge.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/features/settings/data/me_response.dart';
import 'package:devpilot_app/features/settings/domain/ai_usage.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// `AiUsageCard` of SCR-SETTINGS (docs/02 §3.14, S3): status, the service-wide month cost against
/// the budget, and the user's calls today. Values come from `GET /me` (docs/05 §1.9.1).
class AiUsageSection extends ConsumerWidget {
  const AiUsageSection({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final me = ref.watch(meProvider).value;
    if (me == null) {
      return const SizedBox.shrink();
    }
    final l10n = AppLocalizations.of(context);
    final usage = me.aiUsage;
    return Column(
      key: const Key('settings.aiUsage'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.settingsAiTitle),
        const SizedBox(height: AppSpacing.sm),
        Row(
          children: [
            Text(l10n.settingsAiStatus),
            const SizedBox(width: AppSpacing.sm),
            _AiStatusBadge(status: me.aiStatus),
          ],
        ),
        _StatusNote(status: me.aiStatus),
        const SizedBox(height: AppSpacing.md),
        _MonthUsage(usage: usage),
        const SizedBox(height: AppSpacing.sm),
        Text(
          l10n.settingsAiToday(usage.todayCalls, usage.dailyCallLimit),
          key: const Key('settings.aiToday'),
        ),
        const SizedBox(height: AppSpacing.md),
        _Balance(usage: usage),
      ],
    );
  }
}

class _AiStatusBadge extends StatelessWidget {
  const _AiStatusBadge({required this.status});

  final AiStatus status;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final (label, tone) = switch (status) {
      AiStatus.enabled => (l10n.settingsAiStatusEnabled, AppTone.success),
      AiStatus.budgetWarning => (l10n.settingsAiStatusBudgetWarning, AppTone.warning),
      AiStatus.disabled => (l10n.settingsAiStatusDisabled, AppTone.neutral),
      AiStatus.balanceExhausted => (l10n.settingsAiStatusBalanceExhausted, AppTone.warning),
      AiStatus.unknown => (l10n.enumUnknown, AppTone.neutral),
    };
    return StatusBadge(
      key: const Key('settings.aiStatusBadge'),
      label: label,
      tone: tone,
      semanticsGroup: l10n.settingsAiStatus,
    );
  }
}

/// The sentence under the status: why the AI is off, or the budget warning.
class _StatusNote extends StatelessWidget {
  const _StatusNote({required this.status});

  final AiStatus status;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final style = Theme.of(context).textTheme.bodySmall;
    return switch (status) {
      AiStatus.disabled => Text(l10n.settingsAiDisabled, style: style),
      AiStatus.balanceExhausted => Text(l10n.aiBalanceExhaustedBanner, style: style),
      AiStatus.budgetWarning => BudgetWarningNote(status: status),
      AiStatus.enabled || AiStatus.unknown => const SizedBox.shrink(),
    };
  }
}

/// "이번 달 (서비스 전체)" with `$used / $budget`, a bar and the percentage (text next to the bar,
/// never color alone — A-3).
/// 공급자 선불 잔액 (docs/05 §1.9.1).
///
/// 위의 이번 달 사용액은 토큰 수로 **우리가 계산한 추정치**이고, 이것은 공급자가 알려 준 실제 값이다.
/// 둘을 나란히 두되 무엇이 추정인지 한 줄로 밝힌다.
class _Balance extends ConsumerWidget {
  const _Balance({required this.usage});

  final AiUsageView usage;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final balance = usage.balanceUsd;
    final checkedAt = usage.balanceCheckedAt;
    return Column(
      key: const Key('settings.aiBalance'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l10n.settingsAiBalance, style: textTheme.titleSmall),
        Text(
          balance == null
              ? l10n.settingsAiBalanceUnknown
              : l10n.settingsAiBalanceValue(balance),
          key: const Key('settings.aiBalanceValue'),
        ),
        if (balance != null && checkedAt != null)
          Text(
            l10n.settingsAiBalanceCheckedAt(
              formatInstantMonthDay(
                checkedAt,
                ref.watch(userTimeZoneProvider),
                ref.watch(timeZoneSupportProvider),
                l10n,
              ),
            ),
            key: const Key('settings.aiBalanceCheckedAt'),
            style: textTheme.bodySmall,
          ),
        const SizedBox(height: AppSpacing.xs),
        Text(l10n.settingsAiMonthEstimate, style: textTheme.bodySmall),
      ],
    );
  }
}

class _MonthUsage extends StatelessWidget {
  const _MonthUsage({required this.usage});

  final AiUsageView usage;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final month = AiMonthUsage.parse(cost: usage.monthCostUsd, budget: usage.monthlyBudgetUsd);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l10n.settingsAiMonth, style: Theme.of(context).textTheme.titleSmall),
        Text(
          l10n.settingsAiMonthValue(usage.monthCostUsd, usage.monthlyBudgetUsd),
          key: const Key('settings.aiMonthValue'),
        ),
        if (month != null)
          Row(
            children: [
              Expanded(
                child: ExcludeSemantics(
                  child: SelectionContainer.disabled(
                    child: LinearProgressIndicator(value: month.fraction),
                  ),
                ),
              ),
              const SizedBox(width: AppSpacing.sm),
              Text(
                l10n.settingsAiMonthPercent(month.percent),
                key: const Key('settings.aiMonthPercent'),
              ),
            ],
          ),
      ],
    );
  }
}
