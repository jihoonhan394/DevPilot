import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/cursor_list.dart';
import 'package:devpilot_app/features/project/data/side_project_models.dart';
import 'package:devpilot_app/features/project/data/side_project_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Status filter chip of SCR-PROJECTS; null = all (BL-CLI-32 "상태 필터").
final class ProjectsFilterController extends Notifier<SideProjectStatus?> {
  @override
  SideProjectStatus? build() => null;

  void select(SideProjectStatus? status) => state = status;
}

final projectsFilterProvider =
    NotifierProvider.autoDispose<ProjectsFilterController, SideProjectStatus?>(
      ProjectsFilterController.new,
    );

sealed class ProjectActionOutcome {
  const ProjectActionOutcome();
}

final class ProjectActionDone extends ProjectActionOutcome {
  const ProjectActionDone();
}

/// `CONCURRENT_MODIFICATION`: the list was re-read (toast `projects.reloaded`).
final class ProjectActionReloaded extends ProjectActionOutcome {
  const ProjectActionReloaded();
}

/// `RESOURCE_NOT_FOUND`: deleted elsewhere; the list was re-read.
final class ProjectActionNotFound extends ProjectActionOutcome {
  const ProjectActionNotFound();
}

final class ProjectActionFailed extends ProjectActionOutcome {
  const ProjectActionFailed(this.error);

  final Object error;
}

/// SCR-PROJECTS list: `GET /side-projects?status=&cursor=` (updatedAt DESC) plus the status and
/// delete actions of the `⋮` menu (docs/02 §3.16, docs/05 §19).
final class ProjectsController extends AsyncNotifier<CursorList<SideProjectView>> {
  @override
  Future<CursorList<SideProjectView>> build() async {
    final status = ref.watch(projectsFilterProvider);
    return CursorList.firstPage(
      await ref.read(sideProjectRepositoryProvider).fetchProjects(status: status),
    );
  }

  void reload() => ref.invalidateSelf();

  /// Next page. `INVALID_CURSOR` restarts from the first page and returns true (toast).
  Future<bool> loadMore() async {
    final list = state.value;
    final cursor = list?.nextCursor;
    if (list == null || cursor == null || list.isLoadingMore) {
      return false;
    }
    state = AsyncData(list.loadingMore());
    try {
      final page = await ref
          .read(sideProjectRepositoryProvider)
          .fetchProjects(status: ref.read(projectsFilterProvider), cursor: cursor);
      if (ref.mounted) {
        state = AsyncData(list.append(page));
      }
      return false;
    } on ApiException catch (error) {
      if (!ref.mounted) {
        return false;
      }
      if (error.code == ApiErrorCode.invalidCursor) {
        reload();
        return true;
      }
      state = AsyncData(list.failedToLoadMore(error));
      return false;
    }
  }

  /// `⋮` "잠시 멈춤" / "진행 중으로" / "완료로": `PATCH {status, version}`, then the list is re-read
  /// because the change moves the project to the top (updatedAt) and may move "Today 과제 대상".
  Future<ProjectActionOutcome> changeStatus(
    SideProjectView project,
    SideProjectStatus status,
  ) async {
    assert(status != SideProjectStatus.unknown, 'unknown is never sent');
    try {
      await ref
          .read(sideProjectRepositoryProvider)
          .updateProject(
            project.id,
            SideProjectPatchRequest(status: status, version: project.version),
          );
      reload();
      return const ProjectActionDone();
    } on ApiException catch (error) {
      return _failure(error);
    }
  }

  /// `DELETE /side-projects/{id}` → 204: removed from the list.
  Future<ProjectActionOutcome> delete(SideProjectView project) async {
    try {
      await ref.read(sideProjectRepositoryProvider).deleteProject(project.id);
      final list = state.value;
      if (list != null && ref.mounted) {
        state = AsyncData(
          list.withItems([
            for (final item in list.items)
              if (item.id != project.id) item,
          ]),
        );
      }
      return const ProjectActionDone();
    } on ApiException catch (error) {
      return _failure(error);
    }
  }

  ProjectActionOutcome _failure(ApiException error) {
    switch (error.code) {
      case ApiErrorCode.concurrentModification:
        reload();
        return const ProjectActionReloaded();
      case ApiErrorCode.resourceNotFound:
        reload();
        return const ProjectActionNotFound();
      default:
        return ProjectActionFailed(error);
    }
  }
}

final projectsControllerProvider =
    AsyncNotifierProvider.autoDispose<ProjectsController, CursorList<SideProjectView>>(
      ProjectsController.new,
    );
