import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'side_project_models.freezed.dart';
part 'side_project_models.g.dart';

/// `SideProjectView` (docs/05 §19.1). [repoUrl] is stored only; the server never fetches it.
@freezed
abstract class SideProjectView with _$SideProjectView {
  const factory SideProjectView({
    required String id,
    required String name,
    String? description,
    String? repoUrl,
    String? stack,
    @JsonKey(unknownEnumValue: SideProjectStatus.unknown) required SideProjectStatus status,
    required DateTime createdAt,
    required DateTime updatedAt,
    required int version,
  }) = _SideProjectView;

  factory SideProjectView.fromJson(Map<String, Object?> json) => _$SideProjectViewFromJson(json);
}

/// `SideProjectCreateRequest` (docs/05 §19.2). Empty optional fields are stored as null.
@freezed
abstract class SideProjectCreateRequest with _$SideProjectCreateRequest {
  const factory SideProjectCreateRequest({
    required String name,
    String? description,
    String? repoUrl,
    String? stack,
  }) = _SideProjectCreateRequest;

  factory SideProjectCreateRequest.fromJson(Map<String, Object?> json) =>
      _$SideProjectCreateRequestFromJson(json);
}

/// `SideProjectPatchRequest` (docs/05 §19.5): only changed fields are sent; an empty string clears
/// an optional field. [name] cannot be cleared.
@freezed
abstract class SideProjectPatchRequest with _$SideProjectPatchRequest {
  const factory SideProjectPatchRequest({
    @JsonKey(includeIfNull: false) String? name,
    @JsonKey(includeIfNull: false) String? description,
    @JsonKey(includeIfNull: false) String? repoUrl,
    @JsonKey(includeIfNull: false) String? stack,
    @JsonKey(includeIfNull: false) SideProjectStatus? status,
    required int version,
  }) = _SideProjectPatchRequest;

  factory SideProjectPatchRequest.fromJson(Map<String, Object?> json) =>
      _$SideProjectPatchRequestFromJson(json);
}
