import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/onboarding/data/onboarding_models.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/training/data/attempt_models.dart';
import 'package:devpilot_app/features/training/data/challenge_models.dart';
import 'package:devpilot_app/features/training/data/training_enums.dart';

import 'learning_fixtures.dart';

// Training data shaped like the docs/05 §10 examples.

const challengeId = 'c1000000-0000-4000-8000-000000000001';
const otherChallengeId = 'c1000000-0000-4000-8000-000000000002';
const attemptId = 'c2000000-0000-4000-8000-000000000001';

const javaExceptionSkill = SkillRef(
  id: 'b1000000-0000-4000-8000-000000000003',
  code: 'JAVA.EXCEPTION',
  name: 'Java Exception',
  category: SkillCategory.java,
);

ChallengeSummaryView testChallengeSummary({
  String id = challengeId,
  String title = '설정 로더 예외 처리 개선',
  ContentOrigin origin = ContentOrigin.seed,
  LastAttemptView? lastAttempt,
}) => ChallengeSummaryView(
  id: id,
  title: title,
  difficulty: 2,
  estimatedMinutes: 15,
  purpose: ChallengePurpose.practice,
  origin: origin,
  isTransfer: false,
  skills: const [javaExceptionSkill],
  lastAttempt: lastAttempt,
  createdAt: testNow,
);

ChallengeView testChallenge({
  String id = challengeId,
  ChallengeStatus status = ChallengeStatus.validated,
  ChallengePurpose purpose = ChallengePurpose.practice,
  String? activeAttemptId,
  AsyncJobStatus? generationStatus = AsyncJobStatus.completed,
  bool answerRevealed = false,
}) => ChallengeView(
  id: id,
  origin: ContentOrigin.seed,
  status: status,
  generationStatus: generationStatus,
  purpose: purpose,
  isTransfer: false,
  title: '설정 로더 예외 처리 개선',
  difficulty: 2,
  estimatedMinutes: 15,
  scenario: '파일에서 설정값을 읽는 유틸리티가 모든 예외를 삼키고 null을 반환한다.',
  prompt: '호출자가 실패 원인을 알 수 있도록 예외 처리를 고쳐 보세요.',
  constraints: const ['JDK만 사용', '시그니처 유지'],
  skills: const [javaExceptionSkill],
  answerRevealed: answerRevealed,
  activeAttemptId: activeAttemptId,
  createdAt: testNow,
);

AttemptView testAttempt({
  String id = attemptId,
  String challenge = challengeId,
  AttemptStatus status = AttemptStatus.started,
  ChallengePurpose purpose = ChallengePurpose.practice,
  String? selfExplanation,
  bool skipped = false,
  HintLevel maxHintLevel = HintLevel.selfExplain,
  List<DisclosedHintView> hints = const [],
  List<SubmissionView> submissions = const [],
  EvaluatedOutcome? evaluatedOutcome,
  AttemptOutcome? outcome,
  List<ScheduledReviewView> reviewScheduled = const [],
}) => AttemptView(
  id: id,
  challengeId: challenge,
  challengeTitle: '설정 로더 예외 처리 개선',
  difficulty: 2,
  purpose: purpose,
  status: status,
  selfExplanation: selfExplanation,
  selfExplanationSkipped: skipped,
  submissionCount: submissions.length,
  maxSubmissions: 5,
  maxHintLevel: maxHintLevel,
  hints: hints,
  evaluatedOutcome: evaluatedOutcome,
  outcome: outcome,
  submissions: submissions,
  reviewScheduled: reviewScheduled,
  startedAt: testNow,
  version: 0,
);

SubmissionView testSubmission({
  int no = 1,
  AsyncJobStatus status = AsyncJobStatus.pending,
  AsyncFailureCode? failureCode,
  bool retryable = false,
  EvaluationView? evaluation,
}) => SubmissionView(
  submissionNo: no,
  answerText: '원인 예외를 보존하도록 바꿨다.',
  code: 'throw new ConfigLoadException(path, e);',
  language: CodeLanguage.java,
  evaluationStatus: status,
  failureCode: failureCode,
  retryable: retryable,
  submittedAt: testNow,
  evaluation: evaluation,
);

EvaluationView testEvaluation({EvaluatedOutcome outcome = EvaluatedOutcome.partial}) =>
    EvaluationView(
      evaluatedOutcome: outcome,
      rubricCoverageBp: 6000,
      rubric: const [
        RubricResultView(
          id: 'R1',
          criterion: '원인 예외(cause)를 보존한다',
          axis: RubricAxis.implementation,
          weightBp: 4000,
          met: true,
          evidenceQuote: 'throw new ConfigLoadException(path, e)',
        ),
        RubricResultView(
          id: 'R2',
          criterion: '복구 가능한 계층에서만 처리하는 이유를 설명한다',
          axis: RubricAxis.explanation,
          weightBp: 4000,
          met: false,
        ),
        RubricResultView(
          id: 'R3',
          criterion: 'null 반환 대신 의미 있는 결과/예외를 사용한다',
          axis: RubricAxis.implementation,
          weightBp: 2000,
          met: true,
        ),
      ],
      misconceptions: const ['checked 예외는 항상 잡아야 한다고 설명함'],
      followUpQuestion: '이 예외를 호출자가 복구할 수 없다면 어떤 타입이 더 적절할까요?',
    );

/// A diagnostic suggestion; [claimedLevel] null means diagnostic mode (docs/05 §4.2).
DiagnosticSuggestionView testDiagnostic({
  String id = challengeId,
  SkillCategory category = SkillCategory.java,
  int? claimedLevel,
  String title = 'Java 예외 기본 확인',
}) => DiagnosticSuggestionView(
  category: category,
  selfAssessedLevel: claimedLevel,
  skill: javaExceptionSkill,
  challengeId: id,
  title: title,
  difficulty: 1,
  estimatedMinutes: 10,
);
