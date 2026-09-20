import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/api/common_models.dart';
import 'package:devpilot_app/features/today/data/reading_models.dart';
import 'package:devpilot_app/features/today/data/reading_repository.dart';

// `GET /readings/{readingKey}` shaped like the docs/05 §19.7 examples: `kind = CODE` (a
// repository file range) and `kind = CONCEPT` (an official document page).

const petclinicReadingKey = 'READ.PETCLINIC.CONTROLLER_SLICE.001';
const petclinicCommit = '818c4136ea971c21674525f9053de0d9c7ad8cfe';
const gitConceptReadingKey = 'DOC.GIT.BRANCHING.001';

ReadingView testReading({
  String key = petclinicReadingKey,
  String license = 'Apache-2.0',
  String? licenseNote,
  String subPath = '',
  bool retired = false,
}) => ReadingView(
  key: key,
  kind: ReadingKind.code,
  skills: const [
    SkillRef(
      id: 'b1000000-0000-4000-8000-000000000009',
      code: 'SPRING.MVC_REST',
      name: 'Spring MVC REST',
      category: SkillCategory.spring,
    ),
  ],
  estimatedMinutes: 15,
  retired: retired,
  code: CodeReadingView(
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
    question: '이 컨트롤러는 Repository를 직접 주입받고 Service 계층이 없습니다. 괜찮은 경우와 곤란한 경우를 나눠 설명해 보세요.',
    lookFor: const ['계층을 나누는 목적', '트랜잭션 경계가 어디에 생기는가'],
  ),
);

/// A `READING` task's material (docs/19 §3.13): an official document page, no code.
ReadingView testConceptReading({String key = gitConceptReadingKey, bool retired = false}) =>
    ReadingView(
      key: key,
      kind: ReadingKind.concept,
      skills: const [
        SkillRef(
          id: 'b1000000-0000-4000-8000-00000000000a',
          code: 'DEVOPS.GIT',
          name: 'Git 협업',
          category: SkillCategory.devops,
        ),
      ],
      estimatedMinutes: 25,
      retired: retired,
      concept: ConceptReadingView(
        title: 'Pro Git — 3.2 Git Branching, Basic Branching and Merging',
        url: 'https://git-scm.com/book/en/v2/Git-Branching-Basic-Branching-and-Merging',
        publisher: 'Git',
        versionScope: 'Pro Git 2nd Edition (버전 없음, 2026-09-21 기준 내용)',
        whyRead: '브랜치를 복사본이 아니라 커밋을 가리키는 이름으로 이해하게 된다.',
        checkPoints: const [
          'fast-forward merge와 그렇지 않은 merge가 갈리는 조건을 적어 보세요',
          '충돌 표시의 위쪽과 아래쪽이 각각 어느 브랜치의 내용인지 적어 보세요',
          '작업 도중에 급한 수정을 끼워 넣어야 할 때 어떤 순서로 브랜치를 옮길지 적어 보세요',
        ],
        verifiedAt: DateTime.utc(2026, 9, 21),
      ),
    );

final class FakeReadingRepository implements ReadingRepository {
  final readings = <String, ReadingView>{
    petclinicReadingKey: testReading(),
    gitConceptReadingKey: testConceptReading(),
  };
  final fetched = <String>[];

  @override
  Future<ReadingView> fetchReading(String readingKey) async {
    fetched.add(readingKey);
    return readings[readingKey] ??
        (throw const ApiException(code: ApiErrorCode.resourceNotFound, status: 404));
  }
}
