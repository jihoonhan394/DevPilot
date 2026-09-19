import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/review/data/review_item_models.dart';
import 'package:devpilot_app/features/review/data/review_item_repository.dart';
import 'package:devpilot_app/features/review/data/review_models.dart';

import 'learning_fixtures.dart';

// In-memory review card management (docs/05 §11.4~§11.6).

ApiException? _next(List<ApiException> failures) => failures.isEmpty ? null : failures.removeAt(0);

const reviewItemId = 'f3000000-0000-4000-8000-000000000001';
const otherReviewItemId = 'f3000000-0000-4000-8000-000000000002';

ReviewItemView testReviewItem({
  String id = reviewItemId,
  ReviewItemStatus status = ReviewItemStatus.active,
  String prompt = '트랜잭션 전파 REQUIRES_NEW는 언제 쓰나요?',
  ReviewItemSourceType source = ReviewItemSourceType.rubberDuck,
  ReviewRating? lastResult,
}) => ReviewItemView(
  id: id,
  skill: const SkillRef(
    id: 'b1000000-0000-4000-8000-000000000001',
    code: 'SPRING.TRANSACTION',
    name: 'Spring Transaction',
    category: SkillCategory.spring,
  ),
  origin: ContentOrigin.aiGenerated,
  sourceType: source,
  conceptKey: 'SPRING.TRANSACTION.PROPAGATION',
  reviewType: ReviewType.explain,
  prompt: prompt,
  expectedAnswer: '- 바깥 트랜잭션과 따로 커밋해야 할 때',
  rubric: const [ReviewRubricItemView(id: 'R1', criterion: '독립 커밋')],
  dueAt: testNow,
  dueDate: '2026-09-20',
  intervalDays: 1,
  consecutiveSuccesses: 0,
  consecutiveFailures: 0,
  reviewCount: 2,
  lastResult: lastResult,
  status: status,
  createdAt: testNow,
  version: 3,
);

final class FakeReviewItemRepository implements ReviewItemRepository {
  List<ReviewItemView> items = [
    testReviewItem(lastResult: ReviewRating.hard),
    testReviewItem(
      id: otherReviewItemId,
      status: ReviewItemStatus.suspended,
      prompt: '읽기 전용 트랜잭션의 장점은?',
      source: ReviewItemSourceType.seedCard,
    ),
  ];
  final queries = <({String? skillId, ReviewItemStatus? status})>[];
  final creates = <({ReviewItemCreateRequest request, IdempotencyKey key})>[];
  final updates = <({String id, ReviewItemPatchRequest request})>[];
  final updateFailures = <ApiException>[];
  final createFailures = <ApiException>[];
  var answerExisting = false;

  @override
  Future<CursorPage<ReviewItemView>> fetchItems({
    String? skillId,
    ReviewItemStatus? status,
    String? cursor,
  }) async {
    queries.add((skillId: skillId, status: status));
    return CursorPage(
      items: [
        for (final item in items)
          if ((status == null || item.status == status) &&
              (skillId == null || item.skill.id == skillId))
            item,
      ],
    );
  }

  @override
  Future<({ReviewItemView item, bool created})> createItem(
    ReviewItemCreateRequest request, {
    required IdempotencyKey idempotencyKey,
  }) async {
    creates.add((request: request, key: idempotencyKey));
    final failure = _next(createFailures);
    if (failure != null) {
      throw failure;
    }
    final item = testReviewItem(id: 'f3000000-0000-4000-8000-00000000000a', prompt: request.prompt);
    items = [...items, item];
    return (item: item, created: !answerExisting);
  }

  @override
  Future<ReviewItemView> updateItem(String reviewItemId, ReviewItemPatchRequest request) async {
    updates.add((id: reviewItemId, request: request));
    final failure = _next(updateFailures);
    if (failure != null) {
      throw failure;
    }
    final current = items.firstWhere((item) => item.id == reviewItemId);
    final updated = current.copyWith(
      status: request.status ?? current.status,
      prompt: request.prompt ?? current.prompt,
      expectedAnswer: request.expectedAnswer ?? current.expectedAnswer,
      version: current.version + 1,
    );
    items = [for (final item in items) item.id == reviewItemId ? updated : item];
    return updated;
  }
}
