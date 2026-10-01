import 'package:freezed_annotation/freezed_annotation.dart';

part 'progress_reset_response.freezed.dart';
part 'progress_reset_response.g.dart';

/// `POST /me/reset` 응답 (docs/05 §3.7).
///
/// [deletedRows]는 화면에 쓰지 않는다 — "몇 개 지웠다"는 위로가 되지 않고, 숫자를 보이면 오히려 무엇이 남았는지 세게 만든다.
@freezed
abstract class ProgressResetResponse with _$ProgressResetResponse {
  const factory ProgressResetResponse({
    required DateTime resetAt,
    required int deletedRows,
    required bool projectsDeleted,
  }) = _ProgressResetResponse;

  factory ProgressResetResponse.fromJson(Map<String, Object?> json) =>
      _$ProgressResetResponseFromJson(json);
}
