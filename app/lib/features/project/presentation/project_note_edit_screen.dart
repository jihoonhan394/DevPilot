import 'dart:async';

import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/theme/app_dimensions.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/core/widgets/app_toast.dart';
import 'package:devpilot_app/core/widgets/error_view.dart';
import 'package:devpilot_app/core/widgets/inline_error.dart';
import 'package:devpilot_app/core/widgets/screen_body.dart';
import 'package:devpilot_app/core/widgets/skeleton.dart';
import 'package:devpilot_app/features/project/data/project_note_models.dart';
import 'package:devpilot_app/features/project/domain/project_note_form.dart';
import 'package:devpilot_app/features/project/presentation/project_note_controller.dart';
import 'package:devpilot_app/l10n/app_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// SCR-PROJECT-NOTE-EDIT (docs/02 §3.16). 결정 기록 또는 장애 기록 하나를 쓰고 고친다.
///
/// 유형은 <b>화면에서 바꿀 수 없다</b>(PN-2) — 생성은 경로의 `noteType`이, 수정은 서버가 준 값이 정한다. 앱 바 제목이 곧 유형이다.
class ProjectNoteEditScreen extends ConsumerWidget {
  const ProjectNoteEditScreen({
    super.key,
    required this.sideProjectId,
    this.noteId,
    this.noteType,
  });

  final String sideProjectId;

  /// 수정이면 기록 id, 생성이면 null.
  final String? noteId;

  /// 생성일 때의 유형.
  final SideProjectNoteType? noteType;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final id = noteId;
    if (id == null) {
      return _NoteForm(
        sideProjectId: sideProjectId,
        values: ProjectNoteFormValues.create(
          noteType: noteType ?? SideProjectNoteType.decision,
          today: DateTime.now(),
        ),
      );
    }
    final key = (sideProjectId: sideProjectId, noteId: id);
    final note = ref.watch(projectNoteProvider(key));
    return note.when(
      loading: () => const Scaffold(body: ScreenBody(child: SkeletonList(count: 4, lines: 2))),
      error: (error, _) => Scaffold(
        body: ScreenBody(
          child: ErrorView(error: error, onRetry: () => ref.invalidate(projectNoteProvider(key))),
        ),
      ),
      data: (data) => _NoteForm(
        sideProjectId: sideProjectId,
        values: ProjectNoteFormValues.edit(data),
      ),
    );
  }
}

class _NoteForm extends ConsumerStatefulWidget {
  const _NoteForm({required this.sideProjectId, required this.values});

  final String sideProjectId;
  final ProjectNoteFormValues values;

  @override
  ConsumerState<_NoteForm> createState() => _NoteFormState();
}

class _NoteFormState extends ConsumerState<_NoteForm> {
  late ProjectNoteFormValues _values = widget.values;
  late final TextEditingController _title = TextEditingController(text: _values.title);
  late final TextEditingController _skill = TextEditingController(text: _values.skillCode);
  late final Map<NoteField, TextEditingController> _body = {
    for (final entry in _values.body.entries) entry.key: TextEditingController(text: entry.value),
  };
  var _saving = false;
  ApiException? _error;

