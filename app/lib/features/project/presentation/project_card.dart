import 'package:devpilot_app/app/explain_with_duck_button.dart';
import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/l10n/display_format.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/theme/devpilot_colors.dart';
import 'package:devpilot_app/core/time/time_zone_support.dart';
import 'package:devpilot_app/core/widgets/action_error.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/badges.dart';
import 'package:devpilot_app/core/widgets/confirm_dialog.dart';
import 'package:devpilot_app/core/widgets/status_badge.dart';
import 'package:devpilot_app/features/project/data/side_project_models.dart';
import 'package:devpilot_app/features/project/domain/project_form.dart';
import 'package:devpilot_app/features/project/presentation/project_sheet_launcher.dart';
import 'package:devpilot_app/features/project/presentation/projects_controller.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/settings/data/me_provider.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

enum _MenuAction { edit, pause, activate, done, delete }

/// `ProjectCard` of SCR-PROJECTS (docs/02 §3.16), with "이 프로젝트 작업 설명하기" (rubber duck
/// `PROJECT_WORK`).
class ProjectCard extends ConsumerWidget {
  const ProjectCard({
    super.key,
    required this.project,
    required this.isTodayTarget,
    required this.isLastActive,
  });

  final SideProjectView project;

  /// First ACTIVE project of the list: the one Today's project tasks use (SP-3).
  final bool isTodayTarget;

  /// Deleting it leaves no ACTIVE project (adds `projects.delete.lastActive`).
  final bool isLastActive;

  Future<void> _onMenu(BuildContext context, WidgetRef ref, _MenuAction action) async {
    final l10n = AppLocalizations.of(context);
    final controller = ref.read(projectsControllerProvider.notifier);
    final ProjectActionOutcome outcome;
    switch (action) {
      case _MenuAction.edit:
        await openProjectSheet(context, ref, ProjectFormValues.edit(project));
        return;
      case _MenuAction.pause:
        outcome = await controller.changeStatus(project, SideProjectStatus.paused);
      case _MenuAction.activate:
        outcome = await controller.changeStatus(project, SideProjectStatus.active);
      case _MenuAction.done:
        outcome = await controller.changeStatus(project, SideProjectStatus.done);
      case _MenuAction.delete:
        final confirmed = await showConfirmDialog(
          context,
          title: l10n.projectsDeleteTitle,
          body: isLastActive
              ? l10n.projectsDeleteBodyLastActive(
                  l10n.projectsDeleteBody,
                  l10n.projectsDeleteLastActive,
                )
              : l10n.projectsDeleteBody,
          cancelLabel: l10n.commonCancel,
          confirmLabel: l10n.projectsDeleteConfirm,
          destructive: true,
          confirmKey: const Key('projects.deleteConfirmButton'),
        );
        if (!confirmed) {
          return;
        }
        outcome = await controller.delete(project);
    }
    if (!context.mounted) {
      return;
    }
    switch (outcome) {
      case ProjectActionDone():
        showToast(
          context,
          switch (action) {
            _MenuAction.delete => l10n.projectsDeleted,
            _MenuAction.pause || _MenuAction.done => l10n.projectsStatusChanged,
            _ => l10n.projectsSaved,
          },
        );
      case ProjectActionReloaded():
        showToast(context, l10n.projectsReloaded);
      case ProjectActionNotFound():
        showToast(context, l10n.errorResourceNotFound);
      case ProjectActionFailed(:final error):
        await presentActionError(context, ref, error);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final textTheme = Theme.of(context).textTheme;
    final description = project.description;
    final stack = project.stack;
    final repoUrl = project.repoUrl;
    final updated = formatInstantMonthDay(
      project.updatedAt,
      ref.watch(userTimeZoneProvider),
      ref.watch(timeZoneSupportProvider),
      l10n,
    );
    return Card(
      key: Key('projects.card.${project.id}'),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.lg,
          AppSpacing.sm,
          AppSpacing.xs,
          AppSpacing.md,
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const SizedBox(height: AppSpacing.sm),
                  _ProjectTitle(project: project, isTodayTarget: isTodayTarget),
                  if (description != null && description.isNotEmpty) ...[
                    const SizedBox(height: AppSpacing.xs),
                    Text(description, maxLines: 2, overflow: TextOverflow.ellipsis),
                  ],
                  if (stack != null && stack.isNotEmpty) Text(stack, style: textTheme.bodySmall),
                  if (repoUrl != null && repoUrl.isNotEmpty) _RepoLink(url: repoUrl),
                  const SizedBox(height: AppSpacing.xs),
                  Text(l10n.projectsUpdatedAt(updated), style: textTheme.bodySmall),
                  _ExplainProjectButton(project: project),
                ],
              ),
            ),
            _ProjectMenu(project: project, onSelected: (action) => _onMenu(context, ref, action)),
          ],
        ),
      ),
    );
  }
}

