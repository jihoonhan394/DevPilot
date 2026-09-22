import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/lesson/data/lesson_models.dart';
import 'package:devpilot_app/features/lesson/data/lesson_repository.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// SCR-LESSON-LIST (docs/05 §21.9): 개념 노트 전부와 그 사용자의 진행.
///
/// 기술 트리에는 어느 기술에 노트가 붙었는지 표시가 없고 노트가 붙은 기술은 전체의 일부다 — 앱을 열었을 때 "오늘 뭘 열지"가 한 화면에
/// 보이게 하는 것이 이 화면의 목적이다. 서버가 이어서 할 것을 맨 위로 정렬해 주므로 화면은 그 순서를 그대로 쓴다.
class LessonListScreen extends ConsumerWidget {
  const LessonListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final lessons = ref.watch(lessonListProvider);
    return Scaffold(
      appBar: AppBar(title: Semantics(header: true, child: Text(l10n.lessonListTitle))),
      body: ScreenBody(
        child: lessons.when(
          loading: () => const SkeletonList(count: 5, lines: 2),
          error: (error, _) =>
              ErrorView(error: error, onRetry: () => ref.invalidate(lessonListProvider)),
          data: (list) => list.lessons.isEmpty
              ? EmptyState(icon: Icons.menu_book_outlined, message: l10n.lessonListEmpty)
              : _LessonGroups(lessons: list.lessons),
        ),
      ),
    );
  }
}

/// 서버가 준 순서를 지키면서 상태가 바뀌는 자리에만 제목을 끼운다 — 화면은 정렬하지 않는다.
///
/// [ScreenBody]가 이미 스크롤을 맡으므로 여기서는 Column이다. 노트 수는 콘텐츠가 정하는 작은 수라 목록을 통째로 그린다
/// (docs/05 §21.9: 페이지 나누기 없음).
class _LessonGroups extends StatelessWidget {
  const _LessonGroups({required this.lessons});

  final List<LessonSummaryView> lessons;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final rows = <Widget>[];
    LessonStatus? heading;
    for (final lesson in lessons) {
      if (lesson.status != heading) {
        final first = heading == null;
        heading = lesson.status;
        rows.add(
          Padding(
            padding: EdgeInsets.only(
              top: first ? 0 : AppSpacing.lg,
              bottom: AppSpacing.sm,
            ),
            child: SectionTitle(switch (heading) {
              LessonStatus.inProgress => l10n.lessonListInProgress,
              LessonStatus.notStarted => l10n.lessonListNotStarted,
              LessonStatus.done => l10n.lessonListDone,
            }),
          ),
        );
      }
      rows.add(_LessonTile(lesson: lesson));
    }
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: rows);
  }
}

class _LessonTile extends StatelessWidget {
  const _LessonTile({required this.lesson});

  final LessonSummaryView lesson;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Card(
      margin: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: ListTile(
        key: Key('lessonList.tile.${lesson.lessonKey}'),
        title: Text(lesson.title),
        isThreeLine: true,
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(lesson.oneLine, maxLines: 2, overflow: TextOverflow.ellipsis),
            const SizedBox(height: AppSpacing.xs),
            Row(
              children: [
                Expanded(
                  child: LinearProgressIndicator(
                    value: lesson.progress,
                    // 진행은 아래 글로도 읽어 주므로 막대는 장식이다.
                    semanticsLabel: '',
                  ),
                ),
                const SizedBox(width: AppSpacing.sm),
                Text(
                  l10n.lessonListProgress(lesson.solvedUnitCount, lesson.unitCount),
                  style: theme.textTheme.labelSmall,
                ),
              ],
            ),
          ],
        ),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => context.go(AppRoutes.lesson(lesson.lessonKey)),
      ),
    );
  }
}
