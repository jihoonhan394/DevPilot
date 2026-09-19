import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_item_models.dart';
import 'package:flutter/foundation.dart';

/// Values of SCR-REVIEW-ITEM-EDIT (docs/02 §3.6). Creating needs every field; editing changes
/// only the prompt and the expected answer (`PATCH` takes nothing else, docs/05 §11.6).
@immutable
final class ReviewItemForm {
  const ReviewItemForm({
    required this.conceptKey,
    this.editing,
    this.skillCode,
    this.reviewType = ReviewType.recall,
    this.prompt = '',
    this.expectedAnswer = '',
    this.rubric = const [''],
  });

  /// The form of an existing card.
  factory ReviewItemForm.edit(ReviewItemView item) => ReviewItemForm(
    conceptKey: item.conceptKey,
    editing: item,
    skillCode: item.skill.code,
    reviewType: item.reviewType,
    prompt: item.prompt,
    expectedAnswer: item.expectedAnswer,
    rubric: [for (final point in item.rubric) point.criterion],
  );

  /// `MANUAL:` + an upper-case UUID, made once per screen so a retry keeps it (docs/05 §11.5).
  final String conceptKey;

  /// The card being edited; null when creating.
  final ReviewItemView? editing;
  final String? skillCode;
  final ReviewType reviewType;
  final String prompt;
  final String expectedAnswer;
  final List<String> rubric;

  bool get isEdit => editing != null;

  bool get promptValid => InputRules.isRequiredText(prompt, InputRules.reviewItemPromptMaxLength);

  bool get expectedValid =>
      InputRules.isRequiredText(expectedAnswer, InputRules.reviewItemExpectedMaxLength);

  bool rubricPointValid(String point) =>
      InputRules.isRequiredText(point, InputRules.reviewItemRubricMaxLength);

  bool get rubricValid =>
      rubric.isNotEmpty &&
      rubric.length <= InputRules.reviewItemRubricMaxCount &&
      rubric.every(rubricPointValid);

  bool get canAddRubricPoint => rubric.length < InputRules.reviewItemRubricMaxCount;

  /// Something differs from the card (edit) or anything was typed (create).
  bool get isDirty {
    final item = editing;
    if (item != null) {
      return prompt != item.prompt || expectedAnswer != item.expectedAnswer;
    }
    return prompt.isNotEmpty ||
        expectedAnswer.isNotEmpty ||
        rubric.any((point) => point.isNotEmpty);
  }

  /// "저장" is enabled: required values are valid and something changed.
  bool get canSave {
    if (!promptValid || !expectedValid || !isDirty) {
      return false;
    }
    return isEdit || (skillCode != null && rubricValid);
  }

  ReviewItemCreateRequest toCreateRequest() => ReviewItemCreateRequest(
    skillCode: skillCode!,
    conceptKey: conceptKey,
    reviewType: reviewType,
    prompt: prompt,
    expectedAnswer: expectedAnswer,
    rubric: rubric,
  );

  /// Only the changed text fields travel with the version.
  ReviewItemPatchRequest toPatchRequest() {
    final item = editing!;
    return ReviewItemPatchRequest(
      prompt: prompt == item.prompt ? null : prompt,
      expectedAnswer: expectedAnswer == item.expectedAnswer ? null : expectedAnswer,
      version: item.version,
    );
  }

  ReviewItemForm copyWith({
    String? skillCode,
    ReviewType? reviewType,
    String? prompt,
    String? expectedAnswer,
    List<String>? rubric,
  }) => ReviewItemForm(
    conceptKey: conceptKey,
    editing: editing,
    skillCode: skillCode ?? this.skillCode,
    reviewType: reviewType ?? this.reviewType,
    prompt: prompt ?? this.prompt,
    expectedAnswer: expectedAnswer ?? this.expectedAnswer,
    rubric: rubric ?? this.rubric,
  );
}
