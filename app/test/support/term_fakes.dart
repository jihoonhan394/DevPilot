import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/core/api/cursor_page.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/core/api/learning_enums.dart';
import 'package:devpilot_app/features/review/data/review_enums.dart';
import 'package:devpilot_app/features/term/data/term_models.dart';
import 'package:devpilot_app/features/term/data/term_repository.dart';

const testTermKey = 'TERM.JAVA.CHECKED_EXCEPTION';
const otherTermKey = 'TERM.JAVA.UNCHECKED_EXCEPTION';

TermView testTerm({
  String termKey = testTermKey,
  String representative = '체크 예외',
  List<String> aliases = const ['검사 예외'],
  List<TermRefView> confusableWith = const [
    TermRefView(
      termKey: otherTermKey,
      representative: '언체크 예외',
      english: 'unchecked exception',
    ),
  ],
  List<SkillRef> skills = const [
    SkillRef(
      id: 's1000000-0000-4000-8000-000000000001',
      code: 'JAVA.EXCEPTION',
      name: '예외 처리',
      category: SkillCategory.java,
    ),
  ],
  List<CreatedCardView> cards = const [],
  bool retired = false,
}) => TermView(
  termKey: termKey,
  representative: representative,
  english: 'checked exception',
  aliases: aliases,
  definition: '메서드가 던질 수 있다고 서명에 적어 두어야 하고, 부르는 쪽이 잡거나 다시 던지도록 컴파일러가 강제하는 종류다.',
  example: 'IOException을 그대로 올리자 호출부마다 throws가 번져 경계에서 한 번만 감싸기로 했다.',
  confusableWith: confusableWith,
  skills: skills,
  level: TipLevel.basic,
  sourceUrl:
      'https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Exception.html',
  retired: retired,
  cards: cards,
);

TermSummaryView testTermSummary({
  String termKey = testTermKey,
  String representative = '체크 예외',
  bool cardCreated = false,
}) => TermSummaryView(
  termKey: termKey,
  representative: representative,
  english: 'checked exception',
  definition: '메서드가 던질 수 있다고 서명에 적어 두어야 하고, 부르는 쪽이 잡거나 다시 던지도록 컴파일러가 강제하는 종류다.',
  level: TipLevel.basic,
  cardCreated: cardCreated,
);

CreatedCardView testForwardCard({String termKey = testTermKey}) => CreatedCardView(
  reviewItemId: 'c1000000-0000-4000-8000-000000000001',
  conceptKey: 'TERM:$termKey',
  reviewType: ReviewType.recall,
  dueDate: '2026-09-27',
);

CreatedCardView testReverseCard({String termKey = testTermKey}) => CreatedCardView(
  reviewItemId: 'c1000000-0000-4000-8000-000000000002',
  conceptKey: 'TERM:$termKey:REVERSE',
  reviewType: ReviewType.recall,
  dueDate: '2026-09-27',
);

final class FakeTermRepository implements TermRepository {
  /// key → 본문. 없으면 404다.
  final terms = <String, TermView>{testTermKey: testTerm()};

  var page = CursorPage<TermSummaryView>(items: [testTermSummary()]);

  /// 보낸 검색 조건 기록. debounce가 실제로 요청을 줄이는지 이것으로 센다.
  final listQueries = <({String? q, String? skillId})>[];

  final created = <String>[];

  /// 설정하면 카드 만들기가 그 오류로 실패한다.
  ApiException? createFailure;

  /// 이번 요청으로 새로 만든 카드 수. 0이면 이미 있던 것이다.
  int createdCount = 2;

  @override
  Future<CursorPage<TermSummaryView>> list({String? q, String? skillId, String? cursor}) async {
    listQueries.add((q: q, skillId: skillId));
    return page;
  }

  @override
  Future<TermView> fetch(String termKey) async {
    final value = terms[termKey];
    if (value == null) {
      throw const ApiException(code: ApiErrorCode.resourceNotFound, status: 404);
    }
    return value;
  }

  @override
  Future<TermCardResponse> createCard(
    String termKey, {
    required IdempotencyKey idempotencyKey,
  }) async {
    final failure = createFailure;
    if (failure != null) {
      throw failure;
    }
    created.add(termKey);
    return TermCardResponse(
      termKey: termKey,
      cards: [
        testForwardCard(termKey: termKey),
        testReverseCard(termKey: termKey),
      ],
      createdCount: createdCount,
    );
  }
}
