import 'dart:async';

import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/cursor_list.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/tip/data/tip_models.dart';
import 'package:devpilot_app/features/tip/data/tip_repository.dart';
import 'package:devpilot_app/features/tip/presentation/tip_providers.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-TIPS (docs/02 §3.17, docs/05 §20.4). 짧은 시간이 났을 때 하나씩 읽는 자리다.
///
/// "더 보기"는 버튼이다 — 스크롤 자동 로드를 쓰지 않는다. 짧게 읽는 목록이라 끝이 있는 편이 낫다.
class TipsListScreen extends ConsumerStatefulWidget {
  const TipsListScreen({super.key, this.series, this.level});

  final TipSeries? series;
  final TipLevel? level;

  @override
  ConsumerState<TipsListScreen> createState() => _TipsListScreenState();
}

class _TipsListScreenState extends ConsumerState<TipsListScreen> {
  CursorList<TipSummaryView>? _list;
  Object? _error;
  late TipSeries? _series = widget.series;
  late TipLevel? _level = widget.level;

  @override
  void initState() {
    super.initState();
    unawaited(_loadFirstPage());
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.tipsListTitle))),
      body: ScreenBody(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _Filters(
              series: _series,
              level: _level,
              onSeries: (value) => _changeFilter(series: value, level: _level),
              onLevel: (value) => _changeFilter(series: _series, level: value),
            ),
            const SizedBox(height: AppSpacing.md),
            _body(l10n),
          ],
        ),
      ),
    );
  }

  Widget _body(AppLocalizations l10n) {
    final error = _error;
    if (error != null) {
      return ErrorView(error: error, onRetry: () => unawaited(_loadFirstPage()));
    }
    final list = _list;
    if (list == null) {
      return const SkeletonList(count: 4, lines: 2);
    }
    if (list.items.isEmpty) {
      return _EmptyList(filtered: _series != null || _level != null, onClear: _clearFilters);
    }
    final todayKey = ref.watch(todayTipProvider).value?.tipKey;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final tip in list.items) _TipRow(tip: tip, isToday: tip.tipKey == todayKey),
        if (list.hasMore) ...[
          const SizedBox(height: AppSpacing.sm),
          OutlinedButton(
            key: const Key('tips.more'),
            onPressed: list.isLoadingMore ? null : () => unawaited(_loadMore()),
            child: Text(l10n.tipsListMore),
          ),
        ],
      ],
    );
  }

  void _changeFilter({TipSeries? series, TipLevel? level}) {
    setState(() {
      _series = series;
      _level = level;
    });
    // 필터가 바뀌면 cursor를 버리고 첫 페이지부터 다시 읽는다 (docs/02 §3.17)
    unawaited(_loadFirstPage());
  }

  void _clearFilters() => _changeFilter();

  Future<void> _loadFirstPage() async {
    setState(() {
      _list = null;
      _error = null;
    });
    try {
      final page = await ref.read(tipRepositoryProvider).list(series: _series, level: _level);
      if (mounted) {
        setState(() => _list = CursorList.firstPage(page));
      }
    } on Object catch (error) {
      if (mounted) {
        setState(() => _error = error);
      }
    }
  }

  Future<void> _loadMore() async {
    final current = _list;
    if (current == null || !current.hasMore) {
      return;
    }
    setState(() => _list = current.loadingMore());
    try {
      final page = await ref
          .read(tipRepositoryProvider)
          .list(series: _series, level: _level, cursor: current.nextCursor);
      if (mounted) {
        setState(() => _list = current.append(page));
      }
    } on Object catch (error) {
      if (mounted) {
        setState(() => _list = current.failedToLoadMore(error));
      }
    }
  }
}

class _Filters extends StatelessWidget {
  const _Filters({
    required this.series,
    required this.level,
    required this.onSeries,
    required this.onLevel,
  });

  final TipSeries? series;
  final TipLevel? level;
  final ValueChanged<TipSeries?> onSeries;
  final ValueChanged<TipLevel?> onLevel;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final all = l10n.tipsListFilterAll;
    return Wrap(
      spacing: AppSpacing.sm,
      runSpacing: AppSpacing.xs,
      children: [
        DropdownButton<TipSeries?>(
          key: const Key('tips.filter.series'),
          value: series,
          hint: Text(l10n.tipsListFilterSeries(all)),
          onChanged: onSeries,
          items: [
            DropdownMenuItem(child: Text(l10n.tipsListFilterSeries(all))),
            for (final value in TipSeries.values)
              if (value != TipSeries.unknown)
                DropdownMenuItem(
                  value: value,
                  child: Text(l10n.tipsListFilterSeries(value.label(l10n))),
                ),
          ],
        ),
        DropdownButton<TipLevel?>(
          key: const Key('tips.filter.level'),
          value: level,
          hint: Text(l10n.tipsListFilterLevel(all)),
          onChanged: onLevel,
          items: [
            DropdownMenuItem(child: Text(l10n.tipsListFilterLevel(all))),
            for (final value in TipLevel.values)
              if (value != TipLevel.unknown)
                DropdownMenuItem(
                  value: value,
                  child: Text(l10n.tipsListFilterLevel(value.label(l10n))),
                ),
          ],
        ),
      ],
    );
  }
}

class _EmptyList extends StatelessWidget {
  const _EmptyList({required this.filtered, required this.onClear});

  final bool filtered;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    if (!filtered) {
      return EmptyState(icon: Icons.lightbulb_outline, message: l10n.tipsListEmpty);
    }
    return EmptyState(
      icon: Icons.filter_alt_off_outlined,
      message: l10n.tipsListEmptyFilter,
      actionLabel: l10n.tipsListFilterClear,
      onAction: onClear,
    );
  }
}

class _TipRow extends StatelessWidget {
  const _TipRow({required this.tip, required this.isToday});

  final TipSummaryView tip;
  final bool isToday;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final feedback = tip.feedback;
    return Card(
      margin: const EdgeInsets.only(bottom: AppSpacing.sm),
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
                      '[${tip.series.label(l10n)}] ${tip.title}',
                      style: theme.textTheme.titleSmall,
                    ),
                  ),
                  Text(
                    formatMinutes(tip.estimatedMinutes, l10n),
                    style: theme.textTheme.bodySmall,
                  ),
                ],
              ),
              if (isToday) ...[
                const SizedBox(height: AppSpacing.xs),
                Text(
                  l10n.tipsListTodayBadge,
                  style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.primary),
                ),
              ],
              const SizedBox(height: AppSpacing.xs),
              Text(tip.symptom, maxLines: 2, overflow: TextOverflow.ellipsis),
              if (feedback != null) ...[
                const SizedBox(height: AppSpacing.xs),
                Text(feedback.label(l10n), style: theme.textTheme.labelSmall),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
