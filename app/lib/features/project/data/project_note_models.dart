import 'package:devpilot_app/core/api/common_models.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'project_note_models.freezed.dart';
part 'project_note_models.g.dart';

/// 기록의 두 종류 (docs/04 §3, docs/06 §9.5 PN-1).
///
/// 결정 기록은 **무엇을 왜 골랐는지**, 장애 기록은 **무엇이 어떻게 깨졌고 어떻게 고쳤는지**다. 만든 뒤에는 바꿀 수 없다(PN-2) — 세 항목과 네
/// 항목은 옮겨 담을 수 있는 것이 아니다.
enum SideProjectNoteType {
  @JsonValue('DECISION')
  decision,
  @JsonValue('INCIDENT')
  incident,
  unknown;

  /// 화면이 고를 수 있는 값, [unknown] 제외.
  static const known = [decision, incident];
}

/// `SideProjectNoteView` (docs/05 §19.8). 모든 텍스트는 서버가 저장한 마스킹본이다.
@freezed
abstract class SideProjectNoteView with _$SideProjectNoteView {
  const factory SideProjectNoteView({
    required String id,
    required String sideProjectId,
    @JsonKey(unknownEnumValue: SideProjectNoteType.unknown) required SideProjectNoteType noteType,
    required String title,
    required String occurredOn,

    /// 연결한 기술. 없으면 null이다.
    SkillRef? skill,
    String? decisionChoice,
    String? decisionOptions,
    String? decisionRationale,
    String? incidentSymptom,
    String? incidentDetection,
    String? incidentFix,
    String? incidentPrevention,
    required DateTime createdAt,
    required DateTime updatedAt,
    required int version,
  }) = _SideProjectNoteView;

  factory SideProjectNoteView.fromJson(Map<String, Object?> json) =>
      _$SideProjectNoteViewFromJson(json);
}

/// `SideProjectNoteCreateRequest` (docs/05 §19.9). 유형에 맞지 않는 항목은 보내지 않는다.
@freezed
abstract class SideProjectNoteCreateRequest with _$SideProjectNoteCreateRequest {
  const factory SideProjectNoteCreateRequest({
    required SideProjectNoteType noteType,
    required String title,
    required String occurredOn,
    @JsonKey(includeIfNull: false) String? skillCode,
    @JsonKey(includeIfNull: false) String? decisionChoice,
    @JsonKey(includeIfNull: false) String? decisionOptions,
    @JsonKey(includeIfNull: false) String? decisionRationale,
    @JsonKey(includeIfNull: false) String? incidentSymptom,
    @JsonKey(includeIfNull: false) String? incidentDetection,
    @JsonKey(includeIfNull: false) String? incidentFix,
    @JsonKey(includeIfNull: false) String? incidentPrevention,
  }) = _SideProjectNoteCreateRequest;

  factory SideProjectNoteCreateRequest.fromJson(Map<String, Object?> json) =>
      _$SideProjectNoteCreateRequestFromJson(json);
}

/// `SideProjectNotePatchRequest` (docs/05 §19.11). 바뀐 항목만 보낸다.
///
/// `noteType`이 없다 — 유형을 바꾸려면 지우고 다시 만든다(PN-2). 넣으면 서버가 400을 준다.
@freezed
abstract class SideProjectNotePatchRequest with _$SideProjectNotePatchRequest {
  const factory SideProjectNotePatchRequest({
    @JsonKey(includeIfNull: false) String? title,
    @JsonKey(includeIfNull: false) String? occurredOn,

    /// 빈 문자열이면 기술 연결을 끊는다.
    @JsonKey(includeIfNull: false) String? skillCode,
    @JsonKey(includeIfNull: false) String? decisionChoice,
    @JsonKey(includeIfNull: false) String? decisionOptions,
    @JsonKey(includeIfNull: false) String? decisionRationale,
    @JsonKey(includeIfNull: false) String? incidentSymptom,
    @JsonKey(includeIfNull: false) String? incidentDetection,
    @JsonKey(includeIfNull: false) String? incidentFix,
    @JsonKey(includeIfNull: false) String? incidentPrevention,
    required int version,
  }) = _SideProjectNotePatchRequest;

  factory SideProjectNotePatchRequest.fromJson(Map<String, Object?> json) =>
      _$SideProjectNotePatchRequestFromJson(json);
}