  @override
  void dispose() {
    _title.dispose();
    _skill.dispose();
    for (final controller in _body.values) {
      controller.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final error = _error;
    return Scaffold(
      appBar: AppBar(
        title: Semantics(header: true, child: Text(_typeTitle(l10n))),
        actions: [
          TextButton(
            key: const Key('projectNote.saveButton'),
            onPressed: _values.canSave && !_saving ? () => unawaited(_save()) : null,
            child: Text(l10n.projectNoteSave),
          ),
        ],
      ),
      body: ScreenBody(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _titleField(l10n),
            const SizedBox(height: AppSpacing.md),
            _dateField(l10n),
            const SizedBox(height: AppSpacing.md),
            _skillField(l10n),
            const Divider(height: AppSpacing.xl),
            for (final field in NoteField.of(_values.noteType)) _bodyField(l10n, field),
            // 무엇을 적지 않는지 먼저 알린다 — 마스킹은 실수를 줄이는 장치이고, 이 안내는 애초에 무엇을 쓸지를 정한다
            Text(l10n.projectNoteScopeNote, style: Theme.of(context).textTheme.bodySmall),
            Text(l10n.projectNoteMaskingNote, style: Theme.of(context).textTheme.bodySmall),
            if (error?.code == ApiErrorCode.secretDetectedBlocked)
              InlineError(message: l10n.projectNoteSecretBlocked),
          ],
        ),
      ),
    );
  }

  Widget _titleField(AppLocalizations l10n) => TextField(
    key: const Key('projectNote.titleField'),
    controller: _title,
    enabled: !_saving,
    maxLength: InputRules.projectNoteTitleMaxLength,
    decoration: InputDecoration(
      labelText: l10n.projectNoteFieldTitle,
      errorText: _fieldError('title', l10n),
    ),
    onChanged: (value) => setState(() => _values = _values.copyWith(title: value)),
  );

  /// 오늘(plan-day) 이하만 연다 — 아직 일어나지 않은 일을 기록할 수는 없다.
  Widget _dateField(AppLocalizations l10n) => InputDecorator(
    decoration: InputDecoration(
      labelText: l10n.projectNoteFieldOccurredOn,
      errorText: _fieldError('occurredOn', l10n),
    ),
    child: InkWell(
      key: const Key('projectNote.dateField'),
      onTap: _saving ? null : () => unawaited(_pickDate()),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: AppSpacing.xs),
        child: Text(l10n.commonMonthDay(_values.occurredOn)),
      ),
    ),
  );

  Widget _skillField(AppLocalizations l10n) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      TextField(
        key: const Key('projectNote.skillField'),
        controller: _skill,
        enabled: !_saving,
        decoration: InputDecoration(
          labelText: l10n.projectNoteFieldSkill,
          helperText: l10n.projectNoteSkillHelp,
          errorText: _fieldError('skillCode', l10n),
        ),
        onChanged: (value) => setState(() => _values = _values.copyWith(skillCode: value)),
      ),
    ],
  );

  Widget _bodyField(AppLocalizations l10n, NoteField field) => Padding(
    padding: const EdgeInsets.only(bottom: AppSpacing.md),
    child: TextField(
      key: Key('projectNote.${field.name}'),
      controller: _body[field],
      enabled: !_saving,
      minLines: 2,
      maxLines: 5,
      maxLength: InputRules.projectNoteBodyMaxLength,
      decoration: InputDecoration(
        labelText: _label(l10n, field),
        errorText: _fieldError(field.wireName, l10n),
      ),
      onChanged: (value) => setState(() => _values = _values.copyWith(field: field, value: value)),
    ),
  );

  Future<void> _pickDate() async {
    final today = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _values.occurredOn,
      firstDate: DateTime(today.year - 5),
      lastDate: today,
    );
    if (picked != null && mounted) {
      setState(() => _values = _values.copyWith(occurredOn: picked));
    }
  }

  Future<void> _save() async {
    setState(() {
      _saving = true;
      _error = null;
    });
    final l10n = AppLocalizations.of(context);
    final original = _values.original;
    final outcome = await saveProjectNote(
      ref,
      sideProjectId: widget.sideProjectId,
      create: original == null ? _values.toCreateRequest() : null,
      patch: original == null
          ? null
          : (noteId: original.id, request: _values.toPatchRequest(original)),
      idempotencyKey: IdempotencyKey.generate(),
    );
    if (!mounted) {
      return;
    }
    switch (outcome) {
      case NoteSaved():
        showToast(context, l10n.projectNoteSaved);
        Navigator.of(context).pop(true);
      case NoteReloaded(:final latest):
        // 덮어쓰지 않는다. 최신 version으로 갈아 끼우고 입력은 그대로 둔다
        setState(() {
          _saving = false;
          _values = ProjectNoteFormValues(
            original: latest,
            noteType: _values.noteType,
            title: _values.title,
            occurredOn: _values.occurredOn,
            skillCode: _values.skillCode,
            body: _values.body,
          );
        });
        showToast(context, l10n.projectNoteReloaded);
      case NoteSaveFailed(:final error):
        setState(() {
          _saving = false;
          _error = error;
        });
    }
  }

  String _typeTitle(AppLocalizations l10n) => _values.noteType == SideProjectNoteType.incident
      ? l10n.projectNoteTitleIncident
      : l10n.projectNoteTitleDecision;

  String? _fieldError(String field, AppLocalizations l10n) =>
      _error?.fieldMessage(field, fallback: l10n.errorValidationFailed);

  static String _label(AppLocalizations l10n, NoteField field) => switch (field) {
    NoteField.decisionChoice => l10n.projectNoteDecisionChoice,
    NoteField.decisionOptions => l10n.projectNoteDecisionOptions,
    NoteField.decisionRationale => l10n.projectNoteDecisionRationale,
    NoteField.incidentSymptom => l10n.projectNoteIncidentSymptom,
    NoteField.incidentDetection => l10n.projectNoteIncidentDetection,
    NoteField.incidentFix => l10n.projectNoteIncidentFix,
    NoteField.incidentPrevention => l10n.projectNoteIncidentPrevention,
  };
}
