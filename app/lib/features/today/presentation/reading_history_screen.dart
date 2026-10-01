import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/core/widgets/status_badge.dart';
import 'package:devpilot_app/features/today/data/reading_models.dart';
import 'package:devpilot_app/features/today/data/reading_repository.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// 내가 받은 읽기 (docs/05 §19.8). 조회만 하므로 화면을 열 때마다 다시 받는다.
final readingHistoryProvider = FutureProvider.autoDispose<ReadingHistoryResponse>(
  (ref) => ref.watch(readingRepositoryProvider).fetchReadingHistory(),
);

/// SCR-READING-LIST (docs/02 §3.23, docs/05 §19.8).
///
/// 완료한 reading은 다음 제안에서 빠진다(docs/06 §5.3). 목록이 없으면 읽었던 코드로 돌아갈 길이 없어, 커밋까지 고정해
/// 둔 자료가 한 번 쓰고 사라졌다 (2026-09-29 전수조사 3번).
class ReadingHistoryScreen extends ConsumerWidget {
  const ReadingHistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.readingHistoryTitle))),
      body: ScreenBody(
        child: ref
            .watch(readingHistoryProvider)
            .when(
              loading: () => const SkeletonList(count: 4, lines: 2),
              error: (error, _) => ErrorView(
                error: error,
                onRetry: () => ref.invalidate(readingHistoryProvider),
              ),
              data: (response) => _List(readings: response.readings),
            ),
      ),
    );
  }
}

class _List extends StatelessWidget {
  const _List({required this.readings});

  final List<ReadingHistoryView> readings;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    if (readings.isEmpty) {
      return EmptyState(icon: Icons.menu_book_outlined, message: l10n.readingHistoryEmpty);
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          l10n.readingHistoryLead,
          style: theme.textTheme.bodySmall?.copyWith(color: DevPilotColors.of(context).neutral),
        ),
        const SizedBox(height: AppSpacing.md),
        for (final reading in readings) _ReadingTile(reading: reading),
      ],
    );
  }
}

class _ReadingTile extends StatelessWidget {
  const _ReadingTile({required this.reading});

  final ReadingHistoryView reading;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final source = reading.source;
    return Card(
      margin: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: ListTile(
        key: Key('readingHistory.item.${reading.readingKey}'),
        onTap: () => context.go(_destination(reading)),
        title: Text(reading.title),
        subtitle: Text(
          [l10n.readingHistoryLastSeen(reading.lastPlanDate), ?source].join(' · '),
          style: theme.textTheme.bodySmall?.copyWith(color: DevPilotColors.of(context).neutral),
        ),
        trailing: Wrap(
          spacing: AppSpacing.xs,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            if (reading.retired)
              StatusBadge(label: l10n.readingHistoryRetired, tone: AppTone.neutral),
            if (reading.completed)
              StatusBadge(
                label: l10n.readingHistoryDone,
                icon: Icons.check_circle,
                tone: AppTone.success,
              ),
            const Icon(Icons.chevron_right),
          ],
        ),
      ),
    );
  }

  /// 개념 노트는 노트 화면으로, 나머지는 읽기 화면으로 간다 (docs/05 §19.7 `kind`).
  static String _destination(ReadingHistoryView reading) => reading.kind == ReadingKind.lesson
      ? AppRoutes.lesson(reading.readingKey)
      : AppRoutes.readCode(reading.readingKey);
}
