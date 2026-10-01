import 'dart:async';

import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/cursor_list.dart';
import 'package:devpilot_app/core/l10n/enum_labels.dart';
import 'package:devpilot_app/core/platform/file_saver.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/badges.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/project/data/project_note_models.dart';
import 'package:devpilot_app/features/project/data/project_note_repository.dart';
import 'package:devpilot_app/features/project/data/side_project_models.dart';
import 'package:devpilot_app/features/project/data/side_project_repository.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

/// 프로젝트 하나 (docs/05 §19.4).
final projectProvider = FutureProvider.family<SideProjectView, String>(
  (ref, sideProjectId) => ref.watch(sideProjectRepositoryProvider).fetchProject(sideProjectId),
);

/// SCR-PROJECT-DETAIL (docs/02 §3.16): 요약과 결정·장애 기록.
///
/// 프로젝트를 만들며 내린 결정과 겪은 장애를 그 자리에서 남기고, 나중에 설명의 재료로 쓴다.
class ProjectDetailScreen extends ConsumerStatefulWidget {
  const ProjectDetailScreen({super.key, required this.sideProjectId});

  final String sideProjectId;

  @override
  ConsumerState<ProjectDetailScreen> createState() => _ProjectDetailScreenState();
}

class _ProjectDetailScreenState extends ConsumerState<ProjectDetailScreen> {
  CursorList<SideProjectNoteView>? _notes;
  Object? _notesError;
  SideProjectNoteType? _filter;

  @override
  void initState() {
    super.initState();
    unawaited(_loadNotes());
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final project = ref.watch(projectProvider(widget.sideProjectId));
    return Scaffold(
      appBar: AppBar(
        title: Semantics(header: true, child: Text(project.value?.name ?? l10n.moreProjects)),
        actions: [
          PopupMenuButton<void>(
            key: const Key('projectDetail.menu'),
            itemBuilder: (context) => [
              PopupMenuItem(
                onTap: () => unawaited(_export()),
                child: Text(l10n.projectNotesExport),
              ),
            ],
          ),
        ],
      ),
      body: ScreenBody(
        child: project.when(
          loading: () => const SkeletonList(count: 3, lines: 3),
          error: (error, _) => ErrorView(
            error: error,
            onRetry: () => ref.invalidate(projectProvider(widget.sideProjectId)),
          ),
          data: (data) => Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _Summary(project: data),
              const Divider(height: AppSpacing.xl),
              _notesSection(l10n),
              const SizedBox(height: AppSpacing.lg),
              _addButtons(l10n),
            ],
          ),
        ),
      ),
    );
  }

  Widget _notesSection(AppLocalizations l10n) {
    final error = _notesError;
    if (error != null) {
      return ErrorView(error: error, onRetry: () => unawaited(_loadNotes()));
    }
    final notes = _notes;
    if (notes == null) {
      return const SkeletonList(count: 2, lines: 3);
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionTitle(l10n.projectNotesTitle(notes.items.length)),
        const SizedBox(height: AppSpacing.sm),
        _filters(l10n),
        const SizedBox(height: AppSpacing.sm),
        if (notes.items.isEmpty)
          Text(
            l10n.projectNotesEmpty,
            key: const Key('projectDetail.notesEmpty'),
            style: Theme.of(context).textTheme.bodySmall,
          )
        else
          for (final note in notes.items) _NoteCard(note: note, onChanged: _loadNotes),
      ],
    );
  }

  Widget _filters(AppLocalizations l10n) => Wrap(
    spacing: AppSpacing.sm,
    children: [
      for (final option in <SideProjectNoteType?>[null, ...SideProjectNoteType.known])
        ChoiceChip(
          key: Key('projectDetail.filter.${option?.name ?? 'all'}'),
          label: Text(option == null ? l10n.projectNotesFilterAll : _typeLabel(l10n, option)),
          selected: _filter == option,
          onSelected: (_) {
            setState(() => _filter = option);
            unawaited(_loadNotes());
          },
        ),
    ],
  );

  Widget _addButtons(AppLocalizations l10n) => Row(
    children: [
      Expanded(
        child: OutlinedButton(
          key: const Key('projectDetail.addDecision'),
          onPressed: () => unawaited(_openNew(SideProjectNoteType.decision)),
          child: Text(l10n.projectNotesAddDecision),
        ),
      ),
      const SizedBox(width: AppSpacing.sm),
      Expanded(
        child: OutlinedButton(
          key: const Key('projectDetail.addIncident'),
          onPressed: () => unawaited(_openNew(SideProjectNoteType.incident)),
          child: Text(l10n.projectNotesAddIncident),
        ),
      ),
    ],
  );

  Future<void> _openNew(SideProjectNoteType noteType) async {
    final saved = await context.push<bool>(
      AppRoutes.projectNoteNew(widget.sideProjectId, noteType),
    );
    if (saved ?? false) {
      await _loadNotes();
    }
  }

  Future<void> _loadNotes() async {
    setState(() {
      _notes = null;
      _notesError = null;
    });
    try {
      final page = await ref
          .read(projectNoteRepositoryProvider)
          .fetchNotes(widget.sideProjectId, noteType: _filter);
      if (mounted) {
        setState(() => _notes = CursorList.firstPage(page));
      }
    } on Object catch (error) {
      if (mounted) {
        setState(() => _notesError = error);
      }
    }
  }

  /// 기록이 0개면 빈 파일을 만들지 않는다 — 먼저 그 사실만 알린다 (docs/02 §3.16).
  Future<void> _export() async {
    final l10n = AppLocalizations.of(context);
    if ((_notes?.items.isEmpty) ?? true) {
      showToast(context, l10n.projectNotesExportEmpty);
      return;
    }
    final export = await ref.read(projectNoteRepositoryProvider).exportNotes(widget.sideProjectId);
    if (!mounted) {
      return;
    }
    saveTextFile(fileName: export.fileName, text: export.markdown);
    showToast(context, l10n.projectNotesExportDone);
  }

  static String _typeLabel(AppLocalizations l10n, SideProjectNoteType noteType) =>
      noteType == SideProjectNoteType.incident
      ? l10n.projectNoteTitleIncident
      : l10n.projectNoteTitleDecision;
}

