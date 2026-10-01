import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'tip_models.freezed.dart';
part 'tip_models.g.dart';

// 오늘의 팁 (docs/05 §20). 본문은 콘텐츠라 모두가 같은 것을 본다 — 사용자별인 것은 shownOn과 feedback뿐이다.

@freezed
abstract class DailyTipView with _$DailyTipView {
  const factory DailyTipView({
    required String tipKey,
    @JsonKey(unknownEnumValue: TipSeries.unknown) required TipSeries series,
    @JsonKey(unknownEnumValue: TipLevel.unknown) required TipLevel level,
    required String title,
    required String symptom,
    required String cause,

    /// 짧은 코드 예시. 없는 팁이 많다.
    String? example,
    required String whereToLook,

    /// 5분 안에 직접 해 볼 방법. "해 볼게요"를 고르면 다음 날 Today에 붙는다 (docs/06 §5.12 TIP-6).
    String? experiment,
    String? sourceUrl,
    @Default(<SkillRef>[]) List<SkillRef> skills,
    required int estimatedMinutes,

    /// 아직 받은 적 없는 팁을 열면 null이다 (docs/05 §20.4a).
    String? shownOn,
    @JsonKey(unknownEnumValue: TipFeedback.unknown) TipFeedback? feedback,
  }) = _DailyTipView;

  factory DailyTipView.fromJson(Map<String, Object?> json) => _$DailyTipViewFromJson(json);
}

/// 목록 한 줄 (docs/05 §20.4).
@freezed
abstract class TipSummaryView with _$TipSummaryView {
  const factory TipSummaryView({
    required String tipKey,
    @JsonKey(unknownEnumValue: TipSeries.unknown) required TipSeries series,
    @JsonKey(unknownEnumValue: TipLevel.unknown) required TipLevel level,
    required String title,
    required String symptom,
    required int estimatedMinutes,
    @JsonKey(unknownEnumValue: TipFeedback.unknown) TipFeedback? feedback,
  }) = _TipSummaryView;

  factory TipSummaryView.fromJson(Map<String, Object?> json) => _$TipSummaryViewFromJson(json);
}

/// "해 볼게요"로 표시한 팁 하나 (docs/06 §5.12 TIP-6). 과제가 아니라 남는 시간에 해 볼 것이다.
@freezed
abstract class TipExperimentView with _$TipExperimentView {
  const factory TipExperimentView({
    required String tipKey,
    required String title,
    required String experiment,
    required int estimatedMinutes,
  }) = _TipExperimentView;

  factory TipExperimentView.fromJson(Map<String, Object?> json) =>
      _$TipExperimentViewFromJson(json);
}

/// `POST /tips/{tipKey}/feedback` (docs/05 §20.3).
@freezed
abstract class TipFeedbackRequest with _$TipFeedbackRequest {
  const factory TipFeedbackRequest({required TipFeedback feedback}) = _TipFeedbackRequest;

  factory TipFeedbackRequest.fromJson(Map<String, Object?> json) =>
      _$TipFeedbackRequestFromJson(json);
}
