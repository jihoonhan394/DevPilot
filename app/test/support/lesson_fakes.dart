import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/idempotency_key.dart';
import 'package:devpilot_app/features/lesson/data/lesson_models.dart';
import 'package:devpilot_app/features/lesson/data/lesson_repository.dart';

const testLessonKey = 'LESSON.SPRING.MVC_REST.001';
String testUnitKey(int number) => '$testLessonKey.U$number';

/// 단위 2개짜리 노트. 형식은 진짜와 같고 본문만 짧다.
LessonView testLesson({List<LessonUnitView>? units}) => LessonView(
  lessonKey: testLessonKey,
  skillId: 'e3a1d0f2-0000-4000-8000-000000000001',
  skillCode: 'SPRING.MVC_REST',
  skillName: 'Spring MVC REST API',
  title: '요청을 메서드에 잇기',
  whyItMatters: '주소가 어느 메서드로 가는지 정하지 못하면 화면도 앱도 서버에 말을 걸 수 없다.',
  oneLine: '컨트롤러는 URL과 자바 메서드를 이어 주는 자리다.',
  units: units ?? [testUnit(1), testUnit(2)],
  commonMistakes: const ['`@Controller`만 붙이고 문자열을 돌려줘서 화면을 찾다가 실패한다.'],
  inProject: '주문 시스템의 상품 목록과 주문 넣기가 이 매핑 위에 올라간다.',
  sources: const [
    LessonSourceView(
      title: 'Spring Framework Reference — Request Mapping',
      url: 'https://docs.spring.io/spring-framework/reference/web/webmvc.html',
      versionScope: 'Spring Framework 7',
    ),
  ],
);

LessonUnitView testUnit(int number, {UnitProgressView? progress}) => LessonUnitView(
  unitKey: testUnitKey(number),
  title: '주소 하나를 메서드에 잇기 $number',
  minutes: 10,
  core: true,
  explain: '스프링은 요청이 오면 등록된 URL 매핑을 먼저 찾는다. 찾지 못하면 404다.',
  example: const LessonExampleView(
    language: 'java',
    code: '@RestController\npublic class HelloController {}',
    output: 'GET /hello → 200 hi',
  ),
  predict: const LessonQuestionView(
    question: '타입이 Long인데 /orders/abc를 부르면?',
    choices: ['400', '404'],
  ),
  complete: const LessonQuestionView(question: '빈칸을 채우세요.', code: '@___("/ping")', blanks: 1),
  problem: const LessonProblemView(
    prompt: 'GET /orders/health에 ok만 돌려주는 컨트롤러를 작성하세요.',
    deliverables: ['클래스 전체 코드', '애너테이션을 붙인 이유'],
    starterCode: 'public class OrderHealthController {\n}',
    hints: ['클래스에 하나, 메서드에 하나를 붙입니다.', '돌려준 문자열이 화면 이름으로 읽히면 안 됩니다.'],
  ),
  progress: progress,
);

final class FakeLessonRepository implements LessonRepository {
  LessonView lesson = testLesson();

  /// skill → 노트. 비어 있으면 그 skill에는 노트가 없다(404).
  final lessonsBySkill = <String, LessonView>{};

  final predicted = <String>[];
  final completed = <List<String>>[];
  final revealed = <String>[];
  final finished = <({String unitKey, HelpLevel helpLevel, int? selfChecksMet})>[];

  bool predictCorrect = true;
  bool completeCorrect = true;

  @override
  Future<LessonView> fetchLesson(String lessonKey) async {
    if (lessonKey != lesson.lessonKey) {
      throw const ApiException(code: ApiErrorCode.resourceNotFound, status: 404);
    }
    return lesson;
  }

  @override
  Future<LessonView> fetchLessonForSkill(String skillId) async =>
      lessonsBySkill[skillId] ??
      (throw const ApiException(code: ApiErrorCode.resourceNotFound, status: 404));

  @override
  Future<PredictResult> checkPredict(String lessonKey, String unitKey, String answer) async {
    predicted.add(answer);
    return PredictResult(
      correct: predictCorrect,
      expected: '400',
      explanation: '타입을 바꾸지 못해 400이다.',
    );
  }

  @override
  Future<CompleteResult> checkComplete(
    String lessonKey,
    String unitKey,
    List<String> answers,
  ) async {
    completed.add(answers);
    return CompleteResult(
      correct: completeCorrect,
      results: [completeCorrect],
      expected: const ['GetMapping'],
      explanation: 'GET 요청이므로 GetMapping이다.',
    );
  }

  @override
  Future<AnswerResult> fetchAnswer(String lessonKey, String unitKey) async {
    revealed.add(unitKey);
    return const AnswerResult(
      modelAnswer: '```java\n@RestController\n```',
      selfChecks: ['RestController를 붙였는가', '주소를 정확히 적었는가'],
    );
  }

  @override
  Future<FinishResult> finishUnit(
    String lessonKey,
    String unitKey, {
    required HelpLevel helpLevel,
    int? selfChecksMet,
    required IdempotencyKey idempotencyKey,
  }) async {
    finished.add((unitKey: unitKey, helpLevel: helpLevel, selfChecksMet: selfChecksMet));
    return FinishResult(
      unitKey: unitKey,
      helpLevel: helpLevel,
      selfChecksMet: selfChecksMet,
      recordedAt: DateTime.utc(2026, 9, 21),
    );
  }
}
