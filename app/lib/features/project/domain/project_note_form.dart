import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/features/project/data/project_note_models.dart';
import 'package:flutter/foundation.dart';

/// 기록 한 건의 본문 칸 (docs/02 SCR-PROJECT-NOTE-EDIT).
///
/// 유형마다 칸이 다르고 **전부 필수**다 — 결정 기록의 이유가 비면 나중에 읽을 때 답이 없고, 장애 기록의 재발 방지가 비면 같은 일이 또 난다.
enum NoteField {
  decisionChoice,
  decisionOptions,
  decisionRationale,
  incidentSymptom,
  incidentDetection,
  incidentFix,
  incidentPrevention;

  /// 요청 본문의 필드 이름. 서버 오류의 `field`와 맞춰야 인라인으로 붙는다.
  String get wireName => switch (this) {
    NoteField.decisionChoice => 'decisionChoice',
    NoteField.decisionOptions => 'decisionOptions',
    NoteField.decisionRationale => 'decisionRationale',
    NoteField.incidentSymptom => 'incidentSymptom',
    NoteField.incidentDetection => 'incidentDetection',
    NoteField.incidentFix => 'incidentFix',
    NoteField.incidentPrevention => 'incidentPrevention',
  };

  static List<NoteField> of(SideProjectNoteType noteType) => switch (noteType) {
    SideProjectNoteType.decision => const [decisionChoice, decisionOptions, decisionRationale],
    SideProjectNoteType.incident => const [
      incidentSymptom,
      incidentDetection,
      incidentFix,
      incidentPrevention,
    ],
    SideProjectNoteType.unknown => const [],
  };
}

/// SCR-PROJECT-NOTE-EDIT의 입력 상태 (docs/02 §3.16).
///
/// 유형은 여기 담고 <b>바꾸지 않는다</b>(PN-2) — 생성은 query가, 수정은 서버가 준 값이 정한다.
@immutable
final class ProjectNoteFormValues {
  const ProjectNoteFormValues({
    required this.original,
    required this.noteType,
    required this.title,
    required this.occurredOn,
    required this.skillCode,
    required this.body,
  });

  /// 새 기록. [occurredOn] 기본값은 오늘(plan-day)이다.
  factory ProjectNoteFormValues.create({
    required SideProjectNoteType noteType,
    required DateTime today,
  }) => ProjectNoteFormValues(
    original: null,
    noteType: noteType,
    title: '',
    occurredOn: today,
    skillCode: '',
    body: {for (final field in NoteField.of(noteType)) field: ''},
  );

  factory ProjectNoteFormValues.edit(SideProjectNoteView note) => ProjectNoteFormValues(
    original: note,
    noteType: note.noteType,
    title: note.title,
    occurredOn: DateTime.parse(note.occurredOn),
    skillCode: note.skill?.code ?? '',
    body: {
      for (final field in NoteField.of(note.noteType)) field: _valueOf(note, field) ?? '',
    },
  );

  final SideProjectNoteView? original;
  final SideProjectNoteType noteType;
  final String title;
  final DateTime occurredOn;

  /// 빈 문자열이면 연결 없음. 수정에서 비우면 연결을 끊는다.
  final String skillCode;
  final Map<NoteField, String> body;

  bool get isCreate => original == null;

  /// 필수 칸이 모두 찼는지. 길이 초과는 따로 본다.
  Set<NoteField> get emptyFields => {
    for (final entry in body.entries)
      if (entry.value.trim().isEmpty) entry.key,
  };

  Set<NoteField> get tooLongFields => {
    for (final entry in body.entries)
      if (entry.value.length > InputRules.projectNoteBodyMaxLength) entry.key,
  };

  bool get titleTooLong => title.length > InputRules.projectNoteTitleMaxLength;

  /// "저장"은 필수 입력이 모두 찼고 변경이 있을 때만 활성이다 (docs/02 §3.16).
  bool get canSave =>
      title.trim().isNotEmpty &&
      !titleTooLong &&
      emptyFields.isEmpty &&
      tooLongFields.isEmpty &&
      hasChanges;

  bool get hasChanges {
    final note = original;
    if (note == null) {
      return title.isNotEmpty ||
          skillCode.isNotEmpty ||
          body.values.any((value) => value.isNotEmpty);
    }
    return toPatchRequest(note).toJson().length > 1;
  }

  SideProjectNoteCreateRequest toCreateRequest() => SideProjectNoteCreateRequest(
    noteType: noteType,
    title: title.trim(),
    occurredOn: _date(occurredOn),
    skillCode: skillCode.isEmpty ? null : skillCode,
    decisionChoice: body[NoteField.decisionChoice],
    decisionOptions: body[NoteField.decisionOptions],
    decisionRationale: body[NoteField.decisionRationale],
    incidentSymptom: body[NoteField.incidentSymptom],
    incidentDetection: body[NoteField.incidentDetection],
    incidentFix: body[NoteField.incidentFix],
    incidentPrevention: body[NoteField.incidentPrevention],
  );

  /// 바뀐 항목만 보낸다. 기술을 비우면 `""`를 보내 연결을 끊는다 (docs/05 §19.11).
  SideProjectNotePatchRequest toPatchRequest(SideProjectNoteView note) =>
      SideProjectNotePatchRequest(
        title: title.trim() == note.title ? null : title.trim(),
        occurredOn: _date(occurredOn) == note.occurredOn ? null : _date(occurredOn),
        skillCode: skillCode == (note.skill?.code ?? '') ? null : skillCode,
        decisionChoice: _changedBody(note, NoteField.decisionChoice),
        decisionOptions: _changedBody(note, NoteField.decisionOptions),
        decisionRationale: _changedBody(note, NoteField.decisionRationale),
        incidentSymptom: _changedBody(note, NoteField.incidentSymptom),
        incidentDetection: _changedBody(note, NoteField.incidentDetection),
        incidentFix: _changedBody(note, NoteField.incidentFix),
        incidentPrevention: _changedBody(note, NoteField.incidentPrevention),
        version: note.version,
      );

  ProjectNoteFormValues copyWith({
    String? title,
    DateTime? occurredOn,
    String? skillCode,
    NoteField? field,
    String? value,
  }) => ProjectNoteFormValues(
    original: original,
    noteType: noteType,
    title: title ?? this.title,
    occurredOn: occurredOn ?? this.occurredOn,
    skillCode: skillCode ?? this.skillCode,
    body: field == null || value == null ? body : {...body, field: value},
  );

  String? _changedBody(SideProjectNoteView note, NoteField field) {
    final edited = body[field];
    if (edited == null) {
      return null;
    }
    return edited == (_valueOf(note, field) ?? '') ? null : edited;
  }

  static String _date(DateTime value) =>
      '${value.year.toString().padLeft(4, '0')}-'
      '${value.month.toString().padLeft(2, '0')}-'
      '${value.day.toString().padLeft(2, '0')}';

  static String? _valueOf(SideProjectNoteView note, NoteField field) => switch (field) {
    NoteField.decisionChoice => note.decisionChoice,
    NoteField.decisionOptions => note.decisionOptions,
    NoteField.decisionRationale => note.decisionRationale,
    NoteField.incidentSymptom => note.incidentSymptom,
    NoteField.incidentDetection => note.incidentDetection,
    NoteField.incidentFix => note.incidentFix,
    NoteField.incidentPrevention => note.incidentPrevention,
  };
}