/// "이 프로젝트 작업 설명하기": the rubber duck on this project (docs/02 SCR-PROJECTS). Disabled
/// with its reason while the AI is off; this screen has no AI banner.
class _ExplainProjectButton extends StatelessWidget {
  const _ExplainProjectButton({required this.project});

  final SideProjectView project;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return Align(
      alignment: Alignment.centerLeft,
      child: IntrinsicWidth(
        child: ExplainWithDuckButton(
          buttonKey: Key('projects.explain.${project.id}'),
          label: l10n.projectsExplain,
          semanticsLabel: l10n.projectsExplainNamed(project.name),
          launch: RubberDuckLaunch(
            targetType: RubberDuckTargetType.projectWork,
            targetId: project.id,
            preview: RubberDuckTargetPreview(title: project.name, summary: project.description),
          ),
        ),
      ),
    );
  }
}

/// Name, status badge and the "Today 과제 대상" label.
class _ProjectTitle extends StatelessWidget {
  const _ProjectTitle({required this.project, required this.isTodayTarget});

  final SideProjectView project;
  final bool isTodayTarget;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: AppSpacing.sm,
      runSpacing: AppSpacing.xs,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        Text(project.name, style: Theme.of(context).textTheme.titleMedium),
        SideProjectStatusBadge(status: project.status),
        if (isTodayTarget)
          StatusBadge(
            key: const Key('projects.todayTarget'),
            label: AppLocalizations.of(context).projectsTodayTarget,
            tone: AppTone.info,
          ),
      ],
    );
  }
}

/// `⋮` menu: edit, the two other statuses, delete.
class _ProjectMenu extends StatelessWidget {
  const _ProjectMenu({required this.project, required this.onSelected});

  final SideProjectView project;
  final ValueChanged<_MenuAction> onSelected;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return PopupMenuButton<_MenuAction>(
      key: Key('projects.menu.${project.id}'),
      tooltip: l10n.projectsMenuTooltip(project.name),
      onSelected: onSelected,
      itemBuilder: (context) => [
        PopupMenuItem(value: _MenuAction.edit, child: Text(l10n.projectsMenuEdit)),
        if (project.status != SideProjectStatus.paused)
          PopupMenuItem(value: _MenuAction.pause, child: Text(l10n.projectsMenuPause)),
        if (project.status != SideProjectStatus.active)
          PopupMenuItem(value: _MenuAction.activate, child: Text(l10n.projectsMenuActivate)),
        if (project.status != SideProjectStatus.done)
          PopupMenuItem(value: _MenuAction.done, child: Text(l10n.projectsMenuDone)),
        PopupMenuItem(value: _MenuAction.delete, child: Text(l10n.projectsMenuDelete)),
      ],
    );
  }
}

/// Repository address as text; only https opens, in a new tab of the user's browser. DevPilot's
/// server never requests it (docs/05 §19, docs/07 §5.5).
class _RepoLink extends StatelessWidget {
  const _RepoLink({required this.url});

  final String url;

  @override
  Widget build(BuildContext context) {
    final text = repoLinkText(url);
    if (!isOpenableRepoUrl(url)) {
      return Text(text, style: Theme.of(context).textTheme.bodySmall);
    }
    return Semantics(
      link: true,
      child: TextButton.icon(
        style: TextButton.styleFrom(padding: EdgeInsets.zero, alignment: Alignment.centerLeft),
        onPressed: () => launchUrl(Uri.parse(url), webOnlyWindowName: '_blank'),
        icon: const Icon(Icons.open_in_new, size: 16),
        label: Text(text, overflow: TextOverflow.ellipsis),
      ),
    );
  }
}
