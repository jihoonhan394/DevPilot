import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/features/today/data/reading_models.dart';
import 'package:devpilot_app/features/today/data/reading_repository.dart';

// `GET /readings/{readingKey}` shaped like the docs/05 §19.7 example.

const petclinicReadingKey = 'READ.PETCLINIC.CONTROLLER_SLICE.001';
const petclinicCommit = '818c4136ea971c21674525f9053de0d9c7ad8cfe';

CuratedReadingView testReading({
  String key = petclinicReadingKey,
  String license = 'Apache-2.0',
  String? licenseNote,
  String subPath = '',
  bool retired = false,
}) => CuratedReadingView(
  key: key,
  repo: CuratedRepoView(
    key: 'petclinic',
    name: 'Spring PetClinic',
    url: 'https://github.com/spring-projects/spring-petclinic',
    subPath: subPath,
    license: license,
    licenseNote: licenseNote,
    stack: 'Spring Boot 4.1, Java 17, Spring Data JPA',
    why: 'Spring 공식 샘플. 계층 구조와 테스트 작성법의 정석.',
    cloneHint:
        'git clone https://github.com/spring-projects/spring-petclinic.git && cd spring-petclinic'
        ' && git checkout $petclinicCommit',
    pinnedCommit: petclinicCommit,
  ),
  path: 'src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java',
  startLine: 48,
  endLine: 122,
  skills: const [
    SkillRef(
      id: 'b1000000-0000-4000-8000-000000000009',
      code: 'SPRING.MVC_REST',
      name: 'Spring MVC REST',
      category: SkillCategory.spring,
    ),
  ],
  estimatedMinutes: 15,
  question: '이 컨트롤러는 Repository를 직접 주입받고 Service 계층이 없습니다. 괜찮은 경우와 곤란한 경우를 나눠 설명해 보세요.',
  lookFor: const ['계층을 나누는 목적', '트랜잭션 경계가 어디에 생기는가'],
  retired: retired,
);

final class FakeReadingRepository implements ReadingRepository {
  final readings = <String, CuratedReadingView>{petclinicReadingKey: testReading()};
  final fetched = <String>[];

  @override
  Future<CuratedReadingView> fetchReading(String readingKey) async {
    fetched.add(readingKey);
    return readings[readingKey] ??
        (throw const ApiException(code: ApiErrorCode.resourceNotFound, status: 404));
  }
}
