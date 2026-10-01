import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/training/data/attempt_models.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';
import 'package:devpilot_app/features/training/domain/hint_ladder_rules.dart';
import 'package:devpilot_app/features/training/domain/submission_rules.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../support/learning_fixtures.dart';
import '../../support/training_fixtures.dart';

/// Hint Ladder screen rules (docs/02 §6.2, docs/06 §9.1) and submission checks (docs/02 §3.2).
void main() {
  group('HintLadderRules', () {
    test('shouldOfferOnlyTheRungAboveTheMaximum', () {
      expect(HintLadderRules.next(HintLevel.selfExplain), HintLevel.questionOnly);
      expect(HintLadderRules.next(HintLevel.direction), HintLevel.pseudocode);
      expect(HintLadderRules.next(HintLevel.fullExample), isNull);
      expect(HintLadderRules.next(HintLevel.unknown), isNull);
    });

    test('shouldConfirmFromPseudocodeUp', () {
      expect(
        [for (final level in HintLadderRules.rungs) HintLadderRules.needsConfirmation(level)],
        [false, false, false, true, true, true],
      );
    });

    test('shouldGiveUpOnlyForTheFullExampleBeforeASubmission', () {
      expect(HintLadderRules.isGiveUp(HintLevel.fullExample, 0), isTrue);
      expect(HintLadderRules.isGiveUp(HintLevel.fullExample, 1), isFalse);
      expect(HintLadderRules.isGiveUp(HintLevel.partialCode, 0), isFalse);
    });

    test('shouldDescribeTheImpactOfTheMaximum', () {
      expect(HintLadderRules.impactOf(HintLevel.selfExplain), HintImpact.independent);
      expect(HintLadderRules.impactOf(HintLevel.questionOnly), HintImpact.independent);
      expect(HintLadderRules.impactOf(HintLevel.conceptHint), HintImpact.withHints);
      expect(HintLadderRules.impactOf(HintLevel.direction), HintImpact.withHints);
      expect(HintLadderRules.impactOf(HintLevel.pseudocode), HintImpact.review);
    });

    test('shouldMarkRungsSkippedOnAnotherDevice', () {
      final attempt = testAttempt(
        maxHintLevel: HintLevel.direction,
        hints: [
          DisclosedHintView(
            level: HintLevel.questionOnly,
            content: '질문',
            contentOrigin: HintContentOrigin.seed,
            disclosedAt: testNow,
          ),
        ],
      );

      expect(HintLadderRules.stateOf(HintLevel.questionOnly, attempt), HintRungState.disclosed);
      expect(HintLadderRules.stateOf(HintLevel.conceptHint, attempt), HintRungState.skipped);
      expect(HintLadderRules.stateOf(HintLevel.pseudocode, attempt), HintRungState.next);
      expect(HintLadderRules.stateOf(HintLevel.fullExample, attempt), HintRungState.locked);
    });

    test('shouldPassADiagnosticOnlyWhenCorrectWithAtMostTheQuestionHint', () {
      AttemptView diagnostic(HintLevel max, EvaluatedOutcome outcome) => testAttempt(
        purpose: ChallengePurpose.diagnostic,
        maxHintLevel: max,
        evaluatedOutcome: outcome,
      );

      expect(
        HintLadderRules.diagnosticPassed(
          diagnostic(HintLevel.questionOnly, EvaluatedOutcome.correct),
        ),
        isTrue,
      );
      expect(
        HintLadderRules.diagnosticPassed(
          diagnostic(HintLevel.conceptHint, EvaluatedOutcome.correct),
        ),
        isFalse,
      );
      expect(
        HintLadderRules.diagnosticPassed(
          diagnostic(HintLevel.selfExplain, EvaluatedOutcome.partial),
        ),
        isFalse,
      );
    });
  });

  group('SubmissionRules', () {
    SubmissionProblem? problem(String answer, String code, [CodeLanguage? language]) =>
        SubmissionRules.problemOf(answerText: answer, code: code, language: language);

    test('shouldNeedAnAnswerOrCode', () {
      expect(problem('  ', ''), SubmissionProblem.empty);
      expect(problem('답', ''), isNull);
      expect(problem('', 'int x;', CodeLanguage.java), isNull);
    });

    test('shouldNeedALanguageForCode', () {
      expect(problem('', 'int x;'), SubmissionProblem.languageMissing);
    });

    test('shouldCheckAnswerLengthAndCodeBytes', () {
      expect(problem('가' * 5001, ''), SubmissionProblem.answerTooLong);
      // 7,000 Hangul syllables are 21,000 UTF-8 bytes (> 20,000).
      expect(problem('', '가' * 7000, CodeLanguage.java), SubmissionProblem.codeTooLarge);
      expect(problem('', 'a' * 20000, CodeLanguage.java), isNull);
    });

    test('shouldRefuseAPrivateKeyBlock', () {
      final header = ['-----BEGIN', 'RSA PRIVATE KEY-----'].join(' ');
      expect(problem('$header\nabc', ''), SubmissionProblem.privateKey);
    });

    test('shouldLeaveBlankFieldsOutOfTheRequest', () {
      final request = SubmissionRules.requestOf(
        answerText: '답',
        code: '   ',
        language: CodeLanguage.java,
      );
      expect(request.toJson(), {'answerText': '답', 'code': null, 'language': null});
    });
  });
}
