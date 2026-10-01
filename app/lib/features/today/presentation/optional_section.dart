import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';

/// SCR-TODAY 아래쪽 **곁가지 구역** (docs/02 SCR-TODAY "천천히 봐도 되는 것").
///
/// 실력 확인과 오늘의 팁은 오늘 해야 할 일이 아니라 **봐도 되는 것**이다. 그런데 화면에서는 오늘의 핵심과 같은 카드,
/// 같은 크기로 나와 처음 쓰는 사람에게 셋이 같은 무게로 보인다. 구분선과 한 줄 제목으로 "여기부터는 급하지 않다"를
/// 먼저 말하고, 안의 내용은 흐린 색으로 낮춘다.
class OptionalSection extends StatelessWidget {
  const OptionalSection({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    if (children.isEmpty) {
      return const SizedBox.shrink();
    }
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: AppSpacing.xl),
        const Divider(),
        const SizedBox(height: AppSpacing.sm),
        Semantics(
          header: true,
          child: Text(
            l10n.todayOptionalTitle,
            key: const Key('today.optionalTitle'),
            style: theme.textTheme.titleSmall?.copyWith(color: theme.colorScheme.outline),
          ),
        ),
        Text(
          l10n.todayOptionalLead,
          key: const Key('today.optionalLead'),
          style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.outline),
        ),
        const SizedBox(height: AppSpacing.sm),
        ...children,
      ],
    );
  }
}
