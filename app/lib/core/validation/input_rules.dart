import 'dart:convert';

/// Client checks that mirror the server limits (docs/02 §3.2, docs/05 §17).
///
/// Lengths are UTF-16 code units (`String.length`), the same unit as the server `@Size`.
abstract final class InputRules {
  static const displayNameMaxLength = 100;
  static const studyMinutesMax = 720;
  static const projectNameMaxLength = 100;
  static const projectDescriptionMaxLength = 1000;
  static const projectRepoUrlMaxLength = 500;
  static const projectStackMaxLength = 300;
  static const milestoneTitleMaxLength = 200;
  static const milestoneDescriptionMaxLength = 2000;
  static const milestoneMaxCount = 24;
  static const milestoneMaxSkills = 30;
  static const replanReasonMaxLength = 1000;
  static const focusSkillsMax = 10;
  static const selfReflectionMaxLength = 5000;
  static const reviewAnswerMaxLength = 5000;
  static const selfExplanationMaxLength = 5000;
  static const submissionAnswerMaxLength = 5000;
  static const submissionCodeMaxBytes = 20000;

  /// `devpilot.rubberduck.max-explanation-chars` (docs/02 §3.2); the counter warns from 1900.
  static const rubberDuckExplanationMaxLength = 2000;
  static const rubberDuckNearLimit = 1900;

  static const reviewItemPromptMaxLength = 2000;
  static const reviewItemExpectedMaxLength = 3000;
  static const reviewItemRubricMaxCount = 6;
  static const reviewItemRubricMaxLength = 500;

  /// Same pattern as the server `SecretMasker` block rule (docs/02 §3.2).
  static final privateKeyPattern = RegExp(
    r'-----BEGIN ((RSA|EC|DSA|OPENSSH|ENCRYPTED|PGP) )?PRIVATE KEY( BLOCK)?-----',
  );

  /// 1~100 characters after trimming.
  static bool isValidDisplayName(String value) {
    final trimmed = value.trim();
    return trimmed.isNotEmpty && trimmed.length <= displayNameMaxLength;
  }

  static bool isValidStudyMinutes(int minutes) => minutes >= 0 && minutes <= studyMinutesMax;

  /// Non-blank and at most [maxLength] characters (the value itself is sent untrimmed).
  static bool isRequiredText(String value, int maxLength) =>
      value.trim().isNotEmpty && value.length <= maxLength;

  /// `http://` or `https://` with a host, at most [maxLength] characters (docs/05 §19.2).
  static bool isHttpUrl(String value) {
    final uri = Uri.tryParse(value.trim());
    return uri != null && (uri.scheme == 'http' || uri.scheme == 'https') && uri.host.isNotEmpty;
  }

  static bool containsPrivateKey(String value) => privateKeyPattern.hasMatch(value);

  /// Byte size the server checks for code (`getBytes(UTF_8).length`, docs/05 §17).
  static int utf8Length(String value) => utf8.encode(value).length;
}
