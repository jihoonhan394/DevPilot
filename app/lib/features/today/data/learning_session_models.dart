import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'learning_session_models.freezed.dart';
part 'learning_session_models.g.dart';

// Learning session records (docs/05 §9.1~9.4). Today and Review both start and finish sessions.

@freezed
abstract class SessionView with _$SessionView {
  const factory SessionView({
    required String id,
    String? learningTaskId,
    required String planDate,
    required DateTime startedAt,
    DateTime? completedAt,
    int? actualMinutes,
    String? selfReflection,
    @JsonKey(unknownEnumValue: SessionStatus.unknown) required SessionStatus status,
    required int version,
  }) = _SessionView;

  factory SessionView.fromJson(Map<String, Object?> json) => _$SessionViewFromJson(json);
}

@freezed
abstract class SessionStartResponse with _$SessionStartResponse {
  const factory SessionStartResponse({
    required SessionView session,

    /// The earlier session this request closed as ABANDONED, if any.
    String? abandonedSessionId,
  }) = _SessionStartResponse;

  factory SessionStartResponse.fromJson(Map<String, Object?> json) =>
      _$SessionStartResponseFromJson(json);
}

/// `SessionCompleteRequest`: 0~720 minutes, reflection ≤ 5000 (docs/05 §9.2).
@freezed
abstract class SessionCompleteRequest with _$SessionCompleteRequest {
  const factory SessionCompleteRequest({
    required int actualMinutes,
    @JsonKey(includeIfNull: false) String? selfReflection,
  }) = _SessionCompleteRequest;

  factory SessionCompleteRequest.fromJson(Map<String, Object?> json) =>
      _$SessionCompleteRequestFromJson(json);
}