/// 이름·상태·종류·설명. 종류는 <b>두 값 모두</b> 보인다 — 목록 카드와 달리 여기서는 무엇인지 분명해야 한다.
class _Summary extends StatelessWidget {
  const _Summary({required this.project});

  final SideProjectView project;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final description = project.description;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Wrap(
          spacing: AppSpacing.sm,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            SideProjectStatusBadge(status: project.status),
            Text(project.kind.label(l10n), style: theme.textTheme.bodySmall),
          ],
        ),
        if (project.kind == SideProjectKind.pastWork) ...[
          const SizedBox(height: AppSpacing.xs),
          Text(
            l10n.projectNotesPastWorkNote,
            key: const Key('projectDetail.pastWorkNote'),
            style: theme.textTheme.bodySmall,
          ),
        ],
        if (description != null && description.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.sm),
          Text(description),
        ],
      ],
    );
  }
}

/// 기록 한 장. 누르면 수정으로 간다.
class _NoteCard extends ConsumerWidget {
  const _NoteCard({required this.note, required this.onChanged});

  final SideProjectNoteView note;
  final Future<void> Function() onChanged;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final skill = note.skill;
    return Card(
      margin: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: InkWell(
        onTap: () => unawaited(_open(context)),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Text(
                    '[${_typeLabel(l10n)}] ${note.occurredOn}',
                    style: theme.textTheme.labelSmall,
                  ),
                  const Spacer(),
                  IconButton(
                    key: Key('projectDetail.delete.${note.id}'),
                    icon: const Icon(Icons.delete_outline, size: 18),
                    tooltip: l10n.projectNoteDelete,
                    onPressed: () => unawaited(_delete(context, ref, l10n)),
                  ),
                ],
              ),
              Text(note.title, style: theme.textTheme.titleSmall),
              const SizedBox(height: AppSpacing.xs),
              Text(_firstBody(), maxLines: 2, overflow: TextOverflow.ellipsis),
              if (skill != null) ...[
                const SizedBox(height: AppSpacing.xs),
                Text(skill.code, style: theme.textTheme.labelSmall),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _open(BuildContext context) async {
    final saved = await context.push<bool>(
      AppRoutes.projectNote(note.sideProjectId, note.id),
    );
    if (saved ?? false) {
      await onChanged();
    }
  }

  Future<void> _delete(BuildContext context, WidgetRef ref, AppLocalizations l10n) async {
    await ref.read(projectNoteRepositoryProvider).deleteNote(note.sideProjectId, note.id);
    await onChanged();
    if (context.mounted) {
      showToast(context, l10n.projectNoteDeleted);
    }
  }

  String _typeLabel(AppLocalizations l10n) => note.noteType == SideProjectNoteType.incident
      ? l10n.projectNoteTitleIncident
      : l10n.projectNoteTitleDecision;

  /// 카드에는 유형의 첫 칸만 보인다 — 무엇에 대한 기록인지 알면 충분하고, 나머지는 열어서 읽는다.
  String _firstBody() =>
      (note.noteType == SideProjectNoteType.incident
          ? note.incidentSymptom
          : note.decisionChoice) ??
      '';
}
