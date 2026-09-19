import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_enums.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_models.dart';
import 'package:devpilot_app/features/rubber_duck/data/rubber_duck_repository.dart';

import 'learning_fixtures.dart';

// In-memory rubber duck endpoints with the docs/05 §9.5~§9.10 rules the screen depends on: one
// IN_PROGRESS session, the turn limit, the server's "모르겠다" rule (RD-3) and the summary.

ApiException? _next(List<ApiException> failures) => failures.isEmpty ? null : failures.removeAt(0);

ApiException _problem(String code, int status) => ApiException(code: code, status: status);

const duckSessionId = 'd4000000-0000-4000-8000-000000000001';

const springTransactionRef = SkillRef(
  id: 'b1000000-0000-4000-8000-000000000001',
  code: 'SPRING.TRANSACTION',
  name: 'Spring Transaction',
  category: SkillCategory.spring,
);

RubberDuckSessionView testDuckSession({
  String id = duckSessionId,
  RubberDuckTargetType targetType = RubberDuckTargetType.concept,
  String? targetId,
  RubberDuckStatus status = RubberDuckStatus.inProgress,
  List<RubberDuckTurnView> turns = const [],
  bool suggestHint = false,
  SkillRef? skill = springTransactionRef,
  RubberDuckSummaryView? summary,
  AsyncFailureCode? summarySkippedReason,
}) => RubberDuckSessionView(
  id: id,
  targetType: targetType,
  targetId: targetId,
  conceptKey: targetType == RubberDuckTargetType.concept ? 'SPRING.TRANSACTION' : null,
  targetTitle: 'Spring Transaction',
  skill: skill,
  status: status,
  turnCount: turns.length,
  maxTurns: 5,
  suggestHint: suggestHint,
  turns: turns,
  summary: summary,
  summarySkippedReason: summarySkippedReason,
  startedAt: testNow,
  version: turns.length,
);

RubberDuckTurnView testDuckTurn(int turnNo, {String text = '설명', bool stuck = false}) =>
    RubberDuckTurnView(
      turnNo: turnNo,
      userText: text,
      question: '$turnNo번째 질문은 무엇인가요?',
      learnerStuck: stuck,
      createdAt: testNow,
    );

const testGap = RubberDuckGapView(
  conceptKey: 'SPRING.TRANSACTION.READ_ONLY',
  whatWasMissed: '읽기 전용 조회에도 트랜잭션 경계가 필요한 이유를 설명하지 못했다.',
  whyItMatters: '경계를 모르면 지연 로딩과 커넥션 반환 시점을 예측할 수 없다.',
  reviewQuestion: '조회만 하는 메서드에 트랜잭션을 여는 이유를 설명해 보세요.',
  reviewItemId: 'f2000000-0000-4000-8000-000000000001',
);

final class FakeRubberDuckRepository implements RubberDuckRepository {
  final sessions = <String, RubberDuckSessionView>{};
  final starts = <({RubberDuckStartRequest request, IdempotencyKey key})>[];
  final turns = <({String sessionId, String explanation, IdempotencyKey key})>[];
  final completes = <({String sessionId, IdempotencyKey key})>[];
  final abandons = <String>[];
  final startFailures = <ApiException>[];
  final turnFailures = <ApiException>[];
  final completeFailures = <ApiException>[];

  /// The summary answer; defaults to one gap turned into a new card.
  RubberDuckCompleteResponse Function(RubberDuckSessionView session) completeResponder =
      (session) => RubberDuckCompleteResponse(
        sessionId: session.id,
        status: session.turnCount == 0 ? RubberDuckStatus.abandoned : RubberDuckStatus.completed,
        gaps: session.turnCount == 0 ? const [] : const [testGap],
        confirmed: session.turnCount == 0 ? const [] : const ['컨트롤러에 규칙이 없다는 점을 짚었다'],
        overallNote: session.turnCount == 0 ? null : '트랜잭션 경계가 다음 차례다.',
        createdReviewItemCount: session.turnCount == 0 ? 0 : 1,
        version: session.version + 1,
      );
  String? abandonedOnStart;
  var _started = 0;

  @override
  Future<RubberDuckStartResponse> start(
    RubberDuckStartRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    starts.add((request: request, key: idempotencyKey));
    final failure = _next(startFailures);
    if (failure != null) {
      throw failure;
    }
    _started++;
    final session = testDuckSession(
      id: 'd4000000-0000-4000-8000-00000000010$_started',
      targetType: request.targetType,
      targetId: request.targetId,
    );
    sessions[session.id] = session;
    return RubberDuckStartResponse(session: session, abandonedSessionId: abandonedOnStart);
  }

  @override
  Future<RubberDuckTurnResponse> submitTurn(
    String sessionId,
    RubberDuckTurnRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    turns.add((sessionId: sessionId, explanation: request.explanation, key: idempotencyKey));
    final failure = _next(turnFailures);
    if (failure != null) {
      throw failure;
    }
    final session = await fetchSession(sessionId);
    if (!session.inProgress || session.turnLimitReached) {
      throw _problem(ApiErrorCode.invalidStateTransition, 409);
    }
    final turnNo = session.turnCount + 1;
    final text = request.explanation;
    final stuck = text.replaceAll(' ', '').length < 30 && text.contains('모르겠');
    final turn = testDuckTurn(turnNo, text: text, stuck: stuck);
    final all = [...session.turns, turn];
    final suggestHint = all.length >= 2 && all.reversed.take(2).every((item) => item.learnerStuck);
    sessions[sessionId] = session.copyWith(
      turns: all,
      turnCount: turnNo,
      suggestHint: suggestHint,
      version: session.version + 1,
    );
    return RubberDuckTurnResponse(
      turnNo: turnNo,
      question: turn.question,
      suggestHint: suggestHint,
      remainingTurns: session.maxTurns - turnNo,
      version: session.version + 1,
    );
  }

  @override
  Future<RubberDuckCompleteResponse> complete(
    String sessionId, {
    required IdempotencyKey idempotencyKey,
  }) async {
    completes.add((sessionId: sessionId, key: idempotencyKey));
    final failure = _next(completeFailures);
    if (failure != null) {
      throw failure;
    }
    final session = await fetchSession(sessionId);
    if (!session.inProgress) {
      throw _problem(ApiErrorCode.invalidStateTransition, 409);
    }
    final response = completeResponder(session);
    sessions[sessionId] = session.copyWith(
      status: response.status,
      summary:
          response.summarySkippedReason == null && response.status == RubberDuckStatus.completed
          ? RubberDuckSummaryView(
              gaps: response.gaps,
              confirmed: response.confirmed,
              overallNote: response.overallNote,
            )
          : null,
      summarySkippedReason: response.summarySkippedReason,
    );
    return response;
  }

  @override
  Future<RubberDuckSessionView> abandon(String sessionId) async {
    abandons.add(sessionId);
    final session = await fetchSession(sessionId);
    return sessions[sessionId] = session.copyWith(status: RubberDuckStatus.abandoned);
  }

  @override
  Future<RubberDuckSessionView> fetchSession(String sessionId) async =>
      sessions[sessionId] ?? (throw _problem(ApiErrorCode.resourceNotFound, 404));
}
