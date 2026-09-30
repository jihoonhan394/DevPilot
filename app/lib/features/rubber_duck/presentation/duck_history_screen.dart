import 'dart:async';

import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/cursor_list.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/time/time_zone_support.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_models.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_repository.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-RUBBER-DUCK-LIST (docs/02 §3.22, docs/05 §9.11).
///
/// 세션이 끝나면 대화가 화면에서 사라져 "내가 무엇을 설명하지 못했나"의 근거를 다시 볼 수 없었다. 설명이 이 도구의
/// 축이므로 그 기록으로 돌아가는 길을 둔다 (2026-09-29 전수조사 2번).
class DuckHistoryScreen extends ConsumerStatefulWidget {
  const DuckHistoryScreen({super.key});

  @override
  ConsumerState<DuckHistoryScreen> createState() => _DuckHistoryScreenState();
}

class _DuckHistoryScreenState extends ConsumerState<DuckHistoryScreen> {
  CursorList<RubberDuckSessionSummaryView>? _list;
  Object? _error;

  @override
  void initState() {
    super.initState();
    unawaited(_loadFirstPage());
  }

  Future<void> _loadFirstPage() async {
    setState(() {
      _error = null;
      _list = null;
    });
    try {
      final page = await ref.read(rubberDuckRepositoryProvider).fetchSessions();
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
    final list = _list;
    if (list == null || list.nextCursor == null) {
      return;
    }
    try {
      final page = await ref
          .read(rubberDuckRepositoryProvider)
          .fetchSessions(cursor: list.nextCursor);
      if (mounted) {
        setState(() => _list = list.append(page));
      }
    } on Object catch (error) {
      if (mounted) {
        setState(() => _error = error);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.duckHistoryTitle))),
      body: ScreenBody(child: _body(l10n)),
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
      return EmptyState(icon: Icons.forum_outlined, message: l10n.duckHistoryEmpty);
    }
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          l10n.duckHistoryLead,
          style: theme.textTheme.bodySmall?.copyWith(color: DevPilotColors.of(context).neutral),
        ),
        const SizedBox(height: AppSpacing.md),
        for (final session in list.items) _SessionTile(session: session),
        if (list.nextCursor != null) ...[
          const SizedBox(height: AppSpacing.md),
          Align(
            child: OutlinedButton(
              key: const Key('duckHistory.more'),
              onPressed: () => unawaited(_loadMore()),
              child: Text(l10n.duckHistoryMore),
            ),
          ),
        ],
      ],
    );
  }
}

class _SessionTile extends ConsumerWidget {
  const _SessionTile({required this.session});

  final RubberDuckSessionSummaryView session;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final gaps = session.gapCount;
    final title = session.targetTitle ?? session.skill?.name ?? l10n.duckHistoryTitle;
    return Card(
      margin: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: ListTile(
        key: Key('duckHistory.session.${session.id}'),
        onTap: () => context.go(AppRoutes.rubberDuckSession(session.id)),
        title: Text(title),
        subtitle: Text(
          [
            formatInstantMonthDay(
              session.startedAt,
              ref.watch(userTimeZoneProvider),
              ref.watch(timeZoneSupportProvider),
              l10n,
            ),
            l10n.duckHistoryTurns(session.turnCount),
            if (gaps != null) l10n.duckHistoryGaps(gaps),
          ].join(' · '),
          style: theme.textTheme.bodySmall?.copyWith(color: DevPilotColors.of(context).neutral),
        ),
        trailing: const Icon(Icons.chevron_right),
      ),
    );
  }
}
