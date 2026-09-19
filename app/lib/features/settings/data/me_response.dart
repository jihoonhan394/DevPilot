import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'me_response.freezed.dart';
part 'me_response.g.dart';

/// `MeResponse` of `GET/PATCH /me` (docs/05 §3.1). Dates are plan dates (`yyyy-MM-dd`).
@freezed
abstract class MeResponse with _$MeResponse {
  const factory MeResponse({
    required String id,
    required String displayName,
    @JsonKey(unknownEnumValue: UserRole.unknown) required UserRole role,
    @JsonKey(unknownEnumValue: UserStatus.unknown) required UserStatus status,
    required String timezone,
    required int dayStartHour,
    required int weekdayStudyMinutes,
    required int weekendStudyMinutes,
    required bool onboardingCompleted,
    DateTime? onboardingCompletedAt,

    /// Server plan-day of the user; the only "today" the client uses (docs/05 §1.1).
    required String today,
    required bool calendarSubscribed,
    DateTime? deletionRequestedAt,
    @JsonKey(unknownEnumValue: AiStatus.unknown) required AiStatus aiStatus,
    required AiUsageView aiUsage,
    required DateTime createdAt,
    required int version,
  }) = _MeResponse;

  factory MeResponse.fromJson(Map<String, Object?> json) => _$MeResponseFromJson(json);
}

/// `AiUsageView` (docs/05 §3.1). Money is a 2-decimal USD string.
@freezed
abstract class AiUsageView with _$AiUsageView {
  const factory AiUsageView({
    required int todayCalls,
    required int dailyCallLimit,
    required String monthCostUsd,
    required String monthlyBudgetUsd,
  }) = _AiUsageView;

  factory AiUsageView.fromJson(Map<String, Object?> json) => _$AiUsageViewFromJson(json);
}
