import 'package:freezed_annotation/freezed_annotation.dart';

part 'dev_token_response.freezed.dart';
part 'dev_token_response.g.dart';

/// `DevTokenResponse` of `POST /api/v1/dev/token` (docs/05 §1.4.5).
@freezed
abstract class DevTokenResponse with _$DevTokenResponse {
  const factory DevTokenResponse({
    required String accessToken,
    required String tokenType,
    required DateTime expiresAt,
  }) = _DevTokenResponse;

  factory DevTokenResponse.fromJson(Map<String, Object?> json) => _$DevTokenResponseFromJson(json);
}
