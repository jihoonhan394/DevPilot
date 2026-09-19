import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/features/training/data/attempt_models.dart';

/// Why a submission cannot be sent yet (docs/02 §3.2 제출 row).
enum SubmissionProblem {
  /// Neither an answer nor code: `training.submit.validation.empty`.
  empty,

  /// Answer over 5000 characters.
  answerTooLong,

  /// Code over 20,000 UTF-8 bytes.
  codeTooLarge,

  /// Code without a language: `training.submit.validation.language`.
  languageMissing,

  /// A private key block in the answer or the code (sent anyway it would be a 422).
  privateKey,
}

/// Client checks of `SubmissionRequest` (docs/05 §10.9 order: empty → language → size).
abstract final class SubmissionRules {
  static SubmissionProblem? problemOf({
    required String answerText,
    required String code,
    required CodeLanguage? language,
  }) {
    final hasAnswer = answerText.trim().isNotEmpty;
    final hasCode = code.trim().isNotEmpty;
    if (!hasAnswer && !hasCode) {
      return SubmissionProblem.empty;
    }
    if (answerText.length > InputRules.submissionAnswerMaxLength) {
      return SubmissionProblem.answerTooLong;
    }
    if (hasCode && language == null) {
      return SubmissionProblem.languageMissing;
    }
    if (InputRules.utf8Length(code) > InputRules.submissionCodeMaxBytes) {
      return SubmissionProblem.codeTooLarge;
    }
    if (InputRules.containsPrivateKey(answerText) || InputRules.containsPrivateKey(code)) {
      return SubmissionProblem.privateKey;
    }
    return null;
  }

  /// The request body: blank fields are left out, code carries its language.
  static SubmissionRequest requestOf({
    required String answerText,
    required String code,
    required CodeLanguage? language,
  }) {
    final hasCode = code.trim().isNotEmpty;
    return SubmissionRequest(
      answerText: answerText.trim().isEmpty ? null : answerText,
      code: hasCode ? code : null,
      language: hasCode ? language : null,
    );
  }
}
