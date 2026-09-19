import 'package:freezed_annotation/freezed_annotation.dart';

part 'rubber_duck_enums.g.dart';

// Rubber duck enums (docs/04 §3). Every enum ends with `unknown`; requests never send it.

@JsonEnum(alwaysCreate: true)
enum RubberDuckTargetType {
  @JsonValue('CODE_READING')
  codeReading,
  @JsonValue('CHALLENGE')
  challenge,
  @JsonValue('REVIEW_ITEM')
  reviewItem,
  @JsonValue('CONCEPT')
  concept,
  @JsonValue('PROJECT_WORK')
  projectWork,
  unknown;

  /// Wire value, also the `targetType` query parameter of `/rubber-duck/new`.
  String get wireName => _$RubberDuckTargetTypeEnumMap[this]!;

  static RubberDuckTargetType? fromWire(String? value) =>
      values.where((type) => type != unknown && type.wireName == value).firstOrNull;
}

enum RubberDuckStatus {
  @JsonValue('IN_PROGRESS')
  inProgress,
  @JsonValue('COMPLETED')
  completed,
  @JsonValue('ABANDONED')
  abandoned,
  unknown,
}
