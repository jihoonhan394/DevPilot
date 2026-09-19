import 'package:freezed_annotation/freezed_annotation.dart';

part 'update_me_request.freezed.dart';
part 'update_me_request.g.dart';

/// `UpdateMeRequest` of `PATCH /me` (docs/05 §3.2). A null field is left out and means
/// "unchanged", so only the edited fields are sent.
@freezed
abstract class UpdateMeRequest with _$UpdateMeRequest {
  const factory UpdateMeRequest({
    @JsonKey(includeIfNull: false) String? displayName,
    @JsonKey(includeIfNull: false) String? timezone,
    @JsonKey(includeIfNull: false) int? dayStartHour,
    @JsonKey(includeIfNull: false) int? weekdayStudyMinutes,
    @JsonKey(includeIfNull: false) int? weekendStudyMinutes,
    required int version,
  }) = _UpdateMeRequest;

  factory UpdateMeRequest.fromJson(Map<String, Object?> json) => _$UpdateMeRequestFromJson(json);
}
