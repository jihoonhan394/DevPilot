import 'package:devpilot_app/core/history/recent_store.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-RECENT (docs/02 §3.19). 방금 하던 것으로 돌아가는 자리다.
///
/// 서버에는 "내가 한 일" 목록이 없다 — 러버덕 세션과 코드 읽기는 하나씩만 조회되고, 끝난 과제는
/// Today 에서 빠진다. 그래서 화면을 연 기록을 **이 브라우저에** 남겨 두고 여기서 되짚는다.
/// 기기를 바꾸면 사라지는 것이 맞고, 화면도 그렇게 말한다.
class RecentScreen extends ConsumerWidget {
  const RecentScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final entries = ref.watch(recentEntriesProvider);
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.recentTitle))),
      body: ScreenBody(
        child: entries.isEmpty
            ? EmptyState(
                key: const Key('recent.empty'),
                icon: Icons.history,
                message: l10n.recentEmptyBody,
              )
            : Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(l10n.recentLead, style: Theme.of(context).textTheme.bodySmall),
                  const SizedBox(height: AppSpacing.md),
                  for (final entry in entries) _RecentTile(entry: entry),
                ],
              ),
      ),
    );
  }
}

class _RecentTile extends StatelessWidget {
  const _RecentTile({required this.entry});

  final RecentEntry entry;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return ListTile(
      key: Key('recent.item.${entry.route}'),
      contentPadding: EdgeInsets.zero,
      leading: Icon(_iconOf(entry.kind)),
      title: Text(entry.title),
      // 목록은 최신순이다. "언제"보다 "무엇"이 중요해서 종류만 적는다.
      subtitle: Text(_labelOf(entry.kind, l10n)),
      trailing: const Icon(Icons.chevron_right),
      onTap: () => context.go(entry.route),
    );
  }
}

IconData _iconOf(RecentKind kind) => switch (kind) {
  RecentKind.rubberDuck => Icons.forum_outlined,
  RecentKind.readCode => Icons.code,
  RecentKind.challenge => Icons.fitness_center,
  RecentKind.lesson => Icons.menu_book_outlined,
  RecentKind.term => Icons.abc,
  RecentKind.tip => Icons.lightbulb_outline,
};

String _labelOf(RecentKind kind, AppLocalizations l10n) => switch (kind) {
  RecentKind.rubberDuck => l10n.recentKindRubberDuck,
  RecentKind.readCode => l10n.recentKindReadCode,
  RecentKind.challenge => l10n.recentKindChallenge,
  RecentKind.lesson => l10n.recentKindLesson,
  RecentKind.term => l10n.recentKindTerm,
  RecentKind.tip => l10n.recentKindTip,
};
