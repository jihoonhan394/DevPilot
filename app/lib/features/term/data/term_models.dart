import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:freezed_annotation/freezed_annotation.dart';

part 'term_models.freezed.dart';
part 'term_models.g.dart';

// 용어 사전 (docs/05 §20.5~§20.7). 본문은 콘텐츠라 모두가 같은 것을 본다 — 사용자별인 것은 만든 복습 카드뿐이다.

/// 용어 1건 (docs/05 §20.6).
@freezed
abstract class TermView with _$TermView {
  const factory TermView({
    required String termKey,

    /// 저장소 전체가 쓰는 표기 하나. 제목은 늘 이것이다 (docs/19 §3.10).
    required String representative,
    required String english,

    /// "이렇게도 불러요". 검색은 여기까지 훑지만 보여 주는 표기는 바꾸지 않는다.
    @Default(<String>[]) List<String> aliases,
    required String definition,
    required String example,
    @Default(<TermRefView>[]) List<TermRefView> confusableWith,
    @Default(<SkillRef>[]) List<SkillRef> skills,
    @JsonKey(unknownEnumValue: TipLevel.unknown) required TipLevel level,
    required String sourceUrl,

    /// 은퇴한 용어. 검색에는 없지만 이 화면으로는 열린다 (docs/19 §8.2).
    @Default(false) bool retired,

    /// 이 사용자가 이 용어로 만든 카드. 없으면 빈 목록이다.
    @Default(<CreatedCardView>[]) List<CreatedCardView> cards,
  }) = _TermView;

  factory TermView.fromJson(Map<String, Object?> json) => _$TermViewFromJson(json);
}

/// 헷갈리는 짝 한 줄 (docs/05 §20.1). 뜻은 주지 않는다 — 눌러서 넘어가라는 표시다.
@freezed
abstract class TermRefView with _$TermRefView {
  const factory TermRefView({
    required String termKey,
    required String representative,
    required String english,
  }) = _TermRefView;

  factory TermRefView.fromJson(Map<String, Object?> json) => _$TermRefViewFromJson(json);
}

/// 검색 결과 한 줄 (docs/05 §20.5).
@freezed
abstract class TermSummaryView with _$TermSummaryView {
  const factory TermSummaryView({
    required String termKey,
    required String representative,
    required String english,
    required String definition,
    @JsonKey(unknownEnumValue: TipLevel.unknown) required TipLevel level,
    @Default(false) bool cardCreated,
  }) = _TermSummaryView;

  factory TermSummaryView.fromJson(Map<String, Object?> json) => _$TermSummaryViewFromJson(json);
}

/// 만든 복습 카드 한 장 (docs/05 §20.1).
@freezed
abstract class CreatedCardView with _$CreatedCardView {
  const factory CreatedCardView({
    required String reviewItemId,
    required String conceptKey,
    @JsonKey(unknownEnumValue: ReviewType.unknown) required ReviewType reviewType,

    /// `due_at`의 plan-day. 시각이 아니라 날짜다.
    required String dueDate,
  }) = _CreatedCardView;

  factory CreatedCardView.fromJson(Map<String, Object?> json) => _$CreatedCardViewFromJson(json);
}

/// `POST /terms/{termKey}/card` 응답 (docs/05 §20.7).
@freezed
abstract class TermCardResponse with _$TermCardResponse {
  const factory TermCardResponse({
    required String termKey,
    @Default(<CreatedCardView>[]) List<CreatedCardView> cards,

    /// 이번 요청으로 새로 만든 카드 수. 0이면 이미 있던 것이다.
    required int createdCount,
  }) = _TermCardResponse;

  factory TermCardResponse.fromJson(Map<String, Object?> json) => _$TermCardResponseFromJson(json);
}
