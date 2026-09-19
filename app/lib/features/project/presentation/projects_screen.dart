import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/cursor_list.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/empty_state.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/load_more_footer.dart';
import 'package:devpilot_app/core/widgets/refreshing_bar.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/project/data/side_project_models.dart';
import 'package:devpilot_app/features/project/domain/project_form.dart';
import 'package:devpilot_app/features/project/presentation/project_card.dart';
import 'package:devpilot_app/features/project/presentation/project_sheet_launcher.dart';
import 'package:devpilot_app/features/project/presentation/projects_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

const _filterChoices = <SideProjectStatus?>[
  null,
  SideProjectStatus.active,
  SideProjectStatus.paused,
  SideProjectStatus.done,
];

/// SCR-PROJECTS: list, add, edit, status and delete on one screen (docs/02 §3.16, AC-27).
class ProjectsScreen extends ConsumerWidget {
  const ProjectsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final projects = ref.watch(projectsControllerProvider);
    return Scaffold(
      appBar: AppBar(
        title: Semantics(header: true, child: Text(l10n.projectsTitle)),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(2),
          child: RefreshingBar(visible: projects.isRefreshing),
        ),
      ),
      body: projects.when(
        loading: () => const ScreenBody(child: _Header(child: SkeletonList(count: 2))),
        error: (error, _) => ScreenBody(
          child: ErrorView(
            error: error,
            onRetry: () => ref.read(projectsControllerProvider.notifier).reload(),
          ),
        ),
        data: (list) => _ProjectList(list: list),
      ),
    );
  }
}

/// Subtitle and status filter above the list.
class _Header extends ConsumerWidget {
  const _Header({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final filter = ref.watch(projectsFilterProvider);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(l10n.projectsSubtitle, style: Theme.of(context).textTheme.bodyMedium),
        const SizedBox(height: AppSpacing.md),
        Wrap(
          spacing: AppSpacing.sm,
          runSpacing: AppSpacing.sm,
          children: [
            for (final status in _filterChoices)
              ChoiceChip(
                key: Key('projects.filter.${status?.name ?? 'all'}'),
                label: Text(status == null ? l10n.projectsFilterAll : status.label(l10n)),
                selected: filter == status,
                onSelected: (_) => ref.read(projectsFilterProvider.notifier).select(status),
              ),
          ],
        ),
        const SizedBox(height: AppSpacing.lg),
        child,
      ],
    );
  }
}

class _ProjectList extends ConsumerWidget {
  const _ProjectList({required this.list});

  final CursorList<SideProjectView> list;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final filter = ref.watch(projectsFilterProvider);
    final items = list.items;
    if (items.isEmpty && filter == null && !list.hasMore) {
      return const _EmptyProjects();
    }
    // SP-3: the first ACTIVE project in server order is the one Today uses.
    final todayTargetId = items
        .where((project) => project.status == SideProjectStatus.active)
        .firstOrNull
        ?.id;
    final showNoActive =
        todayTargetId == null &&
        !list.hasMore &&
        (filter == null || filter == SideProjectStatus.active);
    final width = ScreenBody.contentWidthFor(MediaQuery.sizeOf(context).width);
    return ListView.builder(
      padding: const EdgeInsets.all(AppSpacing.screen),
      itemCount: items.length + 2,
      itemBuilder: (context, index) {
        final Widget child;
        if (index == 0) {
          child = _Header(
            child: showNoActive
                ? Padding(
                    padding: const EdgeInsets.only(bottom: AppSpacing.md),
                    child: Text(l10n.projectsNoActive, key: const Key('projects.noActive')),
                  )
                : const SizedBox.shrink(),
          );
        } else if (index <= items.length) {
          final project = items[index - 1];
          child = Padding(
            padding: const EdgeInsets.only(bottom: AppSpacing.md),
            child: ProjectCard(
              project: project,
              isTodayTarget: project.id == todayTargetId,
              isLastActive:
                  project.status == SideProjectStatus.active &&
                  items.where((item) => item.status == SideProjectStatus.active).length == 1,
            ),
          );
        } else {
          child = _ListFooter(list: list);
        }
        return Center(
          child: ConstrainedBox(
            constraints: BoxConstraints(maxWidth: width),
            child: child,
          ),
        );
      },
    );
  }
}

/// No project at all: offer the default "주문 시스템" (docs/02 SCR-PROJECTS Empty, SP-1).
class _EmptyProjects extends ConsumerWidget {
  const _EmptyProjects();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    return ScreenBody(
      child: _Header(
        child: EmptyState(
          icon: Icons.code,
          message: l10n.projectsEmpty,
          actionLabel: l10n.projectsEmptyStart,
          actionKey: const Key('projects.emptyStartButton'),
          onAction: () => openProjectSheet(
            context,
            ref,
            ProjectFormValues.create(
              name: l10n.onboardingProjectDefaultName,
              description: l10n.onboardingProjectDefaultDescription,
            ),
          ),
        ),
      ),
    );
  }
}

class _ListFooter extends ConsumerWidget {
  const _ListFooter({required this.list});

  final CursorList<SideProjectView> list;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        LoadMoreFooter(
          hasMore: list.hasMore,
          isLoading: list.isLoadingMore,
          error: list.loadMoreError,
          onLoadMore: () async {
            final restarted = await ref.read(projectsControllerProvider.notifier).loadMore();
            if (restarted && context.mounted) {
              showToast(context, l10n.errorInvalidCursor);
            }
          },
        ),
        const SizedBox(height: AppSpacing.sm),
        FilledButton.icon(
          key: const Key('projects.addButton'),
          onPressed: () => openProjectSheet(context, ref, ProjectFormValues.create()),
          icon: const Icon(Icons.add),
          label: Text(l10n.projectsAdd),
        ),
      ],
    );
  }
}
