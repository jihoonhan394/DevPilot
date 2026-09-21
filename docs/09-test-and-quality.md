# 09. Test & Quality

> Status: Accepted (v2) · Last updated: 2026-09-20 (러버덕 · 코드 읽기 · 사이드 프로젝트 · 교차 학습 · 확장 제안 · 읽기 평가 · 재현 과제 · 학습 트랙 · 프로젝트 기록) · Related: ADR-019, ADR-040, NFR-02, NFR-07, AC-02, AC-05, AC-07, AC-08, AC-12, AC-13, AC-14, AC-15, AC-18, AC-20, AC-23, AC-24, AC-26, AC-27, AC-28, AC-29, AC-30, AC-31, AC-32, AC-33, `06-learning-engine-rules.md`, `08-coding-conventions.md`, `19-content-spec.md`
>
> 이 문서는 **테스트 계층, 도구, 명명, fixture, test vector 적용 방법, 인증·격리·AI·E2E 테스트 목록, 품질 게이트 순서와 실패 기준**을 정의한다. 보안 테스트 목록은 `07-security-and-privacy.md` §16, AI eval은 `17-ai-integration.md` §12, 완료 기준은 `16-definition-of-ready-done.md`가 기준이다.

---

## 1. 테스트 전략

| 계층 | 범위 | 도구 | 위치 | 태그 / task | PR CI |
|---|---|---|---|---|---|
| Unit — 규칙 | `03` §3.2 규칙 클래스, `PlanDayCalculator`, `FixedPointMath`, 가드, `SecretMasker`, `CursorCodec` | JUnit Jupiter, AssertJ, `@ParameterizedTest` + vector 파일 | `backend/src/test/java` (패키지 mirror) | `unit` / `test` | 예 |
| Unit — application | 서비스의 분기·오류 변환 중 DB 없이 검증 가능한 부분 | JUnit, AssertJ, Mockito(경계 port만) | 같음 | `unit` / `test` | 예 |
| Architecture | `08-coding-conventions.md` §11.6 ARCH-01~21 + 테스트 코드 규칙 | ArchUnit | `com.devpilot.architecture` | `unit` / `test` | 예 |
| Web slice | controller 검증, 상태 코드, ProblemDetail 형식, 보안 필터(JWT는 `jwt()`) | `@WebMvcTest`, `MockMvcTester`, spring-security-test | 모듈 `presentation` mirror | `unit` / `test` | 예 |
| JPA slice | repository 쿼리, projection, 배열·JSON 매핑 | `@DataJpaTest` + Testcontainers `postgres:16` | 모듈 `infrastructure` mirror | `integration` / `integrationTest` | 예 |
| Integration | 서비스 + DB 트랜잭션, 불변식, 이벤트, 비동기 task, migration, 실제 JWT decoder | `@SpringBootTest`, Testcontainers, `TestJwksServer`, `FakeAiProvider`, Awaitility | 모듈 mirror, `*IntegrationTest` | `integration` / `integrationTest` | 예 |
| API E2E flow | 여러 endpoint를 이어 호출하는 사용자 흐름 (§11) | `@IntegrationTest`(`@SpringBootTest` + `@AutoConfigureMockMvc`), MockMvc | `com.devpilot.e2e` | `integration` / `integrationTest` | 예 |
| AI eval | 실제 모델 품질 (`17-ai-integration.md` §12) | JUnit + `RestClient`(실제 DeepSeek 호출) | `backend/src/evalTest/java` | — / `aiEval` | **아니오** (수동 workflow) |
| Flutter unit | controller(Notifier), repository 매핑, 오류 매핑, JSON round-trip | `flutter_test`, `ProviderContainer` | `app/test/` (lib mirror) | — / `flutter test` | 예 |
| Flutter widget | SCR-TODAY, SCR-REVIEW-SESSION 등 화면 상태 | `flutter_test`, `WidgetTester` | `app/test/features/**/presentation/` | — / `flutter test` | 예 |
| Flutter integration | 온보딩 → Today UI 흐름 (fake API) | `integration_test`, Chrome | `app/integration_test/` | — / `flutter drive` | 아니오 (tag 배포 전 `app-integration` job) |
| 성능 스모크 | non-AI API p95 (§13) | k6 | `perf/` | — | 아니오 (S3·S5 종료 전 수동, §13) |
| 배포 스모크 | health, 보안 헤더, 로그인 후 `/me` | shell (`curl`) | `infra/scripts/` | — | deploy workflow |

원칙:
- **규칙은 unit, 불변식과 트랜잭션은 integration, 흐름은 E2E**로 검증한다. 같은 내용을 여러 계층에서 반복하지 않는다.
- 단위·통합 테스트는 실제 AI API, 실제 운영 DB, 외부 네트워크를 호출하지 않는다.
- Mockito는 port 경계(`AuthenticatedUserResolver` in web slice, `AiProvider` 호출 횟수 확인이 필요한 곳)에만 쓴다. repository, entity, 규칙 클래스, `Clock`을 mock하지 않는다.

---

## 2. 도구 · 버전

버전은 Spring Boot 4.1.x BOM이 관리하는 값을 쓰고, BOM에 없는 것만 `libs.versions.toml`에 고정한다. 아래 "확인" 표시는 S0 bootstrap에서 실제 좌표·버전을 확인한다.

| 도구 | 기준 | 용도 · 규칙 |
|---|---|---|
| JUnit Jupiter | Boot 4.1 BOM (JUnit 6 계열, 확인) | `junit-jupiter`, `junit-jupiter-params`. JUnit 4·Vintage 금지 |
| AssertJ | BOM | 모든 assertion. `org.junit.jupiter.api.Assertions` import 금지(Checkstyle) |
| Mockito | BOM | 최소 사용(§1). Java 21+ 동적 agent 경고를 피하려고 Gradle test JVM에 `-javaagent:<mockito-core jar>` 설정 |
| Spring Boot Test | `spring-boot-starter-test` | slice 애노테이션 패키지는 Boot 4에서 모듈별로 나뉘었다 — import 경로 확인 |
| spring-security-test | BOM | `jwt()` post-processor, `SecurityMockMvcRequestPostProcessors` |
| Testcontainers | BOM (2.x, 확인) | **`postgres:16`** 이미지(개발·운영 DB와 같은 major, `18` §1.1), `@ServiceConnection`. 컨테이너는 JVM당 1개(singleton). 로컬 Docker가 없으면 서버 Docker를 SSH 터널로 쓴다(`18` §3.1: `DOCKER_HOST`, `TESTCONTAINERS_HOST_OVERRIDE`) |
| Awaitility | `spring-boot-starter-test` 전이 의존성 | 비동기 결과 대기. `Thread.sleep` 대신 사용, 기본 최대 10초. **`libs.versions.toml`이나 `build.gradle.kts`에 따로 선언하지 않는다** — Boot test starter가 가져온다 |
| `TestJwksServer` | JDK `com.sun.net.httpserver.HttpServer` (추가 의존성 없음) | 로컬 EC P-256 키의 JWKS 제공. JWKS 제공에는 MockWebServer·WireMock을 쓰지 않는다. DeepSeek 요청 검사(`DeepSeekAiProviderRequestTest`)는 spring-test의 `MockRestServiceServer`를 쓴다(추가 의존성 없음, `17-ai-integration.md` §12.1). WireMock·MockWebServer는 쓰지 않는다 |
| Nimbus JOSE + JWT | `spring-security-oauth2-jose` 전이 의존성 | 테스트 토큰 서명 (`TestJwtFactory`) |
| ArchUnit | `archunit-junit5` 1.x 최신 (확인) | ARCH 규칙 |
| JaCoCo | Java 25 지원 버전 (확인) | §15 |
| MockMvc | `spring-boot-starter-test` (`@AutoConfigureMockMvc`) | slice·통합·E2E 흐름의 endpoint 호출. E2E도 실제 포트를 띄우지 않으므로 `RestTestClient`·`TestRestTemplate`은 쓰지 않는다 |
| Flutter test | `flutter_test`, `integration_test` (SDK 내장) | FVM 고정 버전 |
| k6 | 최신 안정판 (로컬 설치) | 선택 실행 성능 스모크 |

### 2.1 Testcontainers 설정

```java
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfig {

    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16"))
                    .withDatabaseName("devpilot")
                    .withReuse(true); // effective only when ~/.testcontainers.properties enables reuse

    static {
        POSTGRES.start();
    }

    @Bean
    JdbcConnectionDetails jdbcConnectionDetails() {
        return connectionDetails(POSTGRES.getDatabaseName());
    }
}
```

- 모든 DB 테스트는 이 설정을 `@Import`한다(직접 `@Container` 필드를 만들지 않는다). Spring context 캐시가 재사용되도록 **통합 테스트 설정 조합을 `@IntegrationTest` 메타 애노테이션 하나로 고정**한다(§3.3).
- **컨테이너를 bean으로 두지 않는다.** 컨테이너를 `@Bean`(+`@ServiceConnection`)으로 두면 Spring이 **context를 닫을 때 컨테이너도 멈춘다**(reuse가 켜져 있을 때만 예외). context 하나가 기동에 실패하면 그 context를 닫으며 컨테이너까지 멈춰 **이후 모든 테스트가 `ConnectException`으로 무너진다** — 2026-09-21에 진짜 실패 1건이 288건으로 보였다. 컨테이너는 static으로 JVM 수명 동안 살리고 context에는 **접속 정보(`JdbcConnectionDetails`)만** 준다.
- **콘텐츠 catalog가 다른 context를 만들면 데이터베이스도 나눈다.** 통합 테스트는 소형 `test-content`로 seed한다. 다른 catalog(예: 저장소 루트 `content/`)를 쓰는 context가 같은 DB를 보면 (1) 같은 seed key를 구조가 다르게 정의할 때 `ChallengeSeedService`가 기동을 거부하고 (2) 한쪽 `ContentSeeder`가 상대의 seed를 `RETIRED`로 만든다. 같은 컨테이너 안에 DB를 하나 더 만들어 그 context에만 `@Primary JdbcConnectionDetails`로 물린다.
- 로컬 Windows: `%USERPROFILE%\.testcontainers.properties`에 `testcontainers.reuse.enable=true`. CI는 reuse를 켜지 않는다.
- Docker Desktop이 실행 중이 아니면 `integrationTest`는 실패한다(건너뛰지 않음).

---

## 3. 명명 · 구조

### 3.1 테스트 클래스와 메서드

| 대상 | 이름 |
|---|---|
| 단위 테스트 | `<ProductionClass>Test` — production 클래스 1개당 1개 |
| 통합 테스트 | `<ProductionClass>IntegrationTest` |
| E2E 흐름 | `<Flow>FlowTest` (`com.devpilot.e2e`) |
| 아키텍처 | `<Topic>ArchTest` (`com.devpilot.architecture`) |
| 여러 production 클래스를 가로지르는 보안 테스트 | `07-security-and-privacy.md` §16의 이름 그대로 (`AuthorizationIsolationTest` 등) |
| 메서드 | 영어 `should<Expected>When<Condition>`. 예: `shouldCapFinalRatingAtGoodWhenQuestionOnlyHintUsed` |
| `@DisplayName` | 선택. 쓰면 한국어 문장 (예: `@DisplayName("QUESTION_ONLY 힌트를 봤으면 최종 등급은 GOOD을 넘지 않는다")`) |
| 파라미터화 이름 | `@ParameterizedTest(name = "[{index}] {0}")` + vector의 `id` 컬럼 |

- 패키지는 production과 같다(`src/test/java/com/devpilot/review/domain/RuleBasedV1SchedulerTest.java`).
- 한 테스트 메서드는 한 동작을 검증한다. given/when/then 주석은 쓰지 않고 빈 줄로 구분한다.
- `@Nested`는 상태 전이표처럼 그룹이 명확할 때만 쓴다.
- `@TestMethodOrder`, 테스트 간 상태 공유 금지(E2E flow는 **한 메서드 안에서** 단계를 진행한다).

### 3.2 Source set과 태그

단일 `test` source set에 JUnit 태그로 나누고, 실제 모델 eval만 별도 source set으로 둔다.

| Source set | 디렉터리 | 태그 | Gradle task | `check` 포함 |
|---|---|---|---|---|
| `test` | `src/test/java`, `src/test/resources` | `unit` | `test` (`useJUnitPlatform { includeTags("unit") }`) | 예 |
| `test` | 같음 | `integration` | `integrationTest` (Test task, 같은 classpath, `includeTags("integration")`, `shouldRunAfter(test)`) | 예 |
| `evalTest` | `src/evalTest/java`, `src/evalTest/resources` | — | `aiEval` (`-PevalSuite=<suite>`) | **아니오** |

- 모든 테스트 클래스는 `@Tag("unit")` 또는 `@Tag("integration")`(메타 애노테이션 포함) 중 정확히 하나를 가진다. 태그가 없으면 어떤 task에서도 실행되지 않으므로 ArchUnit `TEST-02`가 실패시킨다.
- `evalTest`는 `test` 출력에 의존하지 않고 main에만 의존한다. `DEEPSEEK_API_KEY`가 없으면 즉시 실패한다.

### 3.3 메타 애노테이션

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestcontainersConfig.class, TestClockConfig.class, TestJwksConfig.class, FakeAiConfig.class})
public @interface IntegrationTest {}
```

`FakeAiConfig`의 `FakeAiProvider` bean에는 main과 같은 `@ConditionalOnProperty("devpilot.ai.provider", havingValue = "fake")`를 붙인다. 그래서 provider를 덮어쓰는 테스트(E2E-07의 `disabled`)에서는 등록되지 않고 `DisabledAiProvider`가 선택된다.

`@UnitTest`(`@Tag("unit")`)도 같은 방식으로 `com.devpilot.testsupport`에 둔다. **E2E 흐름(§11)도 같은 `@IntegrationTest`를 쓴다** — 별도의 E2E 메타 애노테이션을 두지 않는다. `@AutoConfigureMockMvc`가 이 애노테이션에 들어 있어 흐름 테스트가 MockMvc로 endpoint를 순서대로 호출하고, 같은 Spring context를 나머지 통합 테스트와 공유한다(서버 포트를 띄우지 않는다). 테스트마다 `@MockitoBean`이나 `@TestPropertySource`를 추가하면 context 캐시가 깨지므로, 통합 테스트에서는 **AI provider 설정 변경(E2E-07) 외에 추가하지 않는다.**

### 3.4 테스트 코드 규칙 (ArchUnit, `TestCodeRulesArchTest`)

| ID | 규칙 |
|---|---|
| TEST-01 | 테스트 코드에서 `Thread.sleep` 호출 금지 (Awaitility 또는 `MutableClock`) |
| TEST-02 | `*Test` 클래스는 `unit` 또는 `integration` 태그 중 정확히 하나 |
| TEST-03 | `org.junit.jupiter.api.Disabled`는 메시지에 `flaky: #<issue>` 형식만 허용하고, 전체 개수 1개 이하 (§16) |
| TEST-04 | `com.devpilot.architecture` 외 테스트에서 `@TestMethodOrder` 금지 |
| TEST-05 | `@RetryingTest`, JUnit Pioneer retry, Gradle test-retry plugin 사용 금지 |

---

## 4. Fixture

### 4.1 공용 테스트 지원 (`com.devpilot.testsupport`)

| 클래스 | 역할 |
|---|---|
| `MutableClock` | `Clock` 구현. `setInstant(Instant)`, `advance(Duration)`, zone 고정 UTC. 스레드 안전(`AtomicReference`) |
| `TestClockConfig` | `@Primary Clock` bean = `MutableClock`(초기값 `2026-10-05T10:00:00Z`, KST 19:00). 각 통합 테스트는 `@BeforeEach`에서 필요한 시각으로 `setInstant` |
| `FixedIds` | 결정적 UUID: `FixedIds.uuid(7)` → `00000000-0000-0000-0000-000000000007`. 규칙 unit 테스트 입력용. 저장 경로의 ID는 애플리케이션이 만든다 |
| `TestDataFactory` | 도메인 입력 record·entity builder의 진입점. `TestDataFactory.reviewItem().intervalDays(4).dueAt(…).build()` 형식. 모든 필드에 유효한 기본값 |
| `TestUsers` | `TestUsers.owner()`, `TestUsers.invited()`: 매 호출마다 새 `sub`(랜덤 UUID) + allowlist 이메일. `TestUsers.stranger()`: allowlist 밖 이메일 |
| `TestJwtFactory` | `TestJwksServer` 키로 ES256 토큰 발급. `validToken(user)`, `withClaims(user, Consumer<JWTClaimsSet.Builder>)`, `signedWith(otherKey)`, `withAlgorithm(…)` |
| `TestApi` | E2E·격리 테스트용 HTTP 헬퍼: `onboard(user)`, `generateToday(user, minutes)`, `startAttempt(user, challengeId)`, `createSideProject(user, name)`, `startRubberDuck(user, request)`, `submitTurn(user, sessionId, explanation)` 등. 매 POST에 새 `Idempotency-Key` |
| `OutboundRequestRecorder` | 서버가 외부로 HTTP 요청을 보내지 않는지 런타임에서 확인한다(§10.7). context 기동 **전에** `ProxySelector.setDefault`로 기록용 selector를 설치해, JDK HTTP 스택(`HttpURLConnection`, `java.net.http.HttpClient`와 이를 쓰는 `RestClient`)이 연결 전에 조회하는 URI를 모은다. `nonLoopbackRequests()`는 `127.0.0.1`·`localhost`(테스트 서버, `TestJwksServer`)를 뺀 목록이다. 기본 selector를 가로채는 방식이 모든 클라이언트에 적용되는지는 S0에서 확인한다(확인) |
| `FakeAiProvider` test hook | `fakeAiProvider.use(AiOperation, "case")`로 fixture 선택(`17-ai-integration.md` §12.2), `receivedRequests`, `callCount`, `blockUntilReleased`/`release`, `reset` (§10.1) |
| `AuditLogCapture` | Logback `ListAppender`로 `com.devpilot.audit` 로거 이벤트 수집 (`07` §6.3 필드 검증) |

### 4.2 데이터 규칙

- 통합 테스트는 **테이블을 비우지 않는다.** 테스트마다 `TestUsers`로 새 사용자를 만들고, 모든 assertion을 그 사용자의 `userId` 범위로 한다. 그래서 병렬·순서와 무관하다.
- test profile allowlist: `devpilot.security.allowed-emails=owner@devpilot.test,invited@devpilot.test`(비허용 사용자는 `stranger@devpilot.test`). `app_user`에는 이메일 컬럼이 없으므로 같은 이메일·다른 `sub` 사용자를 여러 명 만들 수 있다.
- seed catalog는 `src/test/resources/content/`의 테스트 전용 소형 YAML(skill 12개, challenge 6개, 카드 10장, `curated-repos.yaml` 저장소 1개·reading 3개 — 키 `READ.TESTREPO.*`, `pinnedCommit`은 40자 가짜 SHA)을 `devpilot.content.location`으로 지정해 context 기동 시 1회 적재한다. 운영 `content/`에 테스트가 의존하지 않는다. 테스트 저장소의 `url`은 `https://repo.example.invalid/…`처럼 해석되지 않는 호스트를 쓴다(§10.7 외부 요청 검사).
- entity를 테스트에서 직접 `repository.save`해 상태를 만드는 것은 JPA slice와 불변식 테스트에서만 허용한다. 그 외에는 API 또는 application service로 만든다.

---

## 5. 규칙 test vectors

### 5.1 파일 규칙

- `06-learning-engine-rules.md`의 Test vectors 표는 **한 행도 빠짐없이** 파일로 옮긴다(`06` 머리말).
- 위치: `backend/src/test/resources/vectors/`
- 이름: `06-<절 번호 2자리>-<규칙 kebab-case>.<csv|yaml>` (예: `06-06-review-schedule.csv`). `19-content-spec.md`의 vector(§5.4 계획 템플릿 배치)도 같은 규칙으로 옮기고 접두사만 `19-`로 쓴다(`19-05-plan-template-placement.yaml`).
- 표 형태(한 행 = 입력 몇 개 + 기대값)는 CSV, 중첩 입력(이벤트 목록, rubric 목록, 후보 skill)은 YAML.
- 첫 줄 주석에 출처와 행 수를 적는다: `# source: 06 §6.6, rows: 10`. `VectorFileTest`가 모든 파일의 실제 행 수와 선언 행 수를 비교한다.
- 모든 행에 `id` 컬럼(`V01`…)을 둔다. 테스트 이름에 표시된다.
- `06`의 vector를 바꾸는 PR은 같은 PR에서 파일을 고친다. 파일만 바꾸는 PR은 리뷰에서 거절한다.
- CSV 소스: `@CsvFileSource(resources = "/vectors/06-06-review-schedule.csv", numLinesToSkip = 2)`(주석·헤더). `nullValues = "null"`을 지정해 `null` 리터럴은 null, 빈 칸은 빈 목록으로 읽는다. enum은 이름 문자열, 목록은 `|` 구분.
- YAML 소스: `@MethodSource`에서 `VectorLoader.load("06-07-skill-level.yaml", SkillLevelVector.class)`(테스트 전용 record, Jackson YAML).

예 (`06-06-review-schedule.csv`):

```csv
# source: 06 §6.6, rows: 10
id,prevInterval,selfRating,evaluatedOutcome,hintLevel,expectedFinal,expectedAdjustedBy,horizonDaysFromAnswer,expectedInterval
V01,1,GOOD,NOT_EVALUATED,SELF_EXPLAIN,GOOD,,null,2
V06,30,GOOD,CORRECT,PSEUDOCODE,AGAIN,HINT_CAP_AGAIN,null,1
V10,10,GOOD,CORRECT,SELF_EXPLAIN,GOOD,,5,4
```

### 5.2 `06` 절 → vector 파일 → 테스트 클래스

| `06` 절 | vector 파일 | 테스트 클래스 | 계층 |
|---|---|---|---|
| §1 N-4, N-5, N-7 | `06-01-fixed-point-math.csv` (경계: 0, 음수 거부, overflow) | `FixedPointMathTest` | unit |
| §1 N-6 | — (설정 바인딩 케이스) | `DevPilotPropertiesBindingTest` | unit |
| §2 | `06-02-plan-day.csv` (5행, DST zone vector 포함) | `PlanDayCalculatorTest` | unit |
| §3 | `06-03-study-budget.csv` (7행) | `StudyBudgetCalculatorTest` | unit |
| §4.2 | `06-04-required-minutes.csv` (3행) | `DeadlineRiskEvaluatorTest` | unit |
| §4.3 | `06-04-risk-level.csv` (8행) | `DeadlineRiskEvaluatorTest` | unit |
| §4.4 | `06-04-replan-suggestion.yaml` (4행 — vector 1·2 축소, 3·4 확장. §5.3) | `ReplanSuggestionPolicyTest` | unit |
| §5.3 | `06-05-task-proposal.yaml` (분기 6개(REDO·READ_CODE 포함) + comeback + 제안 분기 T-1~T-5, 학습 트랙 T-6~T-9. §5.3·§5.4) | `TaskProposalPolicyTest` | unit |
| §5.10 RE-1~RE-3 | `06-05-redo-candidate.yaml` (RE-V1~RE-V11. §5.4) | `RedoTaskPolicyTest` | unit |
| §5.10 RE-6~RE-8 | — (RE-V12~RE-V15. §5.4) | `TodayPlanServiceIntegrationTest` | integration |
| §5.4–5.5, §5.7 | `06-05-planner-score.yaml` (10행 — 1~6행은 factor·modifier, 7~10행은 `MONOTONY_*`. `devpilot.planner.weights.stage-gap = 0`으로 돌린다. §5.7 공통 조건) | `PlannerScoringTest` | unit |
| §5.11 | `06-05-learning-stage.yaml` (ST-V1~ST-V12) | `LearningStageEvaluatorTest` | unit |
| §5.12 | `06-05-daily-tip.yaml` (TIP-V1~TIP-V11) | `DailyTipSelectorTest` | unit |
| §5.5 comebackMode | `06-05-comeback-mode.csv` | `ComebackModePolicyTest` | unit |
| §5.6 | `06-05-time-allocation.csv` (6행) | `TimeAllocatorTest` | unit |
| §5.6 추가 과제 | `06-05-extra-tasks.yaml` (E-1~E-5) | `TimeAllocatorTest` | unit |
| §5.8 | `06-05-reasons.yaml` (최소 1개 보장, modifier 우선, 최대 3개) | `ReasonTemplatesTest` | unit |
| §5.9 | — (상태 조합 표 7칸) | `TodayPlanServiceIntegrationTest` | integration |
| §6.1 | `06-06-review-schedule.csv`의 final·adjustedBy 컬럼 | `FinalRatingPolicyTest` | unit |
| §6.2, §6.6 | `06-06-review-schedule.csv` (10행) | `RuleBasedV1SchedulerTest` | unit |
| §6.3 | `06-06-first-due.yaml` | `SeedCardAssignmentServiceIntegrationTest` | integration |
| §6.4 | — (failures 2/4 경계, variant 상태) | `ReviewServiceIntegrationTest` | integration |
| §6.5 | `06-06-due-selection.yaml` (경계: `due_at = planDayStart(today + 1)`은 제외) | `DueReviewSelectorTest` | unit |
| §6.5 3단계, §6.6 RV-INTERLEAVE | `06-06-interleave.yaml` (3행 (a)~(c). §5.3) | `DueReviewSelectorTest` | unit |
| §7.2–7.4, §7.6 #1–12, #15–17 | `06-07-skill-level.yaml` (+ 러버덕 설명 증거 경계 RB-1~RB-6 §5.3, 독립 구현 증거 RD-V1~RD-V4 §5.4) | `SkillLevelRulesTest` | unit |
| §7.5, §7.6 #13–14 | `06-07-planning-level.csv` | `PlanningLevelPolicyTest` | unit |
| §8.1 | `06-08-rubric-coverage.yaml` (6행) | `RubricScorerTest` | unit |
| §8.2 | `06-08-rubric-coverage.yaml`의 outcome 컬럼 | `AttemptOutcomeCalculatorTest` | unit |
| §8.3 | — | `SubmissionEvaluationTaskIntegrationTest` | integration |
| §9.1–9.2 | `06-09-hint-ladder.csv` (9행 — HL-9 재현 잠금 3행 포함) | `HintLadderPolicyTest` | unit |
| §9.6 | `06-09-vague-reference.yaml` (VR-V1~VR-V10) | `VagueReferenceCounterTest` | unit |
| §9.5 PN-1~PN-4 | — (유형별 필수·금지 조합) | `SideProjectNoteServiceIntegrationTest` | integration |
| §9.3 | `06-09-discovered-by.csv` | `DiscoveredByResolverTest` | unit |
| §9.4 | — (findingType × discoveredBy × skill 유무 조합, 같은 concept_key 재사용) | `CoachFindingServiceIntegrationTest` | integration |
| §9.5 RD-1~RD-7, RC-1~RC-4 | — (이 문서 §10.6·§10.7의 케이스 표) | `RubberDuckPolicyTest` 외 (§10.6.1 매핑표) | unit / integration |
| §10 | `06-10-verification-guard.csv` (7행) | `VerificationGuardTest` | unit |
| §11.2 | — (절차 1~9, `restoredDeferrals`, `acceptedDeferrals`와 중복 시 400, `acceptedTargetRaises` 검증 RX-1~RX-8 — §5.3) | `ReplanServiceIntegrationTest` | integration |
| `19` §5.4 (계획 템플릿 배치) | `19-05-plan-template-placement.yaml` (V1~V8. 입력은 `today`·`targetCompletionDate`뿐이고 창은 하나 `[today, targetCompletionDate]`. **milestone 9개** — `JAVA_BACKEND_DEFAULT`의 PREPARATION 8개 뒤에 CONSOLIDATION 1개. V6(62일, COMPRESSED)·V8(63일, SEQUENTIAL)은 모드 경계) | `PlanTemplatePlacementTest` | unit |
| §12 | `06-12-metrics.yaml` (`projectNoteCount`·`independentRedoCount` 행 포함) | `MetricsCalculatorTest` | unit |
| §13 | `06-13-requirement-fit.csv` (4행 + skill 없음 → null, target fallback 순서) | `RequirementFitClassifierTest` | unit |

- vector 외에 **경계값 테스트**를 같은 클래스에 추가한다: `availableMinutes` 5·720, interval 1·60, risk 경계 8,000·10,000·12,500bp, 확장 제안 경계 `ratioBp` 7,000·7,001과 `expandedRatioBp` 9,000·9,001, coverage 3,999·4,000·7,999·8,000bp, cooldown 23:59:59·24:00:00, 러버덕 방치 24:00:00·24:00:01(`<` 비교, §10.6.5).
- 규칙 클래스 생성자에 넘기는 설정은 `03` §9 기본값을 정수로 변환한 `TestRuleSettings.defaults()`를 쓴다.
- `PlanTemplatePlacementTest`는 템플릿 파일이 아니라 `19` §5.4의 가중치(`weightBp`)·`minDays`를 vector 입력으로 직접 받는다. 운영 템플릿의 milestone 수가 바뀌면(현재 9개) vector 파일과 `19` §5.4를 같은 PR에서 고친다(`python content/tools/validate_content.py --placement-vectors`로 재생성).

### 5.3 v3 규칙 테스트 (교차 학습 · 확장 제안 · READ_CODE 제안 · 러버덕 설명 증거)

`06`의 vector는 §5.1대로 파일로 옮긴다. 아래는 그 vector가 무엇을 확인하는지와, vector 밖에서 같은 클래스에 넣는 경계 케이스다.

**`DueReviewSelectorTest` — RV-INTERLEAVE (AC-29, S2).** `06` §6.6의 (a)~(c)를 `06-06-interleave.yaml` 3행으로 옮겨 `@ParameterizedTest`로 돌린다. 입력은 `06` §6.5 1~2단계를 마친 목록이고 skill은 `A`·`B`·`C`로 적는다.

| id | `06` §6.6 | 입력 → 기대 출력 | 추가 확인 |
|---|---|---|---|
| V01 | (a) 같은 skill 5장 연속 | `R1(A) R2(A) R3(A) R4(A) R5(A) R6(B) … R10(B)` → `R1 R2 R6 R4 R5 R7 R3 R8 R9 R10` | swap 2회, 끝의 `B` 3연속은 남는다(RV-INTERLEAVE-S) |
| V02 | (b) skill 1종 | `R1(A) … R5(A)` → 변화 없음 | swap 0회 |
| V03 | (c) 이미 섞여 있음 | `R1(A) R2(B) R3(A) R4(B) R5(C) R6(A)` → 변화 없음 | swap 0회 |

- 경계(같은 클래스): **cap 먼저**(RV-INTERLEAVE-C) — (a)의 입력에 cap 5를 주면 `R1~R5`(모두 `A`)만 남아 변화가 없고, 카드 집합은 cap 결과와 같다. **결정성**(RV-INTERLEAVE-D) — 같은 입력을 두 번 넣으면 같은 순서이고, 입력 목록 객체를 바꾸지 않는다(새 목록 반환). 재배치 전후 카드 수·`id` 집합이 같다.
- `TodayPlanServiceIntegrationTest`: REVIEW task의 `estimatedMinutes`가 재배치와 무관하게 같은 값인지 1케이스(`06` §6.5 끝 문단).

**`ReplanSuggestionPolicyTest` — 확장 제안 (AC-30, S2).** `06-04-replan-suggestion.yaml` 4행이 기준이다.

| id | `06` §4.4 | 기대 |
|---|---|---|
| V01 | vector 1 | 축소 2건 + defer `B, A, C`, `riskAfterSuggestions` MEDIUM(9800bp), `expansionSuggestions = []` |
| V02 | vector 2 | defer `B`만, `expansionSuggestions = []` |
| V03 | vector 3 (확장: 복원 + 상향) | `RESTORE_DEFERRED X`, `RAISE_TARGET T1` 2건. `Z`는 보지 않는다(복원 루프가 `Y`에서 멈춤). `riskAfterSuggestions` LOW 7800bp — 복원분은 risk에 넣지 않는다 |
| V04 | vector 4 (확장: 복원 없음, 상한 5) | `RAISE_TARGET Q` 1건. `P`는 건너뛰고(모든 축 target 5) `R`에서 멈춘다. `riskAfterSuggestions` LOW 8000bp |

- **축소와 배타**: 모든 행과 경계 케이스에서 `(mustTargetReductionSuggestions ∪ deferSuggestions)`와 `expansionSuggestions` 중 하나 이상이 비어 있음을 공통 assertion으로 확인한다. 경계: risk LOW·`ratioBp` 7,000 → 확장 / 7,001~8,000 → 세 배열 모두 `[]` / `ratioBp = null`(`effective = 0`) → 모두 `[]` / risk MEDIUM → 모두 `[]`.
- 6d: 같은 `(skill, 축)`이 두 번 나오지 않는다. 상향 후 target은 5를 넘지 않는다. 올릴 축 동점이면 K, I, E, D 순.

**`ReplanServiceIntegrationTest` — `acceptedTargetRaises` (AC-30, `05` §7.8, `06` §11.2 7단계).** 모두 새 plan이 생기지 않는 400이거나(RX-1~RX-6), 새 plan의 `plan_skill_target`을 확인한다(RX-7~RX-8).

| # | 요청 | 기대 |
|---|---|---|
| RX-1 | `newTarget` = 현재 그 축 target (또는 더 낮음) | 400 `VALIDATION_FAILED`, field error `acceptedTargetRaises[i].newTarget` → `TARGET_NOT_RAISED` (도메인 검사, `05` §1.2.3) |
| RX-2 | `newTarget` = 6 | 400 `VALIDATION_FAILED`, field error `acceptedTargetRaises[i].newTarget` → **`Max`**. `TargetRaiseInput.newTarget`의 `@Max(5)`가 도메인 검사보다 먼저 걸린다(`05` §1.2.3 Bean Validation code, §1.4.3 순서 9) |
| RX-3 | 같은 `(skillCode, axis)`를 `acceptedTargetReductions`와 `acceptedTargetRaises`에 동시에 | 400 `MUTUALLY_EXCLUSIVE` |
| RX-4 | 같은 skill을 `acceptedDeferrals`와 `acceptedTargetRaises`에 동시에 | 400 `MUTUALLY_EXCLUSIVE` |
| RX-5 | `acceptedTargetRaises` 안에 같은 `(skillCode, axis)` 2개 | 400 `DUPLICATE_VALUE` |
| RX-6 | plan에 없는 skill / 없는 code | 400 `SKILL_NOT_IN_PLAN` / `SKILL_CODE_UNKNOWN` |
| RX-7 | 현재 target 3인 축에 `newTarget` 4 | 201, 그 축 target 4, `adjustment = USER_EDITED`. 다른 축·다른 skill은 복사값 그대로 |
| RX-8 | 같은 skill의 **다른 축**을 축소·상향 | 201, 두 축 모두 반영, `adjustment = TARGET_REDUCED` |

- S1 빌드(최소 구현)에서는 `acceptedTargetRaises`가 비어 있지 않으면 400 `VALUE_NOT_ALLOWED`다(`05` §7.8). 이 케이스는 S2에서 RX-1~RX-8로 바꾼다.

**`TaskProposalPolicyTest` — READ_CODE (AC-28, S3).** `06` §5.3의 제안 분기 표 T-1~T-5를 `06-05-task-proposal.yaml`에 그대로 넣는다(T-1 KNOWLEDGE 0 → READING, T-2 → READ_CODE 15분·difficulty 2, T-3 AI `DISABLED` → READING, T-4 reading 소진 → PROJECT_TASK, T-5 key ASC로 `READ.MODULAR_MONOLITH.SECURITY_CONFIG.001` — restbucks는 2026-09-19 소스 점검에서 전부 은퇴했다). 같은 클래스의 추가 케이스:

- reading 선택: 사용자가 `COMPLETED`한 `READ_CODE` task의 key는 제외, 최근 14 plan-day 안에 제안된 key는 제외(14 plan-day 경계는 CHALLENGE의 "최근 14 plan-day" 제외와 같은 해석), 후보가 비면 3번 분기로 내려간다.
- `aiStatus = BALANCE_EXHAUSTED`도 `DISABLED`와 같게 READ_CODE를 막는다(`17` §3.10).
- PROJECT_TASK: `projectNeed`·에너지 조건을 만족해도 `ACTIVE` 사이드 프로젝트가 없으면 EXPLAIN(SP-1). `ACTIVE`가 여러 개면 `updated_at`이 가장 최근인 것의 이름이 제목에 들어간다(SP-2·SP-3). `PAUSED`·`DONE`만 있으면 EXPLAIN.

**`SkillLevelRulesTest` — 러버덕 설명 증거 (AC-26, `06` §7.2 "설명 증거와 coverage", RD-5).** `RUBBER_DUCK_COMPLETED` payload `{ sessionId, turns, gapCount, targetType, hintDisclosed }`. coverage는 `devpilot.rubberduck.evidence-coverage-bp`(7,000) 고정이다. cooldown은 지난 것으로 둔다.

| # | 현재 E | 최근 60일 이벤트 (해당 skill) | 기대 |
|---|---|---|---|
| RB-0 | 0 | 러버덕 `gapCount 2, turns 1` 1개 | E 0→1 `E1_ANY_EXPLANATION` (gap 수와 무관, `06` §7.2) |
| RB-1 | 1 | 러버덕 `gapCount 0, turns 3, hintDisclosed false` 1개 | E 1→2 `E2_PARTIAL` (7,000 ≥ 4,000) |
| RB-2 | 1 | 러버덕 `gapCount 1, turns 3` 1개 | 변화 없음 — 설명 증거가 아니다 |
| RB-2b | 1 | 정리 AI가 gap 1개를 냈지만 `NoAnswerGuard` NA-4가 제거(저장된 `gaps` 0, `rawGapCount` 1) → payload `gapCount 1` | 변화 없음 — `gapCount`는 가드 전 수다(RD-5) |
| RB-3 | 1 | 러버덕 `gapCount 0, turns 2` 1개 | 변화 없음 — `turns ≥ 3` 미달 |
| RB-4 | 2 | 러버덕 `gapCount 0, turns 3, hintDisclosed false` 2개 | E 2→3 `E3_COVERAGE` (7,000 ≥ 7,000, 독립 2개) |
| RB-5 | 2 | 위 2개 중 1개가 `hintDisclosed true` | 변화 없음 — 독립 증거 1개 |
| RB-6 | 3 | 독립 러버덕 증거 3개 + `REVIEW_ANSWERED wasVariant = true, evaluatedOutcome = CORRECT` 1개 | 변화 없음 — E4는 coverage ≥ 8,000 증거가 필요하고 러버덕은 7,000 고정 |

- `evidence_event_ids`에 해당 `RUBBER_DUCK_COMPLETED` 이벤트 ID가 들어가는지 RB-1·RB-4에서 확인한다.
- `skill_id`가 null인 세션은 이벤트를 남기지 않으므로(RD-7) 이 클래스의 입력이 될 수 없다. 그 경로는 `RubberDuckServiceIntegrationTest`(§10.6.2)가 확인한다.

### 5.4 재현 과제 · 학습 트랙 규칙 테스트

**`RedoTaskPolicyTest` — 재현 후보 (AC-31, S4, `06` §5.10).** `06-05-redo-candidate.yaml`에 RE-V1~RE-V11을 그대로 넣는다. 설정은 `min-days-after = 3`, `max-days-after = 7`, `max-attempts = 2`, `today = 2026-10-20`이다. 입력은 원본 task(유형, 완료 plan-day, `estimatedMinutes`, difficulty, `skillId`)와 그 원본을 가리키는 `REDO` task 목록(상태, 완료 plan-day, `withoutAi`)이고, 출력은 후보 여부와 `lastAttemptDate`다.

- 경계(같은 클래스): 창 시작 `daysBetween = 2 / 3`(RE-V1·RE-V2)과 창 끝 `7 / 8`(RE-V3·RE-V4)을 양쪽 다 확인한다. 시도 `1 / 2`(RE-V5·RE-V6). 정렬은 `lastAttemptDate ASC → 원본 task.id ASC`이고 skill 하나당 후보 1개만 나온다(RE-V10). **결정적이어야 한다** — 같은 입력에 항상 같은 출력.
- `TaskProposalPolicyTest`: 재현 후보가 있으면 CHALLENGE·READ_CODE 조건을 모두 만족해도 `REDO`가 나온다(0번 분기). estimated가 `limit`을 넘으면 `REDO`를 버리고 1번부터 다시 고른다(RE-V11, `06` §5.6).
- `PlannerScoringTest`: `REDO_DUE` ×13,000bp가 modifier 5번으로 마지막에 적용되고, `FATIGUE_TWO_DAYS`(×6,000)와 함께 걸리면 `floorDiv(floorDiv(base × 6000, 10000) × 13000, 10000)` 순서로 계산된다.
- `ReasonTemplatesTest`: `REDO` task면 `REDO_WITHOUT_AI`가 항상 들어가고 modifier·task reason 중 가장 앞이다(`06` §5.8).

**`TodayPlanServiceIntegrationTest` — 재현 완료 (AC-31, `06` §5.10 RE-6~RE-8).** RE-V12~RE-V15.

| # | 요청 | 기대 |
|---|---|---|
| RE-V12 | `{status: COMPLETED, redoWithoutAi: true}` | 200. `redo_without_ai = true`, `REDO_COMPLETED{withoutAi: true}` 1행(`REDO:{taskId}:{skillId}`), `review_item` 새 행 0 |
| RE-V13 | `{status: COMPLETED, redoWithoutAi: false}` | 200. 이벤트 `withoutAi: false`, `concept_key = REDO:{sourceTaskId}` 카드 1장(`source_type = REDO_TASK`, `origin = MANUAL`, `review_type = EXPLAIN`, `due_at = planDayStart(today + 1)`). 같은 원본으로 두 번째 실패면 카드를 새로 만들지 않고 due만 당긴다(I-06) |
| RE-V14 | `{status: COMPLETED}` (필드 생략) | 400 `VALIDATION_FAILED`(field `redoWithoutAi`, `VALUE_REQUIRED`). task `IN_PROGRESS` 그대로, 이벤트·카드 0 |
| RE-V15 | `READ_CODE` task에 `redoWithoutAi: true` | 400 `VALIDATION_FAILED`(`VALUE_NOT_ALLOWED`) |
| RE-V16 | `{status: SKIPPED}` (REDO) | 200. `redo_without_ai` null, 이벤트 없음. 다음 날 `RedoTaskPolicy`가 시도 1회로 센다 |

- DB CHECK: `learning_task_redo_answer_required`가 `REDO` + `COMPLETED` + null을 거부한다(§8.2 I-21).

**`RedoLockServiceIntegrationTest` — AI 잠금 (AC-31, RE-5·HL-9).**

| # | 상황 | 기대 |
|---|---|---|
| RL-1 | 그 challenge의 `REDO`가 `PLANNED` → `POST /challenge-attempts/{id}/hints` | 409 `AI_ASSIST_LOCKED_FOR_REDO`. `hint_disclosure` 0행, `HINT_DISCLOSED` 0건, AI 호출 0(자기설명이 없어도 이 코드가 먼저 — HL-9) |
| RL-2 | 그 challenge의 `REDO`가 `IN_PROGRESS` → `POST /rubber-duck` `{targetType: CHALLENGE}` | 409. 세션 0행, B의 기존 `IN_PROGRESS` 세션도 `ABANDONED`가 되지 않는다 |
| RL-3 | 그 사이드 프로젝트의 `REDO`가 `IN_PROGRESS` → `POST /rubber-duck` `{targetType: PROJECT_WORK}` | 409 |
| RL-4 | 같은 상황에서 **다른** challenge의 hint, `REVIEW_ITEM`·`CODE_READING`·`CONCEPT` 러버덕 | 정상(200/201) |
| RL-5 | `REDO`가 `COMPLETED`·`SKIPPED`·`DEFERRED` | 정상 — 잠금은 `PLANNED`·`IN_PROGRESS`뿐이다 |
| RL-6 | A의 `REDO`가 열려 있을 때 B가 같은 seed challenge의 hint 요청 | 정상 — 잠금은 사용자별이다 |

**`SkillLevelRulesTest` — 독립 구현 증거 (AC-31, `06` §7.2·§7.6 #16–17).**

| id | 현재 I | 최근 60일 이벤트 | 기대 |
|---|---|---|---|
| RD-V1 | 2 | `CHALLENGE_EVALUATED` SOLVED_INDEPENDENTLY d2 ×2(challengeId 다름) + `REDO_COMPLETED` withoutAi=true d2 ×1 | I→3 `I3_SOLVED_INDEPENDENT` (증거 3개, `evidenceKey` 3종) |
| RD-V2 | 2 | 위에서 `REDO_COMPLETED`가 withoutAi=**false** | 변화 없음 (증거 2개) |
| RD-V3 | 2 | `REDO_COMPLETED` withoutAi=true d2 ×3, `sourceTaskId` 모두 같음 | 변화 없음 — `evidenceKey` 1종(서로 다른 2개 조건 미달) |
| RD-V4 | 3 | `REDO_COMPLETED` withoutAi=true **d4** ×1 + `EVIDENCE_ACCEPTED` ×1 | 변화 없음 — I4는 `CHALLENGE_EVALUATED`만 센다(`06` §7.2) |

**`TaskProposalPolicyTest` — 학습 트랙 (AC-32, S3, `06` §5.3).** T-6~T-9를 `06-05-task-proposal.yaml`에 넣는다. 같은 클래스의 추가 케이스:

- `maxTaskDifficulty`가 3인 트랙에서 d3 challenge가 없으면 d2로 내려가고, d1까지 없으면 다음 분기로 간다.
- `readCodeMinKnowledge`가 2인 트랙에서 KNOWLEDGE 1 → READING, 2 → READ_CODE (경계 양쪽).
- 트랙 기본값은 `TestRuleSettings.defaults()`가 아니라 케이스 입력으로 받는다(`06` §5.1 `trackDefaults`).

**`DevPilotPropertiesBindingTest` — 트랙 설정 (AC-32).** `devpilot.tracks`에 `TargetRole` 값 하나라도 빠지면 기동 실패, `max-task-difficulty`가 1~5 밖이거나 `read-code-min-knowledge`가 0~5 밖이면 기동 실패, `devpilot.planner.redo.min-days-after > max-days-after`면 기동 실패.

---
## 6. JWT 테스트

### 6.1 Web slice (`jwt()`)

- 대상: controller 요청 검증, 인증 필요 여부, ProblemDetail 형식.
- `@WebMvcTest(XxxController.class)` + `@Import(SecurityConfig.class)` + `@MockitoBean AuthenticatedUserResolver`(→ 고정 `ResolvedUser` 반환).
- 요청: `.with(jwt().jwt(j -> j.subject(sub.toString()).claim("email", "owner@devpilot.test").issuedAt(now)))`.
- slice에서는 서명·issuer 검증을 하지 않는다. 그 검증은 §6.2에서만 한다.

### 6.2 통합 (`JwtValidationIntegrationTest`)

구성:
1. `TestJwksServer`가 기동 시 EC P-256 키쌍(`kid = "test-key-1"`)을 만들고 `http://127.0.0.1:<port>/auth/v1/.well-known/jwks.json`으로 공개키 JWKS를 제공한다.
2. `TestJwksConfig`가 `DynamicPropertyRegistrar`로 `spring.security.oauth2.resourceserver.jwt.issuer-uri = http://127.0.0.1:<port>/auth/v1`, `spring.security.oauth2.resourceserver.jwt.jwk-set-uri = http://127.0.0.1:<port>/auth/v1/.well-known/jwks.json`, `jws-algorithms = ES256`을 **직접** 등록한다(`application-test.yml`의 placeholder 해석에 의존하지 않음. test profile은 `auth-mode = supabase`로 두어 JWKS 경로를 검증한다 — `devtoken` 모드는 §6.4에서 따로 본다). test profile에는 CORS 설정이 없다(`07-security-and-privacy.md` §9.1).
3. `TestJwtFactory`가 같은 private key로 `iss`, `aud=authenticated`, `sub`, `email`, `iat`, `exp=iat+3600`을 넣어 서명한다. 시각은 `MutableClock` 기준.
4. 검증 대상 endpoint: `GET /api/v1/me`.

| # | 케이스 | 기대 |
|---|---|---|
| J-01 | 유효 토큰 | 200 |
| J-02 | `Authorization` 헤더 없음 | 401 `AUTHENTICATION_REQUIRED` |
| J-03 | `Bearer` 뒤 값이 JWT 형식 아님 (`abc`) | 401 |
| J-04 | `Basic …` 스킴 | 401 |
| J-05 | `exp = now − 61초` | 401 |
| J-06 | `exp = now − 59초` (skew 60초 안) | 200 |
| J-07 | `nbf = now + 120초` | 401 |
| J-08 | 다른 EC 키로 서명, 같은 `kid` | 401 |
| J-09 | JWKS에 없는 `kid` (재조회 후에도 없음) | 401, JWKS 요청 횟수 증가 확인 |
| J-10 | `alg = HS256`, JWKS 공개키 바이트를 HMAC secret으로 사용 | 401 |
| J-11 | `alg = none`, 서명 없음 | 401 |
| J-12 | RS256 키로 서명한 토큰 (설정은 ES256) | 401 |
| J-13 | `iss` = 다른 발급자 URL | 401 |
| J-14 | `aud = anon` | 401 |
| J-15 | `aud` 없음 | 401 |
| J-16 | `sub` 없음 | 401 |
| J-17 | `sub = "not-a-uuid"` | 401 |
| J-18 | `is_anonymous = true` | 403 `USER_NOT_ALLOWED` |
| J-19 | allowlist 밖 이메일 | 403 `USER_NOT_ALLOWED`, `app_user` 0행, `AUTH_USER_REJECTED` |
| J-20 | `email` 없음 + `sub`이 `allowed-subjects`에 있음 | 200 |
| J-21 | JWKS 키 회전: 서버가 `test-key-2`를 추가하고 새 키로 서명 | 200 (재조회) |
| J-22 | 401 응답 본문에 `expired`, `signature`, `issuer`, `kid` 문자열 없음 | 통과 |

- 401 응답은 모두 같은 `code`, `title`, `detail`이어야 한다(J-22와 함께 `ProblemDetailsLeakTest`에서 교차 확인).
- `RecentLoginRequiredTest`(`DELETE /me`, `03` §4.2): `amr[].timestamp` 최댓값 = `now − 5분`(허용), `now − 5분 − 1초`(403 `RECENT_LOGIN_REQUIRED`), 오래된 `amr` + 최근 `amr` 섞임(최댓값 기준 허용), `amr` 없음 + `iat = now − 4분`(허용, fallback), `amr` 없음 + `iat = now − 6분`(403), 인증 시각 `now + 61초`(403), 이미 `DELETION_REQUESTED`(검사 없이 202).

### 6.4 개발 토큰 모드 (`DevTokenIntegrationTest`, `auth-mode = devtoken`)

`devtoken`은 **운영을 포함한 기본 인증 모드**이므로(`03` §4.2, 결정 A) §6.2와 같은 비중으로 검증한다. `@TestPropertySource(properties = "devpilot.security.auth-mode=devtoken")`로 전용 context를 쓰고, 키는 기동 시 생성된 것을 그대로 쓴다(테스트가 PEM을 주입하지 않는다).

| # | 요청 | 기대 |
|---|---|---|
| D-01 | `POST /api/v1/dev/token` `{email: allowlist에 있는 값}` | 200, `tokenType = "Bearer"`, `expiresAt = now + 720h`, 감사 `AUTH_DEVTOKEN_ISSUED`. 이 시점에 `app_user` 행은 없다 |
| D-02 | D-01의 `accessToken`으로 `GET /me` | 200, `app_user` 1행 생성(JIT, `03` §4.3), `AUTH_USER_PROVISIONED` |
| D-03 | `POST /api/v1/dev/token` `{email: allowlist 밖}` | 403 `USER_NOT_ALLOWED`, 토큰 없음, `app_user` 행 없음, 감사 `AUTH_DEVTOKEN_REJECTED` |
| D-04 | `POST /api/v1/dev/token` `{email: "not-an-email"}` / body 없음·알 수 없는 속성 | 400 `VALIDATION_FAILED`(field `email`, code `Email`) / 400 `MALFORMED_REQUEST`(본문 파싱 실패, `03` §7) |
| D-05 | `POST /api/v1/dev/token`에 `Idempotency-Key` 없이 호출 | 200 (IK 대상이 아니다, `05` §1.7) |
| D-06 | `GET /api/v1/dev/jwks.json` | 200, `keys[0].kty = "EC"`, `crv = "P-256"`, `alg = "ES256"`, `kid` 존재, private key 필드(`d`) 없음, `Cache-Control: no-store` |
| D-07 | 발급 토큰의 claim | `iss` = `devpilot.security.devtoken.issuer` 설정값(test profile 고정값 `http://localhost/dev`, 운영은 `${APP_BASE_URL}/dev`), `aud = authenticated`, `sub`이 UUID, `email` 일치, `amr[0].method = "devtoken"` |
| D-08 | 다른 키로 서명한 같은 형식의 토큰 | 401 |
| D-09 | `exp`가 지난 토큰(`MutableClock` 전진) | 401 |
| D-10 | 같은 email로 두 번 발급 | `sub`이 같다(UUID v5(email)) → `app_user` 행은 여전히 1개 |
| D-11 | `auth-mode = supabase` context에서 `POST /api/v1/dev/token`, `GET /api/v1/dev/jwks.json` | 404 (bean 미등록) |
| D-12 | IP당 30회/시간 초과 | 429 `RATE_LIMITED` |

- prod profile + `auth-mode = devtoken` + `DEVPILOT_DEV_JWT_KEY` 없음 → **기동 실패**를 `ApplicationContextRunner`로 확인한다(`03` §9).
- prod profile + `auth-mode = devtoken` + 키 있음 → 기동은 **성공**하고 "비공개 네트워크 전용" WARN 1줄이 남는지 `ApplicationContextRunner` + `OutputCaptureExtension`으로 확인한다(`03` §4.2). `auth-mode = supabase`면 이 WARN이 없다.
- test profile은 `devpilot.security.devtoken.issuer`를 `http://localhost/dev`로 고정한다(`application-test.yml`). 공통 기본값은 `${APP_BASE_URL}/dev`인데 CI에 `APP_BASE_URL`이 없어 placeholder가 해석되지 않으므로, D-07의 `iss` 기대값은 이 설정값이다.
- `RecentLoginRequiredTest`(§6.2 끝)는 devtoken 발급 시각(`amr[].timestamp`)으로도 같은 규칙이 성립하는지 1케이스 확인한다.

---

## 7. 아키텍처 테스트 (ArchUnit)

규칙 정의는 `08-coding-conventions.md` §11.6이다. 모듈 의존 matrix는 `03-system-architecture.md` §2.2 표를 그대로 코드로 옮긴다.

| 테스트 클래스 | 규칙 |
|---|---|
| `ModuleDependencyArchTest` | ARCH-01, ARCH-02, ARCH-03 |
| `LayerArchTest` | ARCH-04, ARCH-09, ARCH-14, ARCH-20 |
| `TransactionBoundaryArchTest` | ARCH-10, ARCH-21 |
| `DomainPurityArchTest` | ARCH-11, ARCH-12, ARCH-13, ARCH-16 |
| `CodingRulesArchTest` | ARCH-05, ARCH-06, ARCH-07, ARCH-08, ARCH-15, ARCH-17, ARCH-18, ARCH-19 |
| `TestCodeRulesArchTest` | TEST-01 ~ TEST-05 (§3.4) |

- 클래스 로딩: `@AnalyzeClasses(packages = "com.devpilot", importOptions = ImportOption.DoNotIncludeTests.class)`를 공용 상수로 둔다. `TestCodeRulesArchTest`만 `OnlyIncludeTests`.
- 각 규칙은 `because("<ARCH-ID>: <문서 절>")`로 실패 메시지에 ID와 근거를 남긴다.
- ARCH-01 matrix는 `ModuleDependencyArchTest.ALLOWED_DEPENDENCIES` 상수 하나다. `03` §2.2를 바꾸는 PR은 이 상수를 같은 PR에서 바꾼다. v3에서 바뀐 행: 새 모듈 `project`(→ `common`만), 새 모듈 `rubberduck`(→ `common`, `integration.ai`, `skill`, `learning`, `review`, `training`, `today`, `project` — 다른 모듈은 이 모듈에 의존하지 않고 `account`만 예외), `today`(→ `project` 추가), `content`(→ `today` 추가 — `CuratedReadingRegistry` 등록), `coach`·`onboarding`(→ `project` 추가), `account`(→ `project`, `rubberduck` 추가). `learning`은 바뀌지 않는다(러버덕을 `learning`에 두면 `skill`·`review`와 순환이 생겨 독립 모듈로 뺐다). 테스트는 이 matrix에 순환이 없음도 확인한다.
- 빈 규칙 방지: `archRule.allowEmptyShould(false)`(기본값) 유지. 대상 클래스가 아직 없는 규칙(예: S0의 `*Task`)은 해당 클래스가 처음 생기는 단계에 활성화하고, 비활성 기간에는 `@Disabled`가 아니라 규칙 목록에서 제외한 뒤 PR 설명에 적는다.
- **런타임 이중 방어**: `AiGatewayTransactionGuardTest`(integration) — `TransactionTemplate` 안에서 `AiGateway.call`을 호출하면 `IllegalStateException`이 나고 `FakeAiProvider` 호출 횟수가 0인지 확인한다(T-2, `08` §4.2).
- 러버덕(`RubberDuckService`)은 SYNC operation 2개를 tx1 → AI → tx2로 부른다(`17` §3.11·§3.12). ARCH-10이 `RubberDuckService`의 클래스 레벨 `@Transactional`과 AI 호출 메서드의 `@Transactional`을 막고, 위 런타임 가드 때문에 §10.6·§11 E2E-08이 통과하면 트랜잭션 밖 호출도 함께 확인된다. `RubberDuckServiceIntegrationTest`에는 `fakeAiProvider.blockUntilReleased(RUBBER_DUCK)`로 호출을 붙잡은 동안 다른 요청이 같은 세션 행을 갱신할 수 있는지(= 행 잠금을 쥔 트랜잭션이 없는지) 1케이스를 둔다(§10.4 `SyncAiVersionConflictTest`와 같은 방식).

---

## 8. DB · migration 테스트

### 8.1 Migration

| 테스트 | 내용 |
|---|---|
| `FlywayMigrationIntegrationTest` | 빈 `postgres:16` DB → Flyway 전체 적용 성공 → `spring.jpa.hibernate.ddl-auto=validate`로 context 기동 성공(모든 entity 매핑이 스키마와 일치). 적용된 migration 버전 목록이 `db/migration` 파일 목록과 같음 |
| `SchemaSnapshotConsistencyTest` | 같은 컨테이너에 DB `snapshot`을 만들어 `database/schema.sql`을 실행한다. migration 적용 DB와 비교: `information_schema.columns`(테이블, 컬럼, 타입, nullable, default), `pg_constraint`(이름 무관, `pg_get_constraintdef` 정규화 문자열 집합), `pg_indexes.indexdef`(스키마 이름 치환 후 집합). 차이가 있으면 실패하고 차이 목록을 출력한다 (`04` §10 "schema.sql = 전체 migration 결과") |
| `SupabaseHardeningMigrationTest` | (현재 운영 DB는 순수 PostgreSQL이라 V1의 DO 블록은 no-op다. Supabase 도입 시 의미가 생긴다) migration 전에 역할 `anon`, `authenticated`를 만든 DB에 전체 migration 적용 → `has_schema_privilege('anon', 'devpilot', 'USAGE')`, `has_table_privilege('authenticated', 'devpilot.app_user', 'SELECT')` 등이 모두 `false` (AC-20) |
| `MigrationImmutabilityCheck` (CI step) | PR diff에서 `backend/src/main/resources/db/migration/V*.sql` 중 **base 브랜치**에 이미 있는 파일이 수정되면 실패 (`git diff --name-status origin/${{ github.base_ref }}... \| grep '^M.*db/migration/V'`). base는 `developer` 또는 `main`이다 — `main` 고정으로 두면 `developer`에만 머지된 migration의 수정을 놓친다. **첫 릴리스 태그(`v*`)가 생긴 뒤부터 검사한다** — `04` §10의 "적용된 migration 수정 금지"가 첫 배포부터 발효하기 때문이다 |

- `schema.sql`의 파일 끝 DO 블록은 `snapshot` DB에서도 실행된다(역할이 없으면 no-op).

### 8.2 불변식 테스트 (I-01 ~ I-17)

DB 제약 자체는 `InvariantConstraintIntegrationTest`에서 JDBC로 직접 위반 행을 넣어 `SQLState 23505`(unique) / `23514`(check)를 확인한다. 애플리케이션 경로는 해당 서비스 통합 테스트에서 409 코드를 확인한다.

| 불변식 | DB 제약 테스트 | 애플리케이션 경로 테스트 | 기대 |
|---|---|---|---|
| I-01 학습 목표 1개 | 같은 `user_id` 2행 → 23505 | `LearningGoalServiceIntegrationTest` | `PUT`은 갱신, 중복 생성 없음 |
| I-02 ACTIVE plan 1개 | ACTIVE 2행 → 23505 | `ReplanServiceIntegrationTest.shouldFlushSupersededPlanBeforeInsertingNewVersion` | replan 성공 + ACTIVE 1개. 그리고 **SQL 실행 순서**를 확인: `update learning_plan … SUPERSEDED`가 `insert into learning_plan`보다 먼저 실행됨(Hibernate `StatementInspector`로 수집) |
| I-02 동시 replan (AC-24) | — | `ReplanConcurrencyIntegrationTest`: 같은 plan·같은 `version`으로 스레드 2개가 `CountDownLatch`로 동시에 replan | 정확히 1개 201, 나머지 409 `CONCURRENT_MODIFICATION` 또는 `PLAN_NOT_ACTIVE`, ACTIVE plan 1개 |
| I-03 plan-day당 daily_plan 1개 | 23505 | `TodayPlanServiceIntegrationTest` | 재생성은 같은 행 갱신 |
| I-04 활성 main task 1개 | 같은 daily_plan에 `is_main=true`·`PLANNED` 2행 → 23505. `COMPLETED` 1행 + `PLANNED` 1행은 성공 | `TodayPlanServiceIntegrationTest`: `06` §5.9 표 7칸 전부, force 재생성 시 `DEFERRED` 변경 → flush → 새 main INSERT 순서 | 표의 결과·오류 코드 |
| I-05 IN_PROGRESS 세션 1개 | 23505 | `LearningSessionServiceIntegrationTest`: 진행 중 세션이 있을 때 새 세션 시작 | 기존 `ABANDONED`, 새 세션 201 |
| I-06 사용자·개념당 review item 1개 | 23505 | `ReviewItemServiceIntegrationTest`: 같은 `concept_key` 생성 | 기존 항목 `ACTIVE`, `due_at = min(기존, 내일 시작)` |
| I-07 VERIFIED 근거 | `VERIFIED` + `AI_REASONING` → 23514 | `VerificationGuardTest` | 강등 |
| I-08 도구 source_type | `STATIC_ANALYSIS` + `AI_JUDGMENT` → 23514 | `VerificationGuardTest` | 강등 |
| I-09 VALIDATED 필수 필드 | `VALIDATED` + `prompt null` → 23514 | `ChallengeValidationServiceTest` | `REJECTED` |
| I-10 rubric weight 합 10,000 | (DB 제약 없음) | `ChallengeValidationServiceTest`: 9,999 / 10,000 / 10,001 | 10,000만 통과 |
| I-11 hint 1회 저장 | 23505 | `HintServiceIntegrationTest`: 같은 단계 재요청 | AI 호출 0, 저장 1행 |
| I-12 레벨 변경 ↔ state change | — | ARCH-11 + `SkillStateUpdaterIntegrationTest`: 이벤트 처리 후 `user_skill_state` 레벨 변경 수 = `skill_state_change` 행 수 | 일치 |
| I-13 learning_event 불변 | — | ARCH-16 | setter 없음 |
| I-14 마스킹 후 저장 | — | `CoachReviewMaskingOrderTest` (§10.3) | 원문 미저장 |
| I-15 타 사용자 접근 불가 | — | `AuthorizationIsolationTest` (§9) | 404 |
| I-16 러버덕 `turn_no` 유일 | 같은 `session_id`·`turn_no` 2행 → 23505. `turn_no = 0` → 23514 | `RubberDuckServiceIntegrationTest` (§10.6.2): 턴 3개 제출 후 `turn_no` = 1, 2, 3. 같은 세션에 턴 2개를 동시에 제출(`blockUntilReleased`) | 연속 번호. 동시 제출은 1개 201, 1개 409 `CONCURRENT_MODIFICATION`, 턴 행 1개 추가 |
| I-17 `reading_key` ↔ `READ_CODE` (CHECK `learning_task_reading_key_type`) | `task_type = 'READ_CODE'` + `reading_key = null` → 23514. `task_type = 'EXPLAIN'` + `reading_key = 'READ.TESTREPO.X.001'` → 23514. `READ_CODE` + 값 있음 / `EXPLAIN` + null → 성공 | `TodayPlanServiceIntegrationTest`: READ_CODE main task의 `reading_key`가 제안된 reading의 key와 같고, 다른 유형 task는 null | 제약 이름이 `learning_task_reading_key_type`인지도 `pg_constraint`로 확인 |
| I-19 `reading_feedback`은 `READ_CODE`에만 (CHECK `learning_task_reading_feedback_type` + 값 CHECK) | `task_type = 'EXPLAIN'` + `reading_feedback = 'HELPFUL'` → 23514. `READ_CODE` + `reading_feedback = 'GREAT'` → 23514. `READ_CODE` + `HELPFUL`·`TOO_HARD`·`BORING`·null → 성공 | `TodayPlanServiceIntegrationTest`(아래 RC-1 행) | 제약 이름을 `pg_constraint`로 확인 |
| I-20 `redo_source_task_id` ↔ `REDO` (CHECK `learning_task_redo_source_type`) | `task_type = 'REDO'` + `redo_source_task_id = null` → 23514. `task_type = 'EXPLAIN'` + 값 있음 → 23514. `REDO` + 값 / `EXPLAIN` + null → 성공 | `TodayPlanServiceIntegrationTest`: REDO main task의 `redo_source_task_id`가 후보의 원본 task id와 같다 | 제약 이름을 `pg_constraint`로 확인 |
| I-21 `redo_without_ai`는 `REDO`에만·완료 시 필수 (CHECK `learning_task_redo_without_ai_type` + `learning_task_redo_answer_required`) | `EXPLAIN` + `redo_without_ai = true` → 23514. `REDO` + `COMPLETED` + null → 23514. `REDO` + `IN_PROGRESS` + null → 성공 | `TodayPlanServiceIntegrationTest` RE-V12~RE-V16(§5.4) | 400 `VALUE_REQUIRED` / 제약 이름 확인 |
| I-22 프로젝트 기록 본문 ↔ `note_type` (CHECK `side_project_note_body_by_type`) | `DECISION` + `incident_symptom` 있음 → 23514. `DECISION` + `decision_rationale = null` → 23514. `INCIDENT` + 넷 모두 있음 + `decision_*` null → 성공 | `SideProjectNoteServiceIntegrationTest`: 유형별 필수·금지 400 조합, `PATCH`로 필수 항목을 빈 문자열로 지우려 하면 400 `VALUE_REQUIRED` | CHECK가 어떤 경로로도 깨지지 않는다 |

### 8.3 Repository (JPA slice)

- 모든 custom `@Query`와 projection 메서드는 `@DataJpaTest` 테스트 1개 이상을 가진다. 타 사용자 행이 섞인 데이터에서 `userId` 조건으로 걸러지는지 확인한다.
- JSON·배열 컬럼 매핑 round-trip: `LearningTask.scoreBreakdown`(jsonb record), `reasonCodes`(varchar[]), `SkillStateChange.evidenceEventIds`(uuid[]).
- cursor 쿼리: 같은 `sortKey`를 가진 행이 여러 개일 때 `id`로 중복·누락 없이 페이지가 이어지는지 확인한다(21행, limit 10).
- v3 쿼리: `SideProjectRepository`의 planner 조회(`status = ACTIVE`, `updated_at` DESC 1개 — SP-3)와 목록(`updatedAt` DESC, `id` DESC cursor), `RubberDuckSessionRepository`의 방치 세션 조회(`status = IN_PROGRESS and started_at <= :cutoff`, 부분 인덱스 `idx_rubber_duck_session_in_progress`)와 턴 조회(`turn_no` ASC). 모두 다른 사용자 행이 섞인 데이터로 확인한다. `rubber_duck_turn`에는 `user_id`가 없으므로 턴 조회는 반드시 소유 세션을 먼저 `userId` 조건으로 찾은 뒤에 한다(`04` §7).
- FK `on delete set null`: `side_project` 행을 지우면 그것을 가리키는 `learning_task.side_project_id`·`coach_review.side_project_id`가 null이 되고 task·리뷰 행은 남는다. `coach_review`는 S4 전까지 API로 만들 수 없으므로 이 확인은 `InvariantConstraintIntegrationTest`처럼 JDBC로 행을 넣어 한다. API 경로는 §11 E2E-09가 본다.

---

## 9. 권한 격리 테스트

### 9.1 구조

- `AuthorizationIsolationTest`(`@IntegrationTest`)는 **endpoint catalog 하나를 입력으로 하는 파라미터화 테스트**다.
- catalog: `com.devpilot.security.UserOwnedEndpoints` — `record EndpointCase(String id, HttpMethod method, String pathTemplate, Kind kind, BiFunction<Owner, TestApi, Map<String, String>> pathVariables, Function<Owner, Object> body, String expectedNotFoundCode)`.
- `@BeforeAll`: 사용자 A(`TestUsers.owner()`)가 모든 리소스를 가진 상태를 API로 만든다(온보딩(사이드 프로젝트 포함), today(READ_CODE task 1개 포함), 세션, seed review 답변, 수동 카드, 러버덕 세션 2개(`COMPLETED` 1개, 턴 1개가 있는 `IN_PROGRESS` 1개), AI 생성 challenge + attempt + 제출 평가 완료, coach review 완료 + finding, evidence, weekly review, 요구사항 문서 — 해당 endpoint가 생기는 단계 전에는(예: 로드맵 비교는 S7) case를 catalog에 넣지 않는다).
- 사용자 B(`TestUsers.invited()`)는 온보딩만 한다. 사용자 C는 `TestUsers.stranger()`.

| 검증 | 대상 | 기대 |
|---|---|---|
| ISO-1 | `Kind.OWNED_RESOURCE`: B 토큰으로 A의 리소스 ID 사용 | 404 + case의 `expectedNotFoundCode`. A의 리소스 상태 변화 없음(요청 전후 `GET` 비교) |
| ISO-1b | `Kind.BODY_REFERENCE`: B 토큰으로 body에 A의 리소스 ID | 400 `VALIDATION_FAILED` + field error `REFERENCE_NOT_FOUND` (없는 ID와 같은 응답, `05-api-spec.md` §1.2.3). 새 행 없음 |
| ISO-2 | `Kind.SCOPED_COLLECTION`: B 토큰으로 호출 | 200, 응답에 A의 리소스 ID가 하나도 없음 |
| ISO-3 | 모든 case: `Authorization` 없음 | 401 `AUTHENTICATION_REQUIRED` |
| ISO-4 | 모든 case: C 토큰 | 403 `USER_NOT_ALLOWED` |
| ISO-5 | `IdempotencyTest` 연계: B가 A의 `Idempotency-Key` 값으로 같은 POST | 재생되지 않고 B의 요청으로 처리 |
| ISO-6 | B가 A의 cursor 문자열을 목록 API에 전달 | 200, B의 데이터만 |
| ISO-7 | `Kind.SHARED_CONTENT`: A 토큰과 B 토큰으로 같은 요청 | 둘 다 200이고 body가 같다(사용자 데이터가 섞이지 않음). 소유권 검사 대상이 아니다 |

- POST case는 매 요청 새 `Idempotency-Key`를 붙인다(401·403·404가 idempotency 처리보다 먼저인지도 함께 확인된다).
- `Kind.SHARED_CONTENT`는 인증은 필요하지만 사용자 소유가 아닌 공용 콘텐츠 조회다(`05` §19.7 "공용 조회"). ISO-3·ISO-4·ISO-7만 적용한다.

### 9.2 Catalog (`05-api-spec.md` 기준 사용자 소유 endpoint)

| Kind | endpoint (A의 ID를 넣는 위치) | 기대 code |
|---|---|---|
| OWNED_RESOURCE | `GET /plans/{planId}` | `PLAN_NOT_FOUND` |
| OWNED_RESOURCE | `PATCH /plans/{planId}/milestones/{milestoneId}` | `PLAN_NOT_FOUND` |
| OWNED_RESOURCE | `POST /plans/{planId}/replan/preview`, `POST /plans/{planId}/replan` | `PLAN_NOT_FOUND` |
| OWNED_RESOURCE | `PATCH /today/tasks/{taskId}` | `RESOURCE_NOT_FOUND` |
| BODY_REFERENCE | `POST /learning-sessions` (body `learningTaskId` = A의 task) | 400 `VALIDATION_FAILED` + field error `REFERENCE_NOT_FOUND` |
| OWNED_RESOURCE | `POST /learning-sessions/{sessionId}/complete`, `/abandon` | `RESOURCE_NOT_FOUND` |
| OWNED_RESOURCE | `GET /rubber-duck/{sessionId}`, `POST /rubber-duck/{sessionId}/turns`, `POST …/complete`, `POST …/abandon` (A의 `IN_PROGRESS` 세션) | `RESOURCE_NOT_FOUND`. A 세션의 `turn_count`·`status` 변화 없음, `RUBBER_DUCK`·`RUBBER_DUCK_SUMMARY` 호출 0 |
| BODY_REFERENCE | `POST /rubber-duck` (body `targetId` = A의 READ_CODE task(`CODE_READING`) / A의 attempt(`CHALLENGE`) / A의 review item(`REVIEW_ITEM`) / A의 사이드 프로젝트(`PROJECT_WORK`) — 대상 유형마다 1 case) | 400 `VALIDATION_FAILED` + field error `REFERENCE_NOT_FOUND`(field `targetId`). 새 세션 없음, B의 기존 `IN_PROGRESS` 세션도 `ABANDONED`로 바뀌지 않음 |
| OWNED_RESOURCE | `GET /side-projects/{sideProjectId}`, `PATCH /side-projects/{sideProjectId}`, `DELETE /side-projects/{sideProjectId}` | `RESOURCE_NOT_FOUND`. A의 프로젝트는 남아 있고 `version` 변화 없음 |
| OWNED_RESOURCE | `GET /side-projects/{sideProjectId}/notes`, `POST …/notes` (A의 프로젝트 id) | `RESOURCE_NOT_FOUND`. `side_project_note` 새 행 0 |
| OWNED_RESOURCE | `GET …/notes/{noteId}`, `PATCH …/notes/{noteId}`, `DELETE …/notes/{noteId}` (A의 프로젝트·기록 id) | `RESOURCE_NOT_FOUND`. A의 기록은 남아 있고 `version` 변화 없음. **B 본인 프로젝트 id + A의 기록 id** 조합도 같은 404(부모·자식 둘 다 검사, `07` §4.3) |
| OWNED_RESOURCE | `GET /challenges/{challengeId}` (A 소유 AI 생성 challenge) | `RESOURCE_NOT_FOUND` |
| OWNED_RESOURCE | `POST /challenges/{challengeId}/attempts` (A 소유 challenge) | `RESOURCE_NOT_FOUND` |
| OWNED_RESOURCE | `GET /challenge-attempts/{attemptId}` | `RESOURCE_NOT_FOUND` |
| OWNED_RESOURCE | `POST /challenge-attempts/{attemptId}/self-explanation`, `/hints`, `/submissions`, `/abandon` | `RESOURCE_NOT_FOUND` |
| OWNED_RESOURCE | `POST /challenge-attempts/{attemptId}/submissions/{submissionNo}/retry` | `RESOURCE_NOT_FOUND` |
| OWNED_RESOURCE | `POST /reviews/{reviewItemId}/answer` | `RESOURCE_NOT_FOUND` |
| OWNED_RESOURCE | `PATCH /review-items/{reviewItemId}` | `RESOURCE_NOT_FOUND` |
| OWNED_RESOURCE | `GET /coach/reviews/{reviewId}`, `POST /coach/reviews/{reviewId}/retry`, `POST …/complete`, `DELETE …/content` | `RESOURCE_NOT_FOUND` |
| OWNED_RESOURCE | `POST /coach/reviews/{reviewId}/findings/{findingId}/responses`, `/hints`, `PATCH …/findings/{findingId}` | `RESOURCE_NOT_FOUND` |
| OWNED_RESOURCE | `GET /evidence/{evidenceId}`, `PATCH /evidence/{evidenceId}`, `POST …/accept`, `POST …/reject` | `RESOURCE_NOT_FOUND` |
| BODY_REFERENCE | `POST /evidence/drafts` (body `sourceLearningEventId` = A의 이벤트 / `sourceProjectNoteId` = A의 기록 — 2 case) | 400 `VALIDATION_FAILED` + field error `REFERENCE_NOT_FOUND` |
| OWNED_RESOURCE | `GET /weekly-reviews/{weekStartDate}`, `PUT …/reflection` (B에게 해당 주 리뷰 없음) | `RESOURCE_NOT_FOUND` |
| OWNED_RESOURCE | `GET /requirement-docs/{requirementDocId}`, `DELETE /requirement-docs/{requirementDocId}` | `RESOURCE_NOT_FOUND` |
| SCOPED_COLLECTION | `GET /me`, `PATCH /me`, `GET /me/export`(A의 사이드 프로젝트·러버덕 세션이 B의 export에 없음 포함), `GET /learning-goal`, `GET /skills/me`, `GET /skills/{skillId}/history` (공용 skill ID), `GET /plans/active`, `GET /plans`, `GET /plans/active/budget`, `GET /today`, `GET /learning-sessions`, `GET /challenges`, `GET /reviews/due`, `GET /review-items`, `GET /side-projects`, `GET /coach/reviews`, `GET /dashboard`, `GET /evidence`, `GET /evidence/export`, `GET /weekly-reviews`, `GET /thinking-patterns/trend`, `GET /requirement-docs`, `GET /diagnostics/suggestions` | — |
| SHARED_CONTENT | `GET /skills/tree`(공용 skill catalog), `GET /readings/{readingKey}`(공용 코드 읽기 콘텐츠 — `CuratedReadingRegistry`, DB·사용자 데이터 없음, `05` §19.7) | — (ISO-7) |

- 404 `code` 열은 `05-api-spec.md`의 endpoint별 오류 표와 같아야 한다. 다르면 `05`를 기준으로 catalog를 고친다.
- `Kind.ACCOUNT_ACTION`(A의 ID를 넣을 위치가 없는 계정·생성 요청 — ISO-3·ISO-4만 적용): `DELETE /me`, `POST /me/calendar-token`, `POST /onboarding`, `POST /today/generate`, `POST /plans`, `POST /learning-sessions`(body 없음), `POST /rubber-duck`(`targetType = CONCEPT` — `targetId` 없음), `POST /side-projects`, `POST /review-items`, `POST /coach/reviews`, `POST /challenges/generate`, `POST /evidence`, `POST /requirement-docs`, `PUT /learning-goal`. catalog에 이 Kind로 등록한다.
- v3 endpoint 11개의 분류: `/rubber-duck*` 5개(OWNED_RESOURCE 4 + BODY_REFERENCE·ACCOUNT_ACTION으로 나눠 등록한 `POST /rubber-duck`), `/side-projects*` 5개(OWNED_RESOURCE 3 + SCOPED_COLLECTION 1 + ACCOUNT_ACTION 1), `GET /readings/{readingKey}`(SHARED_CONTENT). `POST /coach/reviews`의 `sideProjectId`(S4)는 BODY_REFERENCE case(A의 사이드 프로젝트 → 400 `REFERENCE_NOT_FOUND`)를 그 단계에 추가한다.
- 프로젝트 기록 endpoint 5개(S3)는 모두 **OWNED_RESOURCE**다 — 경로에 프로젝트 id가 있어 `POST`·목록도 A의 id를 넣을 자리가 있다. `ACCOUNT_ACTION`으로 등록하지 않는다.
- 제외 목록(`EndpointCatalogCompletenessTest`): `GET /api/v1/calendar/{token}.ics` — Bearer 인증을 쓰지 않으므로 `CalendarFeedSecurityTest`가 검증한다.

### 9.3 Catalog 완전성

`EndpointCatalogCompletenessTest`(integration)가 `RequestMappingHandlerMapping.getHandlerMethods()`의 모든 `/api/v1/**` 매핑을 읽어, 각 매핑이 `UserOwnedEndpoints` catalog 또는 제외 목록(`GET /api/v1/calendar/{token}.ics`)에 있는지 확인한다. 새 endpoint를 추가하고 catalog를 갱신하지 않으면 실패한다.

---

## 10. AI 테스트

### 10.1 `FakeAiProvider` fixture

- 형식·위치·선택 방법은 `17-ai-integration.md` §12.2가 기준이다: `backend/src/test/resources/ai-fixtures/<OPERATION>/<case>.json`, `attempts[]`(호출 순서별 응답, `stopReason`, `output`/`outputRaw`, `simulate`, `usage`), 선택은 `fakeAiProvider.use(AiOperation.X, "case")`, 지정이 없으면 `default`.
- `test` profile의 provider는 `fake`다(`03` §10). 실제 API는 호출하지 않는다.
- 테스트 전용 확인 기능(`FakeAiProvider`의 test hook, main 코드에서 호출 금지):

| 메서드 | 용도 |
|---|---|
| `receivedRequests(AiOperation)` | provider가 받은 `AiRequest` 목록 — 마스킹본 전달 확인(§10.4) |
| `callCount(AiOperation)` | AI 호출 횟수 — HL-1 캐시, idempotency 재생 확인 |
| `blockUntilReleased(AiOperation)` / `release(AiOperation)` | `CountDownLatch`로 호출을 붙잡아 동시성 테스트(E2E-06 7번, `SyncAiVersionConflictTest`) |
| `reset()` | `@AfterEach`에서 선택·기록 초기화 |

- 모든 fixture의 `output`이 스키마에 맞는지는 `OutputSchemaConsistencyTest`(`17` §12.1)와 함께 `AiFixtureSchemaTest`가 검사한다. 가드 위반을 일부러 담은 fixture는 파일 이름에 `-violation`을 붙이고 JSON 형식만 검사한다.

### 10.2 결정적 AI 테스트

테스트 목록과 vector는 `17-ai-integration.md` §12.1이 기준이다. 이 문서는 계층·태그와 추가 확인 항목만 정한다.

| 테스트 | 기준 vector | 계층 | 추가 확인 |
|---|---|---|---|
| `SecretMaskerTest`, `SecretMaskerPerformanceTest` | `17` §7.3 V1~V23, 30KB **200ms** 이내 | unit | 입력 문자열은 런타임 조합(`07` §11.3). 공유 CI runner에서 벽시계 단언은 흔들리므로 상한을 넉넉히 둔다(§16 flaky 예방). 실제 목표는 10ms이고 회귀 감시는 값 자체가 아니라 실패 여부로 한다 |
| `CodeDetectorTest`, `CodeLeakGuardTest` | `17` §6.3 C1~C11 | unit | HL-8 경계(`06` §9.1) |
| `EnumGuardTest`, `FindingCountGuardTest`, `SkillCodeGuardTest`, `LanguageGuardTest` | `17` §6.4~§6.7 | unit | — |
| `NoAnswerGuardTest` | `17` §6.8 NA-1~NA-4 — `17`에 vector 표가 없으므로 이 문서 §10.6.3의 NG-01~NG-14가 기준 | unit | 러버덕 2개 operation 전용. SYNC라 위반 시 재시도 없음 |
| `VerificationGuardTest` | `06` §10 vector 7행 + `17` §6.2 추가 vector | unit | 하위 도메인 허용(`sub.docs.spring.io`), 유사 도메인 거부(`docs.spring.io.evil.com`), URL fetch 없음(ArchUnit `ARCH-19`) |
| `AiCostCalculatorTest` | `17` §8.4 K1~K6 | unit | — |
| `AiBudgetGuardTest` | `17` §8.5 B1~B17 | integration | §10.3 |
| `AiGatewayTest` | `17` §5.3 표 전체 | integration | 가드 위반 재시도는 **ASYNC만 1회**(`attempt_no` 2), SYNC는 재시도 없이 502 `AI_OUTPUT_INVALID`. SDK 자체 재시도는 끔(`maxRetries(0)`), provider 호출마다 `ai_call_log` 1행(실패 포함, 원문 없음) |
| `PromptRegistryTest`, `PromptImmutabilityTest`, `OutputSchemaConsistencyTest`, `DeepSeekAiProviderRequestTest` | `17` §12.1 | unit | — |
| `AiGatewayTransactionGuardTest` | §7 런타임 이중 방어 | integration | — |

### 10.3 예산 경계 (`AiBudgetGuardTest`, integration)

- **벡터 기준값**: 운영 기본 월 예산은 USD 3(= 3,000,000 micro, 경고 2,400,000)이지만, 이 절과 `17` §8.5의 벡터는 `application-test.yml`이 고정한 **test profile 값 USD 25**(25,000,000 micro) 기준이다. 예산 값을 바꿔도 벡터는 그대로 쓴다.
- 결정(DEC-06, `03` §9): 월 예산은 **서비스 전체** 합계, 월 경계는 `devpilot.time.default-zone`(Asia/Seoul) 달력 월. 일일 한도는 사용자별, 사용자 plan-day 시작부터. 동시 실행은 사용자별 (진행 중 동기 호출 + `PENDING`/`RUNNING` 비동기 작업) ≥ 2.
- `17` §8.5 B1~B17를 그대로 파라미터화 케이스로 옮기고, 통합 테스트에서만 확인할 수 있는 아래 항목을 추가한다. 누적 비용은 `ai_call_log` 행을 직접 넣어 만든다(서로 다른 사용자 2명에 나눠 넣어 서비스 전체 합계를 검증).

| # | 조건 | 기대 |
|---|---|---|
| BI-01 | 사용자 A 10,000,000 + 사용자 B 9,999,999 micro USD (합 79.99996%) 후 A 호출 1회로 20,000,000 이상 | 호출 허용, 이후 A·B 모두 `aiStatus = BUDGET_WARNING`, `AI_BUDGET_WARNING` 정확히 1회 |
| BI-02 | 합계 25,000,000 | A·B 모두 429 `AI_MONTHLY_BUDGET_EXCEEDED`, `Retry-After` = 다음 달 1일 00:00 KST까지 초, `ai_call_log.status = BUDGET_BLOCKED` 1행, `AI_BUDGET_BLOCKED`(`reason = MONTHLY_BUDGET_EXCEEDED`), provider 호출 0 |
| BI-03 | BI-02 상태에서 `POST /today/generate`, `POST /reviews/{id}/answer`(`evaluate=false`), `GET /dashboard` | 모두 정상 (AC-12, AC-13) |
| BI-04 | BI-02 상태에서 `POST /reviews/{id}/answer`(`evaluate=true`) | 200, 답변 저장, `evaluationSkippedReason = AI_BUDGET_EXCEEDED` |
| BI-05 | BI-02 상태에서 `POST /challenge-attempts/{id}/submissions`(S3), `POST /rubber-duck/{id}/turns`(S3), `POST /coach/reviews`(**S4에 추가**) | 429, submission·`rubber_duck_turn`·coach_review 행 없음, 러버덕 세션 `turn_count` 변화 없음 |
| BI-05a | BI-02 상태에서 턴이 3개인 러버덕 세션에 `POST /rubber-duck/{id}/complete` (S3) | **200**(오류 아님), `status = COMPLETED`, `summarySkippedReason = AI_BUDGET_EXCEEDED`, `summary = null`, 복습 카드·`RUBBER_DUCK_COMPLETED` 없음, `RUBBER_DUCK_SUMMARY` provider 호출 0 (`05` §1.9.4 "동기, 대체 있음") |
| BI-06 | clock `2026-10-31T15:00:00Z`(= 11-01 00:00 KST) | 허용, `ENABLED` |
| BI-07 | 일일 한도 거부 | 429 `AI_DAILY_LIMIT_EXCEEDED`, `Retry-After` = 다음 plan-day 시작까지 초, 다른 사용자는 허용 |
| BI-08 | 같은 사용자 `PENDING` 비동기 작업 1개 + 동기 hint 1개 진행 중(`blockUntilReleased`)에 비동기 시작 요청 | 429 `AI_CONCURRENCY_LIMIT`, `Retry-After: 5`, 저장 없음 |

### 10.4 순서·동시성·대체 동작

| 테스트 | 내용 |
|---|---|
| `CoachReviewMaskingOrderTest` | (1) `content` 또는 `userSelfReview`에 private key → 422 `SECRET_DETECTED_BLOCKED`, `coach_review` 0행, `ai_call_log` 0행, 예산 집계 변화 없음, `SECRET_BLOCKED` 1회 (2) `content`와 `userSelfReview`에 일반 secret 각 1개 → 202, `maskedSecretCount = 2`, 저장값에 원문 secret 없음, `receivedRequests(COACH_REVIEW)`에 원문 secret 없음 (3) 예산 초과 상태 → 마스킹은 통과해도 429, 저장 없음 (AC-14, I-14) |
| `SecretMaskingEndpointsTest` | (격리 catalog처럼 **해당 endpoint가 생기는 단계에 행을 추가**한다) `05` §1.11 표의 endpoint·필드마다: private key → 422 + 행 없음, 일반 secret → 저장값 마스킹본 (coach finding 응답 `text`, 자기설명, 제출 `answerText`·`code`, 복습 `answerText`, 세션 `selfReflection`, `sourceText`). v3 행: **러버덕 `explanation`**(S3) — private key → 422, `rubber_duck_turn` 0행·`turn_count` 변화 없음·`RUBBER_DUCK` 호출 0·`SECRET_BLOCKED` 1회 / 일반 secret → 201, `rubber_duck_turn.user_text`와 `receivedRequests(RUBBER_DUCK)`의 `learnerExplanation`에 원문 없음, 다음 턴·정리 호출의 `conversation`에도 원문 없음. **사이드 프로젝트 `name`·`description`·`stack`**(S1) — `POST /side-projects`, `PATCH /side-projects/{id}`, `POST /onboarding`의 `sideProject` 각각: private key → 422 + `side_project` 행 없음(온보딩은 전체 롤백), 일반 secret → 저장값 마스킹본. `repoUrl`은 마스킹 대상이 아니다(URL 필드) |
| `SyncAiVersionConflictTest` | 동기 AI(`HINT_GENERATE`) 실행 중(`blockUntilReleased`) 다른 요청이 대상 `version`을 바꾸면 결과를 저장하지 않고 409 `CONCURRENT_MODIFICATION` (`03` §5.3). 러버덕 케이스: `RUBBER_DUCK` 실행 중 같은 세션에 `POST /rubber-duck/{id}/abandon` → 턴 요청은 409 `CONCURRENT_MODIFICATION`, 턴 행 없음, 세션 `ABANDONED` 유지 |
| `OrphanAsyncTaskJobIntegrationTest` | `RUNNING` + `status_updated_at = now − 10분 1초` → `FAILED(INTERRUPTED)`, `now − 9분 59초` → 변화 없음 |
| `ReviewEvaluateFallbackTest` | `REVIEW_EVALUATE` fixture `simulate: TIMEOUT` → 답변 200 저장, `evaluatedOutcome = NOT_EVALUATED`, `evaluationSkippedReason = AI_TIMEOUT` |
| `CoachResponseFeedbackFallbackTest` | `COACH_RESPONSE_FEEDBACK` fixture `simulate: PROVIDER_ERROR` → 응답 `text`(마스킹본) 저장, finding `USER_RESPONDED`, `feedbackSkippedReason = AI_UNAVAILABLE`, `user_identified_issue` 변화 없음 |
| `TodayChallengeProposalAiDisabledTest` | `aiStatus = DISABLED`(월 예산 도달) 또는 `BALANCE_EXHAUSTED`(공급자 잔액 소진)일 때 VALIDATED seed challenge와 선택 가능한 reading이 있고 planning KNOWLEDGE ≥ 1이어도 main task가 `CHALLENGE`도 `READ_CODE`도 아님 (`06` §5.3 1·2번 조건, T-3) |
| `AiBalanceMonitorTest` | 402 수신 → `aiStatus = BALANCE_EXHAUSTED`, 이후 AI endpoint 503 `AI_UNAVAILABLE`(`Retry-After: 3600`), `AI_BALANCE_LOW` 감사 이벤트 1회 / `AiBalanceCheckJob`이 잔액 ≥ `min-balance-usd`를 받으면 해제 (`17` §8.7) |

### 10.5 Eval

실제 모델 eval의 case 구조, 합격 기준, 실행 조건은 `17-ai-integration.md` §12가 기준이다. 이 문서의 테스트는 eval을 대신하지 않고, eval은 PR CI 게이트가 아니다. prompt·모델·가드를 바꾼 PR은 PR 템플릿 "Tests"에 eval 실행 결과 링크를 적는다. 러버덕 prompt(`rubber.duck`, `rubber.duck.summary`)와 `NoAnswerGuard`를 바꾼 PR도 같다 — RD-2(질문이 빈틈을 겨냥하는가)와 서술형 정답(§10.6.3 NG-07)은 결정적 테스트로 잡을 수 없고 eval만 본다.

### 10.6 러버덕 (AC-26, S3)

규칙은 `06` §9.5 RD-1~RD-7, API는 `05` §9.5~§9.10, AI 계약은 `17` §3.11·§3.12·§4.10·§4.11·§6.8이 기준이다. 턴(`RUBBER_DUCK`)과 정리(`RUBBER_DUCK_SUMMARY`)는 둘 다 SYNC이고 재시도가 없다.

#### 10.6.1 RD 규칙 → 테스트

| RD | 확인하는 것 | 테스트 | 계층 |
|---|---|---|---|
| RD-1 | AI 출력에 정답 단정·코드가 없다 | `NoAnswerGuardTest`(§10.6.3), `CodeLeakGuardTest`(`RUBBER_DUCK`의 `question`은 `DIRECTION` 이하 기준) | unit |
| RD-1 | 가드 위반이면 턴 502 `AI_OUTPUT_INVALID`, 턴 저장 없음 / 정리는 `summarySkippedReason = AI_OUTPUT_INVALID` | `RubberDuckServiceIntegrationTest` RS-08·RS-13 | integration |
| RD-2 | 질문이 설명의 빈틈·틀린 전제를 겨냥한다 | eval(§10.5). 결정적 테스트는 `targetsGap`이 응답·DB·다음 턴 prompt에 없는지만 본다(RS-05) | eval / integration |
| RD-3 | "모르겠다" 판정, 2턴 연속 → `suggestHint`, Hint Ladder 연결 | `RubberDuckPolicyTest` RDP-01~RDP-10, `RubberDuckStuckTest`(§10.6.4) | unit / integration |
| RD-4 | 턴 상한, 정리 1회, 턴 0개 종료 = `ABANDONED` | `RubberDuckPolicyTest` RDP-11~RDP-16, `RubberDuckServiceIntegrationTest` RS-06·RS-10·RS-14 | unit / integration |
| RD-5 | gaps 0개 + 턴 ≥ 3 → 설명 증거 | `RubberDuckPolicyTest` RDP-17~RDP-19, `SkillLevelRulesTest` RB-1~RB-6(§5.3) | unit |
| RD-6 | 저장·AI 전송 전 마스킹, private key 차단, 원문 미저장 | `SecretMaskingEndpointsTest`(§10.4), `RubberDuckServiceIntegrationTest` RS-07 | integration |
| RD-7 | skill 없는 세션은 학습 이벤트 없음 | `RubberDuckPolicyTest` RDP-20, `RubberDuckServiceIntegrationTest` RS-15 | unit / integration |

**`RubberDuckPolicyTest`** (unit, `rubberduck.domain.RubberDuckPolicy`). 설정은 `TestRuleSettings.defaults()`(`max-turns` 5, `stuck-turns-before-hint` 2, "모르겠다" 문구 목록·길이 임계값은 `06` §9.5 기본값). 케이스는 이 표가 기준이다(`06` §9.5에 vector 표가 없으므로 파일로 옮기지 않고 `@MethodSource`로 둔다).

| # | 입력 | 기대 |
|---|---|---|
| RDP-01 | 턴 텍스트 "모르겠어요" | 모르겠다 = true |
| RDP-02 | "잘 모르겠습니다" | true |
| RDP-03 | "IDK" (대문자) | true (대소문자 무시) |
| RDP-04 | "No idea." | true |
| RDP-05 | "트랜잭션은 커밋 시점에 반영됩니다" (문구 없음) | false |
| RDP-06 | 문구 포함, 공백을 뺀 길이 29자 | true (`< 30`) |
| RDP-07 | 문구 포함, 공백을 뺀 길이 30자 ("모르겠지만 …"으로 시작하는 설명) | false (경계) |
| RDP-08 | 최근 턴 판정 `[true, true]` | RD-3 발동 |
| RDP-09 | `[true, false, true]` | 발동 안 함 (사이 턴이 카운터를 0으로) |
| RDP-10 | `[true]` (턴 1개) | 발동 안 함 |
| RDP-11 | `IN_PROGRESS`, `turn_count` 4 | 턴 제출 가능 |
| RDP-12 | `IN_PROGRESS`, `turn_count` 5 (= max) | 제출 불가 → 409 `INVALID_STATE_TRANSITION` |
| RDP-13 | `COMPLETED` 또는 `ABANDONED`, `turn_count` 2 | 제출 불가 |
| RDP-14 | 종료 요청, `turn_count` 0 | `ABANDONED`, 정리 호출 없음 |
| RDP-15 | 종료 요청, `turn_count` ≥ 1 | 정리 호출 1회 → `COMPLETED` |
| RDP-16 | `COMPLETED` 세션에 종료 요청 | 불가 (정리는 세션당 1회) |
| RDP-17 | 정리 성공, `gaps` 0개, 턴 3 | 설명 증거 |
| RDP-18 | 정리 성공, `gaps` 0개, 턴 2 | 증거 아님 |
| RDP-19 | 정리 성공, `gaps` 1개, 턴 5 | 증거 아님 (이벤트는 남는다 — RDP-20) |
| RDP-20 | 정리 성공 + `skill` 있음 / 정리 성공 + `skill` null / 정리 실패 + `skill` 있음 | 이벤트 기록 / 기록 안 함(RD-7) / 기록 안 함. 기록 여부는 `gaps` 수와 무관하다 |

#### 10.6.2 `RubberDuckServiceIntegrationTest` (integration)

`05` §9.6~§9.10의 처리 순서를 따라 성공·실패·상태 전이를 본다. 시각은 `MutableClock`, AI는 `FakeAiProvider` fixture(§10.6.6).

| # | 상황 | 기대 |
|---|---|---|
| RS-01 | `POST /rubber-duck` `CONCEPT`, `skillCode` 생략(`conceptKey` 접두사 = 활성 skill code) | 201 `IN_PROGRESS`, `turnCount 0`, `skill` = 접두사 skill, `abandonedSessionId = null`, AI 호출 0, 학습 이벤트 0. 진행 중 학습 세션이 있으면 `learningSessionId` = 그 세션 |
| RS-02 | 같은 사용자의 `IN_PROGRESS` 세션 D1이 있을 때 새로 시작 | D1 `ABANDONED`(+`completed_at`), 응답 `abandonedSessionId = D1`, 새 세션 `IN_PROGRESS`. 사용자의 `IN_PROGRESS` 세션은 1개 (`05` §9.5 "동시 세션") |
| RS-03 | `CONCEPT` + `targetId` / `CODE_READING` + `conceptKey` / `CODE_READING` + `targetId` 없음 | 400 `MUTUALLY_EXCLUSIVE`(field `targetId`) / `MUTUALLY_EXCLUSIVE`(field `conceptKey`) / `ONE_OF_REQUIRED`(field `targetId`) |
| RS-04 | `skillCode` = 없는 code / `PROJECT_WORK`에 `skillCode` 생략 / `CODE_READING`의 `targetId`가 READ_CODE가 아닌 task | 400 `SKILL_CODE_UNKNOWN` / 201 `skill = null` / 400 `REFERENCE_NOT_FOUND` |
| RS-05 | 턴 1~5 제출 (`default` fixture) | 각 201, `turnNo` 1~5, `remainingTurns` 4~0, 세션 `turn_count` 5, 턴마다 `ai_call_id`, `RUBBER_DUCK` 호출 5, 학습 이벤트 0. 응답·DB·`receivedRequests(RUBBER_DUCK)`의 다음 턴 `conversation`에 `targetsGap` 값이 없음. 두 번째 턴부터 `conversation`에 직전 턴이 들어 있음 |
| RS-06 | 6번째 턴 | 409 `INVALID_STATE_TRANSITION`, AI 호출 0, 턴 행 변화 없음 (RD-4) |
| RS-07 | `explanation` 2,000자 / 2,001자 / private key 블록 포함 | 201 / 413 `CONTENT_TOO_LARGE` / 422 `SECRET_DETECTED_BLOCKED` — 뒤 둘은 턴 저장·AI 호출·`turn_count` 변화 없음 |
| RS-08 | `RUBBER_DUCK` fixture `timeout` / `provider-error` / `answer-phrase-violation` | 504 `AI_TIMEOUT` / 503 `AI_UNAVAILABLE` / 502 `AI_OUTPUT_INVALID`. 모두 턴 행 추가 없음, `turn_count` 그대로, `ai_call_log` 1행(`attempt_no` 1). 같은 설명을 새 키로 다시 내면 201 |
| RS-09 | 같은 `Idempotency-Key`·같은 body로 턴 재전송 | 저장된 201 재생(`Idempotent-Replayed: true`), `RUBBER_DUCK` 호출 1회, 턴 1행 |
| RS-10 | 턴 0개에서 `complete` | 200 `ABANDONED`, `summary = null`, `summarySkippedReason = null`, `aiMeta = null`, `RUBBER_DUCK_SUMMARY` 호출 0, 복습 카드·이벤트 0 |
| RS-11 | 턴 3개 + `two-gaps`로 `complete` | 200 `COMPLETED`, `createdReviewItemCount 2`, `review_item` 2행(`source_type = RUBBER_DUCK`, `source_id` = 세션, `origin = AI_GENERATED`, `review_type = EXPLAIN`, `prompt` = `reviewQuestion`, `concept_key` = gap `conceptKey`, `due_at = planDayStart(today + 1)`), 각 gap의 `reviewItemId`. `RUBBER_DUCK_COMPLETED` 1행, payload `{ sessionId, turns: 3, gapCount: 2, targetType, hintDisclosed: false }` |
| RS-12 | gap의 `concept_key`와 같은 `ACTIVE` 카드가 이미 있음 | 새 카드 없음, 기존 카드 `due_at = min(기존, planDayStart(today + 1))`, `createdReviewItemCount`에 세지 않음, 그 gap의 `reviewItemId` = 기존 카드 |
| RS-13 | 정리 fixture `timeout` / 예산 차단(§10.3 BI-05a) / `note-answer-violation` | 모두 200 `COMPLETED`, `summary = null`, `summarySkippedReason` = `AI_TIMEOUT` / `AI_BUDGET_EXCEEDED` / `AI_OUTPUT_INVALID`, 복습 카드·이벤트 0, 턴 기록 유지 |
| RS-14 | `COMPLETED` 세션에 `complete` / `turns` / `abandon` | 모두 409 `INVALID_STATE_TRANSITION`, `RUBBER_DUCK_SUMMARY` 호출 누계 1 (RD-4) |
| RS-15 | `skill = null` 세션(RS-04 두 번째)을 턴 3개 + `no-gaps`로 정리 | 200 `COMPLETED`, `summary` 저장, `RUBBER_DUCK_COMPLETED` 없음, 레벨 변화 없음 (RD-7) |
| RS-16 | `abandon` (턴 2개) | 200 `ABANDONED`, `turns` 2개 유지, AI 호출 0, 카드·이벤트 0. IK 헤더를 보내도 `idempotency_record` 없음(`05` §1.7) |
| RS-17 | `unknown-prefix` 정리(gap 2개 중 1개 접두사를 모름) | 그 gap만 버림(`SkillCodeGuard`), 카드 1장 |
| RS-18 | `CODE_READING` 세션의 턴 | `receivedRequests(RUBBER_DUCK)`의 `targetSummary` = reading의 저장소·경로·줄 범위·`question`. 서버에는 코드 본문이 없으므로 그 밖의 내용이 없다(`17` §3.11). `PROJECT_WORK` 세션은 `side_project.name` + `description`(마스킹본) |
| RS-19 | 세션 조회 `GET /rubber-duck/{id}` | `turns` `turnNo` ASC 전체, `userText` 마스킹본, `COMPLETED`이면 `summary`와 gap별 `reviewItemId`. AI `DISABLED` 상태에서도 200 |

- 턴 동시 제출과 `turn_no` 연속성은 §8.2 I-16, tx 경계는 §7 끝 문단, 소유권 404는 §9.2가 본다.

#### 10.6.3 `NoAnswerGuardTest` (unit, `17` §6.8)

`17` §6.8에는 vector 표가 없으므로 아래 표가 기준이다. **위반 문자열은 소스에 완성형 리터럴로 쓰지 않고 런타임에 조합한다**(`07` §11.3과 같은 방식): NA-2 표현은 `devpilot.ai.guards.no-answer-phrases` 설정 목록(`TestRuleSettings`)에서 꺼내 문장에 붙이고, NA-3 코드 줄은 `String.join("\n", …)`으로 만든다. 그래서 설정 목록이 바뀌어도 테스트를 고치지 않는다.

| # | operation · 필드 | 입력 | 기대 |
|---|---|---|---|
| NG-01 | `RUBBER_DUCK` `question` | 물음표로 끝나는 되묻기 ("폼 제출을 처리하는 메서드는 어디에 규칙을 두고 있나요?") | 통과, action 없음 |
| NG-02 | 〃 | NG-01 문장을 마침표로 끝냄 | NA-1 위반 → 502 `AI_OUTPUT_INVALID` (SYNC, 재시도 없음) |
| NG-03 | 〃 | 가운데 `?`, 끝은 마침표 ("왜 그럴까요? 다시 설명해 보세요.") | NA-1 위반 |
| NG-04 | 〃 | 목록 표현 + 물음표 문장 (예: `phrases[0] + ". 그렇다면 롤백은 언제 일어날까요?"`) | NA-2 위반 |
| NG-05 | 〃 | 목록의 **표현마다** 물음표 문장 1개 (목록 길이만큼 파라미터화) | 모두 NA-2 위반 |
| NG-06 | 〃 | 질문 형태지만 목록 표현 포함 ("`정답은` 무엇이라고 생각하세요?") | NA-2 위반 — 문자열 검사라 형태를 보지 않는다 |
| NG-07 | 〃 | 목록 표현 없는 서술형 정답 + 물음표 ("이 경우에는 롤백되지 않는데, 왜 그럴까요?") | **통과** — `17` §6.8 "한계". eval이 본다 |
| NG-08 | 〃 | `CodeDetector`가 코드 줄로 판정하는 줄 2개 + 물음표 문장 | 통과 (3줄 미만) |
| NG-09 | 〃 | 코드 줄 3개 + 물음표 문장 | NA-3 위반 |
| NG-10 | 〃 | 코드 블록(백틱 3개로 감싼 1줄) + 물음표 문장 | NA-3 위반 |
| NG-11 | `RUBBER_DUCK_SUMMARY` `gaps[1].whatWasMissed` | gap 2개, 두 번째 gap에 목록 표현 | NA-4: `gaps[1]`만 제거, `gaps[0]` 유지, 결과는 성공. `guardActions`에 NoAnswerGuard `REMOVED` 1개(`detail = "gaps[1]"`, 본문 없음) |
| NG-12 | `RUBBER_DUCK_SUMMARY` `gaps[0].whyItMatters` | gap 1개, 목록 표현 | NA-4: 제거 → `gaps = []`, 결과는 성공 |
| NG-13 | `RUBBER_DUCK_SUMMARY` `overallNote` / `gaps[0].reviewQuestion` | 목록 표현 | NA-2 위반 → 정리 실패(`summarySkippedReason = AI_OUTPUT_INVALID`). gap 제거(NA-4)는 `whatWasMissed`·`whyItMatters`에만 적용한다 |
| NG-14 | `RUBBER_DUCK_SUMMARY` `whatWasMissed` | 코드 블록 | NA-3 위반 → 정리 실패 (코드는 gap 제거 대상이 아니다) |
| NG-15 | `RUBBER_DUCK_SUMMARY` 전체 | 목록 표현·코드 없음 | 통과, action 없음 |

#### 10.6.4 `RubberDuckStuckTest` (integration, RD-3)

"모르겠다" 판정은 **서버 결정적 규칙**(`06` §9.5 — 공백 제거 길이 < `dont-know-max-chars` 30이고 `dont-know-phrases` 포함)이다. AI 출력에 없으므로 fixture는 모든 턴 `default`이고, 판정은 설명 텍스트만으로 갈린다("모르겠어요", "잘 모르겠습니다" = 참)를 쓴다 — AI 판정(`learnerStuck`)과 서버 문구 판정이 같은 결론을 내는 입력이다.

| # | 턴 순서 (설명) | 기대 |
|---|---|---|
| SK-1 | 일반 설명 → "모르겠어요" | 두 번째 턴 `suggestHint = false` (1회) |
| SK-2 | SK-1에 이어 "잘 모르겠습니다" | 세 번째 턴 `suggestHint = true`, `GET` 세션 `suggestHint = true`, `turns[].learnerStuck` = `[false, true, true]` |
| SK-3 | "모르겠어요" → 일반 설명 → "모르겠어요" | 세 턴 모두 `suggestHint = false` (사이 턴이 끼면 0부터) |
| SK-4 | 첫 턴부터 "모르겠어요" | `suggestHint = false` (턴 수 < 2) |
| SK-5 | SK-2 이후 `suggestHint = true`인 채 턴 계속 | 상한(5)까지 제출 가능. RD-3은 턴을 막지 않는다 |
| SK-6 | `CHALLENGE` 대상(attempt A, 자기설명 없음) 세션에서 SK-2 → `POST /challenge-attempts/{A}/hints` `{requestedLevel: QUESTION_ONLY}` | 409 `SELF_EXPLANATION_REQUIRED`가 아니라 hint 공개(러버덕 턴 ≥ 1 = 자기설명, `06` §9.5 "HL-2 선행 조건"), `hint_disclosure` 1행, `HINT_DISCLOSED` 1행. 이어서 `complete`하면 `RUBBER_DUCK_COMPLETED.hintDisclosed = true` |

#### 10.6.5 `StaleRubberDuckJobIntegrationTest` (integration)

`03` §6: 매시 25분(`0 25 * * * *`), 조건 `status = IN_PROGRESS and started_at < now − devpilot.rubberduck.stale-after`(24h). 설정은 기본값으로만 본다(바꾸면 context 캐시가 깨진다, §3.3). cron을 기다리지 않고 job 메서드를 직접 호출한다(E2E-04 13번과 같은 방식). 기준 시각은 마지막 턴이 아니라 세션 `started_at`이고, 비교가 `<`(엄격)이므로 정확히 24시간인 세션은 아직 방치가 아니다.

| # | 상황 (`started_at = T0`) | 기대 |
|---|---|---|
| SR-1 | clock `T0 + 23:59:59` | `IN_PROGRESS` 유지 |
| SR-2 | clock `T0 + 24:00:00` (`started_at = now − 24h`, 경계) | `IN_PROGRESS` 유지 |
| SR-3 | clock `T0 + 24:00:01` | `ABANDONED`, `completed_at = now`, `RUBBER_DUCK_SUMMARY` 호출 0, 복습 카드·학습 이벤트 0, 턴 기록 유지 |
| SR-4 | `T0 + 23:00:00`에 턴을 1개 더 낸 세션, clock `T0 + 24:00:01` | `ABANDONED` — 최근 턴이 아니라 `started_at` 기준 |
| SR-5 | `COMPLETED`·`ABANDONED` 세션, `started_at`이 오래됨 | 변화 없음 |
| SR-6 | 사용자 2명의 방치 세션 | 둘 다 `ABANDONED`. 사용자 단위 트랜잭션(`03` §6), 시작·처리 건수·소요시간 INFO 1줄 |

#### 10.6.6 `FakeAiProvider` fixture (러버덕)

`backend/src/test/resources/ai-fixtures/` 아래(`17` §12.2 형식). `-violation` 파일은 JSON 형식만 검사한다(§10.1). `conceptKey`는 테스트 seed skill code를 접두사로 쓴다.

| operation | case | 내용 |
|---|---|---|
| `RUBBER_DUCK` | `default` | 물음표로 끝나는 질문, `targetsGap` 있음 ("모르겠다" 판정은 출력에 없다 — `06` §9.5 서버 규칙) |
| `RUBBER_DUCK` | `timeout`, `provider-error` | `simulate: TIMEOUT` / `PROVIDER_ERROR` |
| `RUBBER_DUCK` | `no-question-mark-violation`, `answer-phrase-violation`, `code-lines-violation` | NA-1 / NA-2 / NA-3 위반 |
| `RUBBER_DUCK_SUMMARY` | `default` | gap 1, confirmed 1 |
| `RUBBER_DUCK_SUMMARY` | `two-gaps` | gap 2 (서로 다른 `conceptKey`), confirmed 1 |
| `RUBBER_DUCK_SUMMARY` | `no-gaps` | `gaps: []`, confirmed 2 (RD-5) |
| `RUBBER_DUCK_SUMMARY` | `unknown-prefix` | gap 2 중 1개의 접두사가 catalog에 없음 (`SkillCodeGuard`) |
| `RUBBER_DUCK_SUMMARY` | `gap-answer-violation`, `note-answer-violation` | NA-4(gap 1개 제거) / NA-2(`overallNote`, 정리 실패) |
| `RUBBER_DUCK_SUMMARY` | `timeout` | `simulate: TIMEOUT` |

- main의 `ai-fixtures/RUBBER_DUCK/default.json`, `RUBBER_DUCK_SUMMARY/default.json`은 `local`·`demo` profile용이다(`17` §12.2). test fixture와 따로 둔다.

### 10.7 코드 읽기 · 사이드 프로젝트 (AC-27 S1, AC-28 S3)

`READ_CODE`의 제안(AI 상태)과 완료 조건(RC-1, 러버덕)이 AI에 묶여 있어 이 절에 둔다. 아래 테스트 대부분은 AI를 호출하지 않는다.

| 테스트 | 계층 | 확인 |
|---|---|---|
| `ContentValidatorTest` | unit | `19` §4.1 CV-80~CV-87(코드 읽기)·CV-62(시간 제한)·CV-88~CV-89(`whyItMatters`)·CV-90~CV-96(오늘의 팁)·CV-100~CV-106(용어)·CV-110~CV-113(과제 체크리스트) 각각 1케이스 이상. 오류 주입 목록은 `19` §4.3 표(`repo` 미실재 → CV-84, `path`의 `..`·내림차순 `lines` → CV-85 2건, 양수 아닌 `lines` → CV-85, 없는 skill code·40자 미만 `question` → CV-86, key 중복 → CV-83, `pinnedCommit: null` → CV-82 WARN). 은퇴 규칙(`19` §8.2): `retired: true`인데 key가 `retired.readingKeys`에 없음 → CV-83, `retired.readingKeys`에 있는데 reading이 지워짐 → CV-83, 은퇴하지 않은 reading의 key가 `retired.readingKeys`에 있음 → CV-83. 은퇴한 reading만 남은 저장소는 CV-87 WARN 없음 |
| `CuratedReadingRegistryTest` | unit | 테스트 콘텐츠 적재, key 조회, 없는 key → empty, 비활성 skill code는 `skills`에서 뺀다(`05` §19.7). `retired: true` reading도 key로 조회되고 `retired = true`다. 제안 후보 목록에는 없다(`06` §5.3) |
| `ReadingControllerTest` | web slice | `readingKey` 패턴 위반 → 400 `VALIDATION_FAILED`(field `readingKey`, `Pattern`), 형식은 맞지만 없는 key → 404 `RESOURCE_NOT_FOUND`, 토큰 없음 → 401. **응답 JSON의 필드 집합이 `CuratedReadingView`·`CuratedRepoView` 정의와 정확히 같다 — 코드 본문을 담을 필드가 없다.** `startLine ≤ endLine`, `pinnedCommit`·`cloneHint` 포함 |
| `NoOutboundFetchTest` | integration (`@IntegrationTest`) | `OutboundRequestRecorder`(§4.1)를 설치한 상태에서 `GET /readings/{key}`, `POST /side-projects`·`PATCH /side-projects/{id}`(`repoUrl = https://repo.example.invalid/…`), `POST /onboarding`(`sideProject.repoUrl` 포함), `CODE_READING` 러버덕 시작 + 턴 1개를 호출 → `nonLoopbackRequests()`가 **0건**. 정적 보장은 ArchUnit ARCH-19(HTTP 클라이언트는 `integration.ai.deepseek`에만) |
| `SideProjectServiceIntegrationTest` | integration | 아래 SP 표 |
| `OnboardingServiceIntegrationTest` (AC-11 확장) | integration | `sideProject` 있음 → `side_project` 1행 `ACTIVE` + 응답 `sideProject` / `null` → 행 없음·응답 `null`, 이후 `POST /today/generate`에서 PROJECT_TASK 없음(SP-1) / `sideProject.repoUrl` 형식 오류 → 400 `URL`, 온보딩 전체 롤백(`onboarding_completed_at` null 유지) / `runDiagnostic = true` + `selfAssessments` 비어 있지 않음 → 400 `MUTUALLY_EXCLUSIVE` / `runDiagnostic = false` + 빈 목록 → 400 `ONE_OF_REQUIRED` / `runDiagnostic = true` → 모든 `self_assessed_level = null`, `suggestedDiagnostics` ≤ 5개(category당 1개) |
| `TodayPlanServiceIntegrationTest` (RC-1) | integration | READ_CODE task `IN_PROGRESS → COMPLETED`: 그 task를 대상으로 한 러버덕 세션이 없음 → 409 `INVALID_STATE_TRANSITION` / `ABANDONED` 세션만 있음 → 409 / 다른 task 대상 `COMPLETED` 세션만 있음 → 409 / `COMPLETED` 세션 1개(정리 실패로 `summarySkippedReason`이 있어도) → 200. 다른 task 유형의 완료는 러버덕과 무관. `CODE_READING` 세션 `complete`만으로는 task 상태가 바뀌지 않는다(`05` §9.8 7번). **읽기 평가** `readingFeedback`(`05` §8.4): READ_CODE `COMPLETED` + `HELPFUL` → 200, `reading_feedback = 'HELPFUL'` / 생략 → 200, `reading_feedback = null` / `status = DEFERRED` + `readingFeedback` → 400 `VALIDATION_FAILED`(`VALUE_NOT_ALLOWED`, field `readingFeedback`), 상태 변화 없음 / `EXPLAIN` task `COMPLETED` + `readingFeedback` → 400 같은 코드 / `readingFeedback = "GREAT"` → 400 `UNKNOWN_ENUM_VALUE` / RC-1 미충족 + `readingFeedback` → 409 `INVALID_STATE_TRANSITION`(평가 저장 없음). 평가 저장 전후로 skill state·learning event·planner 입력(`score_breakdown`)이 같다 |

`SideProjectServiceIntegrationTest` (`05` §19.2~§19.6):

| # | 요청 | 기대 |
|---|---|---|
| SP-T1 | `POST /side-projects` 전 필드 | 201 `ACTIVE`, `version 0`. `description`·`repoUrl`·`stack`이 빈 문자열이면 `null`로 저장 |
| SP-T2 | `repoUrl` = `ftp://…` / `not a url` / host 없음 / 501자 | 400 `VALIDATION_FAILED`(field `repoUrl`, `URL`) / 같음 / 같음 / `Size` |
| SP-T3 | `name` 101자 / `description` 1001자 / `stack` 301자 | 400 |
| SP-T4 | `PATCH` 필드 생략 | 변경 없음. 실제로 바뀐 필드가 없으면 `version`·`updated_at` 그대로 |
| SP-T5 | `PATCH` `description: ""` / `name: "  "` | `description = null` / 400 `NOT_BLANK_IF_PRESENT` |
| SP-T6 | `PATCH` 이전 `version` | 409 `CONCURRENT_MODIFICATION` |
| SP-T7 | `PATCH` `status`: `ACTIVE → PAUSED → ACTIVE`, `ACTIVE → DONE` | 200. 허용 전이 전체는 `05` §19.5 기준 |
| SP-T8 | `GET /side-projects?status=ACTIVE` / 모르는 status | `updatedAt` DESC, `id` DESC, ACTIVE만 / 400 `UNKNOWN_ENUM_VALUE` |
| SP-T9 | PROJECT_TASK task(`side_project_id = P`)와 `PROJECT_WORK` 러버덕 세션(`target_id = P`)이 있는 상태에서 `DELETE /side-projects/{P}` | 204. task는 남고 `side_project_id = null`(`TaskView.sideProjectId = null`), 러버덕 세션은 남고 `targetTitle = null`. `coach_review.side_project_id`는 §8.3(JDBC, S4부터 API) |
| SP-T10 | 삭제한 id로 다시 `DELETE` / `GET` | 404 `RESOURCE_NOT_FOUND` (멱등 204 아님) |
| SP-T11 | `ACTIVE` 2개(P1 먼저, P2 나중 수정) → `POST /today/generate`로 PROJECT_TASK 생성 | task `side_project_id = P2`, 제목에 P2 이름 (SP-2·SP-3) |

---
## 11. API E2E flows

- 실행 환경: `@IntegrationTest`(§3.3 — `@SpringBootTest` + `@AutoConfigureMockMvc`) + Testcontainers + `TestJwksServer` + `FakeAiProvider` + `MutableClock`. **MockMvc로 endpoint를 순서대로 호출한다** — 서버 포트를 띄우지 않으므로 나머지 통합 테스트와 context를 공유한다. 비동기 task는 실제 `aiTaskExecutor`에서 실행되고 Awaitility로 기다린다(최대 10초, 간격 100ms). Awaitility는 `spring-boot-starter-test`가 전이로 가져온다(§2).
- 공통 시작 시각: `2026-10-05T10:00:00Z` (KST 19:00, plan-day `2026-10-05`, `dayStartHour = 4`).
- 모든 POST는 새 `Idempotency-Key`(E2E-06 제외). 응답 `status`, `code`, 핵심 필드와 DB 상태(사용자 범위 SQL)를 함께 확인한다.
- 성공 응답 status(200/201/202/204)는 `05-api-spec.md`의 endpoint 정의를 따른다. 아래 표에 적은 status는 `05-api-spec.md`에 명시된 것만이다.

### E2E-01 온보딩 → Today → 세션 완료 (`OnboardingToTodayFlowTest`, AC-02, AC-11, AC-17)

| # | 요청 | 기대 |
|---|---|---|
| 1 | `GET /me` (첫 요청) | 200, `onboardingCompleted = false`, `app_user` 1행, `AUTH_USER_PROVISIONED` |
| 2 | `GET /today` | 409 `ONBOARDING_REQUIRED` |
| 3 | `POST /onboarding` — `runDiagnostic = false` + 카테고리 자기평가 13개, `sideProject = null`(건너뛰기), `dayStartHour = 4`, 평일 45/주말 240, 목표일 `2027-04-01` | 201, `activePlan.planVersion = 1`, `sideProject = null` (사이드 프로젝트 생성 경로는 §10.7 `OnboardingServiceIntegrationTest`) |
| 4 | `POST /onboarding` 다시 (새 키) | 409 `ONBOARDING_ALREADY_COMPLETED` |
| 5 | `GET /plans/active` | milestone 1개 이상, `plan_skill_target` = role target 복사(`adjustment = ROLE_DEFAULT`) |
| 6 | DB: `review_item` | seed 카드 10장 복사, 정렬 앞 5장 `due_at = 2026-10-04T19:00:00Z`, 다음 5장 `2026-10-05T19:00:00Z` (`06` §6.3) |
| 7 | `GET /today` | 404 `TODAY_NOT_GENERATED` |
| 8 | `POST /today/generate` `{availableMinutes: 45, energyLevel: NORMAL}` | main task 정확히 1개, `reasons` 1~3개, REVIEW task `estimatedMinutes = 8`(dueCount 5 — `06` §6.5 `due_at < planDayStart(today + 1)` 기준으로 첫 묶음 5장만 포함 → `min(ceilDiv(15, 2) = 8, 11, 40)`), main `estimatedMinutes ≤ floorDiv(37 × 11000, 10000) = 40` |
| 9 | `PATCH /today/tasks/{mainId}` `{status: IN_PROGRESS, version}` | 200 |
| 10 | `POST /learning-sessions` `{learningTaskId: mainId}` | `IN_PROGRESS`, `SESSION_STARTED` 이벤트 |
| 11 | clock +40분 → `POST /learning-sessions/{id}/complete` `{actualMinutes: 35}` | `COMPLETED`, `SESSION_COMPLETED` 이벤트 `plan_date = 2026-10-05` |
| 12 | `PATCH /today/tasks/{mainId}` `{status: COMPLETED, version}` | 200, `completed_at` 설정 |
| 13 | `POST /today/generate` `{force: false}` | 409 `TODAY_ALREADY_COMPLETED` |
| 14 | clock `2026-10-05T18:59:59Z`(KST 03:59:59) → `GET /today` | 같은 plan-day 계획 반환 |
| 15 | clock `2026-10-05T19:00:00Z`(KST 04:00) → `GET /today` | 404 `TODAY_NOT_GENERATED` (새 plan-day) |

### E2E-02 Seed 복습 답변 → 간격 (`SeedReviewFlowTest`, AC-05, AC-09)

| # | 요청 | 기대 |
|---|---|---|
| 1 | 온보딩 완료 사용자 (`TestApi.onboard`) | — |
| 2 | `GET /reviews/due?limit=20` | 5장(E2E-01 8번과 같은 기준), 각 항목에 `prompt`, `expectedAnswer`, `rubric` 포함 |
| 3 | 카드 X: `POST /reviews/{X}/answer` `{selfRating: GOOD, hintLevel: SELF_EXPLAIN, evaluate: false, answerText}` | `finalRating = GOOD`, `adjustedBy = []`, `intervalBefore = 1`, `intervalAfter = 2`, 다음 due `2026-10-06T19:00:00Z` |
| 4 | 카드 Y: `{selfRating: EASY, hintLevel: CONCEPT_HINT}` | `finalRating = HARD`, `adjustedBy = [HINT_CAP_HARD]`, `intervalAfter = 2` |
| 5 | 카드 Z: `{selfRating: GOOD, hintLevel: FULL_EXAMPLE}` | `finalRating = AGAIN`, `adjustedBy = [HINT_CAP_AGAIN]`, `intervalAfter = 1`, `consecutive_failures = 1` |
| 6 | `GET /reviews/due` | 2장 |
| 7 | DB | `review_answer` 3행, `REVIEW_ANSWERED` 이벤트 3행, 각 카드 skill의 `knowledge_level` 0→1 (`K1_ANY_EVENT`) 및 `skill_state_change` 행 (카드가 같은 skill이면 cooldown으로 1행) |
| 8 | 3번 요청을 같은 `Idempotency-Key`·같은 body로 재전송 | 같은 body, 헤더 `Idempotent-Replayed: true`, `review_answer` 3행 유지 |

### E2E-03 Challenge → hint → 제출 → 비동기 평가 → 복습 항목 → skill 변화 (`ChallengeAttemptFlowTest`, AC-04, AC-16, AC-09)

전제: 테스트 seed challenge C (`VALIDATED`, difficulty 2, skill S 1개, `hints_json` 1~3단계, rubric **R1 5000 IMPLEMENTATION / R2 5000 EXPLANATION** — `src/test/resources/content/challenges`의 `PRACTICE.SPRING.TRANSACTION.L2.001`). `CHALLENGE_EVALUATE` fixture는 `default`(R1만 met), `all-met`, `none-met`, `timeout`이다(§4.1, `17` §12.2).

| # | 요청 | 기대 |
|---|---|---|
| 1 | `GET /challenges/{C}` | `rubric`, `expectedConcepts` 없음 |
| 2 | `POST /challenges/{C}/attempts` | 201 `STARTED`, `CHALLENGE_STARTED`, S의 K 0→1 |
| 3 | `POST /challenge-attempts/{A}/hints` `{requestedLevel: CONCEPT_HINT}` | 409 `SELF_EXPLANATION_REQUIRED` |
| 4 | `POST /challenge-attempts/{A}/self-explanation` `{text}` | `SELF_EXPLANATION_SUBMITTED`, E 0→1 |
| 5 | `POST …/hints` `{requestedLevel: CONCEPT_HINT}` | 내용 = `hints_json.CONCEPT_HINT`, `maxHintLevel = CONCEPT_HINT`, `HINT_DISCLOSED.skippedLevels = [QUESTION_ONLY]`, AI 호출 0 |
| 6 | `POST …/hints` `{requestedLevel: QUESTION_ONLY}` | CONCEPT_HINT 내용 반환, AI 호출 0, `hint_disclosure` 1행 유지 (HL-1) |
| 7 | `fakeAiProvider.use(CHALLENGE_EVALUATE, "default")` → `POST …/submissions` `{code, language: JAVA, answerText}` | 202, submission `PENDING`, `CHALLENGE_SUBMITTED`, I 0→1 |
| 8 | Awaitility: `GET /challenge-attempts/{A}` | submission `COMPLETED`, `evaluatedOutcome = PARTIAL`(coverage **5000** — R1만 met), attempt `EVALUATED`, `outcome = PARTIAL`, `rubricCoverageBp = 5000`, `explanationCoverageBp = 0`(R2가 유일한 EXPLANATION 항목이고 unmet) |
| 9 | `GET /challenges/{C}` | 이제 `rubric`, `expectedConcepts` 포함 |
| 10 | DB `review_item` | `concept_key = CHALLENGE:{C}`, `review_type = EXPLAIN`, `source_type = CHALLENGE_ATTEMPT`, `due_at = 2026-10-05T19:00:00Z` |
| 11 | DB `skill_state_change` | S: K 0→1 `K1_ANY_EVENT`, E 0→1 `E1_ANY_EXPLANATION`, I 0→1 `I1_ATTEMPTED` 3행만 (E2는 coverage 0이라 없음) |
| 12 | clock +25시간, `fakeAiProvider.use(CHALLENGE_EVALUATE, "all-met")` → `POST …/submissions` | 202, attempt `SUBMITTED`, `submission_count = 2` |
| 13 | Awaitility | `evaluatedOutcome = CORRECT`, `outcome = SOLVED_WITH_HINTS`(maxHint CONCEPT_HINT), `CHALLENGE_EVALUATED`, I 1→2 `I2_SOLVED_GUIDED`, E 1→2 `E2_PARTIAL`(`explanationCoverageBp = 10000`, 독립 아님 → E3 아님), 새 review item 없음(`06` §8.3 조건 미충족) |
| 14 | `fakeAiProvider.use(CHALLENGE_EVALUATE, "timeout")` → 3번째 제출 | Awaitility: submission `FAILED`, `failureCode = AI_TIMEOUT`, attempt `SUBMITTED` 유지 |
| 15 | `fakeAiProvider.use(CHALLENGE_EVALUATE, "all-met")` → `POST …/submissions/3/retry` | 202 → `COMPLETED` |
| 16 | 완료된 제출에 `POST …/submissions/2/retry` | 409 `AI_TASK_NOT_RETRYABLE` |

### E2E-04 Coach review → 응답 → 완료 → thinking observation (`CoachReviewFlowTest`, AC-06, AC-14, AC-19)

fixture `coach-review/two-findings`: F1(`RESOURCE_LIFECYCLE`, `BUG`, `mentionedByUser = true`, skill S1), F2(`EXCEPTION_STRATEGY`, `RISK`, `mentionedByUser = false`, skill S2), `incorrectClaims = [CONCURRENCY]`.

| # | 요청 | 기대 |
|---|---|---|
| 1 | `POST /coach/reviews` `{contentType: CODE, language: JAVA, content(런타임 조합 fake secret 1개 포함), confidentialConsent: true, userSelfReview: "스트림을 닫는지 모르겠다", context}` | 202 `{status: PENDING, maskedSecretCount: 1}` |
| 2 | 같은 요청 `confidentialConsent: false` (새 키) | 400 `VALIDATION_FAILED` |
| 3 | Awaitility `GET /coach/reviews/{R}` | `COMPLETED`, findings 2개, 수정 코드 필드 없음, `verificationStatus`·`confidence` 존재 |
| 4 | DB | `content`에 원문 secret 없음, `content_retention_until = created_at + 30일`, `thinking_pattern_observation(CONCURRENCY, INCORRECT_CLAIM)` 1행 |
| 5 | `fakeAiProvider.use(COACH_RESPONSE_FEEDBACK, "identified")` → `POST /coach/reviews/{R}/findings/{F2}/responses` `{text}` | F2 `USER_RESPONDED`, `userIdentifiedIssue = true` |
| 6 | `fakeAiProvider.use(HINT_GENERATE, "question-only")` → `POST …/findings/{F2}/hints` `{requestedLevel: QUESTION_ONLY}` | hint 반환, `HINT_DISCLOSED` |
| 7 | `PATCH …/findings/{F1}` `{status: RESOLVED, version}` | 200 |
| 8 | `POST /coach/reviews/{R}/complete` | `closedAt` 설정 |
| 9 | DB | F1 `discovered_by = MENTIONED_UNPROMPTED`, F2 `FOUND_AFTER_HINT`; observation `RESOURCE_LIFECYCLE/MENTIONED_UNPROMPTED`, `EXCEPTION_STRATEGY/FOUND_AFTER_HINT`; `COACH_FINDING_CLOSED` 2행, `COACH_REVIEW_COMPLETED`; `skill_state_change`에 S1·S2의 DEBUGGING 0→1 `D1_ANY_REVIEW` 포함 |
| 9a | DB `review_item` (`06` §9.4) | F2(RISK, `FOUND_AFTER_HINT`, skill S2) → `concept_key = COACH:{S2 code}:EXCEPTION_STRATEGY`, `review_type = EXPLAIN`, `source_type = COACH_FINDING`, `source_id = F2`, `prompt` = F2 `learning_question`, `due_at = 2026-10-05T19:00:00Z`. F1(`MENTIONED_UNPROMPTED`)은 항목 없음 |
| 10 | `PATCH …/findings/{F2}` `{status: DISMISSED, version}` | 409 `REVIEW_ALREADY_CLOSED` |
| 11 | `DELETE /coach/reviews/{R}/content` | 성공, `content = null`, `content_purged_at` 설정, findings 유지 |
| 12 | `POST /coach/reviews` content에 `-----BEGIN OPENSSH PRIVATE KEY-----` 블록 | 422 `SECRET_DETECTED_BLOCKED`, 새 행 0, `ai_call_log` 증가 0 <!-- gitleaks:allow --> |
| 13 | clock +30일 1초 → `CoachContentPurgeJob` 메서드 직접 호출 (다른 review R2) | R2 `content = null` |

### E2E-05 Replan preview → commit → 새 version (`ReplanFlowTest`, AC-01, AC-03, AC-24)

| # | 요청 | 기대 |
|---|---|---|
| 1 | 온보딩 완료, 활성 plan P1(`planVersion 1`). **목표일을 가깝게 잡아** 시작부터 risk HIGH인 상태로 둔다 — `effective < requiredMust ≤ floorDiv(effective × 12_500, 10_000)`(`06` §4.3) | — |
| 2 | `POST /plans/{P1}/replan/preview` (**`Idempotency-Key` 없음**) — milestone 구성을 바꾸는 편집안 | 200, `riskLevel = HIGH`, `deferSuggestions` 1개 이상, `riskAfterSuggestions` 존재. DB plan 1행 유지 |
| 3 | `POST /plans/{P1}/replan` `{version, reason, milestones, acceptedDeferrals: [첫 제안 skill]}` | 새 plan P2 `planVersion = 2`, `supersedesPlanId = P1` |
| 4 | `GET /plans/active` / `GET /plans/{P1}` / `GET /plans` | P2 / `SUPERSEDED` + `supersededAt` / 2개 |
| 5 | DB | P2 `plan_skill_target`에서 수락한 skill `deferred = true`, `adjustment = DEFERRED`; `PLAN_REPLANNED` 이벤트 `{fromVersion: 1, toVersion: 2}`; 감사 이벤트 `PLAN_REPLANNED`; `plan_progress_snapshot(P2, 2026-10-05)` |
| 6 | 과거 `daily_plan` | `learning_plan_id = P1` 유지 |
| 7 | `POST /plans/{P1}/replan` (새 키) | 409 `PLAN_NOT_ACTIVE` |
| 8 | `PATCH /plans/{P2}/milestones/{M}` `{status: IN_PROGRESS, version: <이전 값>}` | 409 `CONCURRENT_MODIFICATION` |
| 8a | `POST /plans/{P2}/replan` 같은 skill을 `acceptedDeferrals`와 `restoredDeferrals`에 동시에 | 400 `VALIDATION_FAILED`, 새 plan 없음 |
| 8b | `POST /plans/{P2}/replan` `{version, reason, milestones, restoredDeferrals: [3번에서 defer한 skill]}` | P3 `planVersion = 3`, 해당 skill `deferred = false`, `adjustment = USER_EDITED` (`06` §11.2) |
| 9 | 동시 replan은 `ReplanConcurrencyIntegrationTest`(§8.2) | — |

- risk는 **milestone 기간이 아니라** MUST `plan_skill_target`의 `requiredMust`와 목표일까지의 `effective` 예산으로 계산한다(`06` §4.1~§4.3). milestone을 늘리거나 줄여도 risk는 그대로다 — 그래서 이 흐름은 1번에서 목표일로 risk를 만들고, 2번에서는 편집안이 제안을 만드는지만 본다.

### E2E-06 Idempotency (`IdempotencyTest`, AC-23)

| # | 요청 | 기대 |
|---|---|---|
| 1 | `POST /today/generate` 헤더 없음 | 400 `IDEMPOTENCY_KEY_REQUIRED` |
| 2 | `POST /today/generate` 키 `abc` (8자 미만) | 400 `VALIDATION_FAILED` |
| 3 | `POST /challenges/generate` 키 K1, body B1 | 202, Awaitility로 생성 완료, `CHALLENGE_GENERATE` 호출 1회 |
| 4 | K1 + B1 재전송 | 202, 같은 body, `Idempotent-Replayed: true`, AI 호출 여전히 1회, challenge 행 1개 |
| 5 | K1 + B2 (body 필드 하나 다름) | 422 `IDEMPOTENCY_KEY_REUSED` |
| 6 | K1 + B1, JSON 키 순서·공백만 다름 | 재생 (canonical hash, `03` §5.4) |
| 7 | `fakeAiProvider.blockUntilReleased(HINT_GENERATE)`로 붙잡은 상태에서 같은 키 K2로 동기 hint 요청 2개 동시 | 1개 200, 1개 409 `IDEMPOTENCY_IN_PROGRESS` |
| 8 | self-explanation 없이 hint 요청 키 K3 → 409 `SELF_EXPLANATION_REQUIRED` → self-explanation 제출 → 같은 K3로 같은 hint 요청 | 두 번째는 재생이 아니라 실행되어 200 (실패 시 record 삭제) |
| 9 | 사용자 B가 K1 + B1 | 재생 아님, B의 요청으로 202 |
| 10 | clock +24시간 1초 → `RetentionCleanupJob` 직접 호출 → K1 + B1 | 새로 실행(AI 호출 2회) |
| 11 | `PATCH`/`PUT`/`DELETE` 요청에 `Idempotency-Key` 헤더 포함 | 헤더 무시, 정상 처리 |
| 12 | `POST /plans/{planId}/replan/preview`에 `Idempotency-Key` 헤더 포함, 같은 키로 2회 | 둘 다 200으로 새로 계산, `idempotency_record` 행 없음 (`05` §1.7) |

### E2E-07 AI 비활성 (`AiDisabledFlowTest`, AC-12)

`@IntegrationTest` + `@TestPropertySource(properties = "devpilot.ai.provider=disabled")` (이 테스트 전용 context). AI를 쓸 수 없을 때 자기채점 같은 대체 경로는 없다(`17-ai-integration.md` §3.10).

| # | 요청 | 기대 |
|---|---|---|
| 1 | `GET /me` | 200, `aiStatus = DISABLED` (`FakeAiProvider`는 조건부라 등록되지 않고 `DisabledAiProvider`가 쓰인다, §3.3) |
| 2 | `POST /onboarding`, `GET /plans/active`, `GET /dashboard` | 모두 정상 |
| 3 | `POST /today/generate` (테스트 seed에 VALIDATED challenge와 reading 있음) | 정상, main task `taskType ∉ {CHALLENGE, READ_CODE}` (`06` §5.3 1·2번) |
| 4 | `POST /reviews/{id}/answer` `{evaluate: true, answerText}` | 200, `evaluatedOutcome = NOT_EVALUATED`, `evaluationSkippedReason = AI_UNAVAILABLE`, 간격 계산 정상 |
| 5 | seed challenge attempt → self-explanation → hint `QUESTION_ONLY`~`DIRECTION` | 정상 (`hints_json`, AI 호출 없음) |
| 6 | hint `PSEUDOCODE` + `acknowledgeEvidenceImpact: true` | 503 `AI_UNAVAILABLE`, `hint_disclosure` 행 없음 |
| 7 | `POST /challenge-attempts/{id}/submissions` | 503 `AI_UNAVAILABLE`, `challenge_submission` 행 없음, attempt 상태·`submission_count` 변화 없음 |
| 8 | `POST /challenges/generate` | 503 `AI_UNAVAILABLE`, `challenge` 행 없음 |
| 9 | `POST /coach/reviews` | 503 `AI_UNAVAILABLE`, `coach_review` 행 없음 |
| 10 | 7~9와 같은 `Idempotency-Key`로 재요청 | 재생이 아니라 다시 503 (실패 응답은 저장하지 않음, `05` §1.7) |
| 11 | `POST /rubber-duck` `{targetType: CONCEPT, conceptKey}` | 201 — 시작은 AI를 부르지 않아 차단 검사도 하지 않는다(`05` §9.6). 시작 버튼을 막는 것은 클라이언트다 |
| 12 | `POST /rubber-duck/{id}/turns` | 503 `AI_UNAVAILABLE`, `rubber_duck_turn` 행 없음, `turn_count` 0 |
| 13 | `POST /rubber-duck/{id}/complete` (턴 0개) / `GET /rubber-duck/{id}` | 200 `ABANDONED`(AI 호출 없음) / 200 |
| 14 | 1~13 동안 로그 | ERROR 레벨 0건 |

### E2E-08 러버덕 → 복습 카드 → 설명 증거 (`RubberDuckFlowTest`, AC-26, AC-09)

전제: 온보딩 완료 사용자(`TestApi.onboard`), 테스트 seed skill S(EXPLANATION 0). 공통 시작 시각(`2026-10-05T10:00:00Z`, plan-day `2026-10-05`).

| # | 요청 | 기대 |
|---|---|---|
| 1 | `POST /rubber-duck` `{targetType: CONCEPT, conceptKey: "{S code}.BOUNDARY", skillCode: S}` | 201 `IN_PROGRESS`, `turnCount 0`, `maxTurns 5`, AI 호출 0 |
| 2 | `fakeAiProvider.use(RUBBER_DUCK, "default")` → `POST /rubber-duck/{D1}/turns` 3회 (2번째 설명에 런타임 조합 fake secret 1개) | 각 201, `turnNo` 1~3, `remainingTurns` 4·3·2, `suggestHint = false`, `aiMeta.promptVersion = "rubber.duck@v1"` |
| 3 | DB | `rubber_duck_turn` 3행, 2번째 `user_text`에 원문 secret 없음, 세션 `turn_count 3`, `learning_event` 증가 없음 |
| 4 | `fakeAiProvider.use(RUBBER_DUCK_SUMMARY, "two-gaps")` → `POST /rubber-duck/{D1}/complete` | 200 `COMPLETED`, `gaps` 2개(각 `reviewItemId`), `createdReviewItemCount 2`, `summarySkippedReason = null`, `aiMeta.promptVersion = "rubber.duck.summary@v1"` |
| 5 | DB | `review_item` 2행(`source_type = RUBBER_DUCK`, `review_type = EXPLAIN`, skill S, `due_at = 2026-10-05T19:00:00Z`), `RUBBER_DUCK_COMPLETED` 1행 `{sessionId: D1, turns: 3, gapCount: 2, targetType: CONCEPT, hintDisclosed: false}`, S의 K 0→1 `K1_ANY_EVENT` |
| 6 | `POST /rubber-duck/{D1}/complete` (새 키) | 409 `INVALID_STATE_TRANSITION`, `RUBBER_DUCK_SUMMARY` 호출 누계 1 (RD-4) |
| 7 | clock `2026-10-05T19:00:00Z`(새 plan-day) → `GET /reviews/due` → 러버덕 카드 1장에 `POST /reviews/{R}/answer` `{selfRating: GOOD, hintLevel: SELF_EXPLAIN, evaluate: false, answerText}` | 4번의 카드 2장이 due에 있다. 답변 200, `REVIEW_ANSWERED`(EXPLAIN) → S의 E 0→1 `E1_ANY_EXPLANATION` |
| 8 | clock +24시간 1초(cooldown 경과) → 같은 대상으로 새 세션 D2 시작 → 턴 3회(`default`) → `fakeAiProvider.use(RUBBER_DUCK_SUMMARY, "no-gaps")` → `complete` | 200 `COMPLETED`, `gaps = []`, `createdReviewItemCount 0` |
| 9 | DB | `RUBBER_DUCK_COMPLETED` `{sessionId: D2, turns: 3, gapCount: 0, hintDisclosed: false}` → S의 E 1→2 `E2_PARTIAL`(coverage 7,000 고정 ≥ 4,000, RD-5), 그 `skill_state_change.evidence_event_ids`에 이 이벤트 |
| 10 | `GET /rubber-duck/{D1}` | `turns` 3개 `turnNo` ASC, `userText` 마스킹본, `summary.gaps[].reviewItemId` = 5번 카드 |

### E2E-09 코드 읽기 → 러버덕 → 과제 완료 (`CodeReadingFlowTest`, AC-28, AC-27)

전제: 테스트 seed의 skill S에 reading `READ.TESTREPO.*` 1개(15분) 이상이 있고 VALIDATED challenge는 없다. 온보딩 입력은 S가 main 후보 1위이고 S의 planning KNOWLEDGE가 1 이상이 되도록 고정한다(자기평가, `focusSkillCodes = [S]`). 테스트 내내 `OutboundRequestRecorder`(§4.1)를 켠다.

| # | 요청 | 기대 |
|---|---|---|
| 1 | `POST /onboarding` `{runDiagnostic: false, selfAssessments, sideProject: {name: "주문 시스템", repoUrl: "https://repo.example.invalid/order"}, …}` | 201, `sideProject.status = ACTIVE` |
| 2 | `POST /today/generate` `{availableMinutes: 45, energyLevel: NORMAL}` | main task `taskType = READ_CODE`, `readingKey` = S의 첫 reading key(key ASC), `estimatedMinutes = 15`, 제목 `{repo.name} 읽기 — {파일명} {시작}~{끝}줄`. DB `learning_task.reading_key` 같은 값 |
| 3 | `GET /readings/{readingKey}` | 200, `repo.cloneHint`·`repo.pinnedCommit`·`path`·`startLine ≤ endLine`·`question`. 코드 본문 필드 없음 |
| 4 | `PATCH /today/tasks/{T}` `{status: IN_PROGRESS, version}` | 200 |
| 5 | `PATCH /today/tasks/{T}` `{status: COMPLETED, version}` | 409 `INVALID_STATE_TRANSITION` — 완료된 러버덕이 없다(RC-1) |
| 6 | `POST /rubber-duck` `{targetType: CODE_READING, targetId: T}` (`skillCode` 생략) | 201, `skill` = S(task의 skill), `readingKey` = 2번 key, `targetTitle` 있음 |
| 7 | 턴 3회(`default`) → `complete`(`default`) | 턴 201 × 3, `receivedRequests(RUBBER_DUCK)`의 `targetSummary`에 저장소·경로·줄 범위·`question`. 정리 200 `COMPLETED` |
| 8 | `PATCH /today/tasks/{T}` `{status: COMPLETED, readingFeedback: "HELPFUL", version}` | 200, `completed_at` 설정, DB `learning_task.reading_feedback = 'HELPFUL'`. `GET /me/export`의 `dailyPlans[].tasks[]` 해당 행에 `readingFeedback = "HELPFUL"` |
| 9 | 1~8 동안 `OutboundRequestRecorder.nonLoopbackRequests()` | 0건 — `repoUrl`과 저장소 `url`을 서버가 요청하지 않는다 |

### E2E-10 재현 과제 → AI 잠금 → 완료 (`RedoTaskFlowTest`, AC-31, AC-09, S4)

전제: 온보딩 완료 사용자, 테스트 seed skill S에 VALIDATED PRACTICE challenge C(difficulty 2, 35분) 1개. `MutableClock` 시작 `2026-10-16T10:00:00Z`(plan-day `2026-10-16`). `devpilot.planner.redo` 기본값.

| # | 요청 | 기대 |
|---|---|---|
| 1 | `POST /today/generate` → main `CHALLENGE`(C) → attempt → 자기설명 → hint `CONCEPT_HINT` → 제출 → 평가(`fakeAiProvider` `all-met`) → `PATCH /today/tasks/{T1}` `{status: COMPLETED}` | task `COMPLETED`. `CHALLENGE_EVALUATED` outcome `SOLVED_WITH_HINTS`(힌트를 봤으므로 독립 증거가 아니다) |
| 2 | clock `2026-10-18`(2일 뒤) → `POST /today/generate` | main이 `REDO`가 **아니다** — 창 시작 전(RE-2, `daysBetween = 2 < 3`) |
| 3 | clock `2026-10-19`(3일 뒤) → `POST /today/generate` | main `taskType = REDO`, `redoSourceTaskId = T1`, `redoSourceTaskType = CHALLENGE`, `estimatedMinutes = 35`, `reasons`에 `REDO_WITHOUT_AI`. DB `learning_task.redo_source_task_id = T1` |
| 4 | `POST /challenge-attempts/{C attempt}/hints` `{requestedLevel: DIRECTION, …}` | 409 `AI_ASSIST_LOCKED_FOR_REDO`. `hint_disclosure` 증가 0, `HINT_DISCLOSED` 0, AI 호출 0 (HL-9) |
| 5 | `POST /rubber-duck` `{targetType: CHALLENGE, targetId: {C attempt}}` | 409 `AI_ASSIST_LOCKED_FOR_REDO`. `rubber_duck_session` 0행 (RE-5) |
| 6 | `POST /rubber-duck` `{targetType: CONCEPT, conceptKey: "{S code}.X"}` | 201 — 다른 대상은 잠기지 않는다 |
| 7 | `PATCH /today/tasks/{T2}` `{status: IN_PROGRESS}` → `{status: COMPLETED, version}` (답 없이) | 400 `VALIDATION_FAILED` field `redoWithoutAi` code `VALUE_REQUIRED`. task는 `IN_PROGRESS` 그대로 |
| 8 | `PATCH /today/tasks/{T2}` `{status: COMPLETED, redoWithoutAi: true, version}` | 200. DB `redo_without_ai = true`, `REDO_COMPLETED{taskId: T2, sourceTaskId: T1, sourceTaskType: CHALLENGE, withoutAi: true, difficulty: 2}` 1행, `review_item` 새 행 0 |
| 9 | 4·5를 다시 요청 | 200/201 — 재현 과제가 `COMPLETED`라 잠금이 풀린다 |
| 10 | clock `2026-10-23` → `POST /today/generate` | main이 `REDO`가 **아니다** — 성공한 재현이 있으면 그 원본은 끝이다(RE-3) |
| 11 | (분기) 8을 `redoWithoutAi: false`로 한 경우 | `REDO_COMPLETED{withoutAi: false}`, `review_item` 1행(`concept_key = REDO:{T1}`, `source_type = REDO_TASK`, `origin = MANUAL`, `due_at = planDayStart(다음 plan-day)`). clock +3일 → `POST /today/generate`에서 `REDO`가 다시 나온다(시도 1/2) |
| 12 | `GET /me/export` | `dailyPlans[].tasks[]`의 T2 행에 `redoSourceTaskId`·`redoWithoutAi` |

### E2E-11 프로젝트 기록 (`SideProjectNoteFlowTest`, AC-33, AC-14, S3)

| # | 요청 | 기대 |
|---|---|---|
| 1 | `POST /side-projects` → `POST /side-projects/{P}/notes` `{noteType: INCIDENT, title, occurredOn: 오늘, skillCode: S, incident* 4개}` | 201 `SideProjectNoteView`, `decision*` 전부 null, `version = 0` |
| 2 | 같은 요청에서 `incidentPrevention` 생략 | 400 `VALIDATION_FAILED` field `incidentPrevention` code `VALUE_REQUIRED`, 행 0 |
| 3 | `{noteType: INCIDENT, …, decisionChoice: "x"}` | 400 `VALUE_NOT_ALLOWED`(field `decisionChoice`) |
| 4 | `{noteType: DECISION, decision* 3개, occurredOn: 내일}` | 400 `DATE_OUT_OF_RANGE`(field `occurredOn`) |
| 5 | `incidentSymptom`에 런타임 조합 fake secret | 201, 저장값·응답·로그에 원문 0건(AC-14). private key 블록 → 422 `SECRET_DETECTED_BLOCKED`, 행 0 |
| 6 | `GET /side-projects/{P}/notes` / `?noteType=DECISION` / `limit=1` | `occurredOn` DESC·`id` DESC, 유형 필터, `nextCursor`로 다음 1건 |
| 7 | `PATCH …/notes/{N}` `{title: "…", version: 0}` | 200 `version = 1`. 같은 값으로 다시 → `version`·`updatedAt` 그대로. 이전 `version` → 409 `CONCURRENT_MODIFICATION` |
| 8 | `PATCH …/notes/{N}` body에 `noteType` | 400 `MALFORMED_REQUEST`(알 수 없는 속성, PN-2) |
| 9 | `PATCH …/notes/{N}` `{incidentFix: "", version}` | 400 `VALUE_REQUIRED` — 유형에 필요한 항목은 지울 수 없다(I-22) |
| 10 | `PATCH …/notes/{N}` `{skillCode: "", version}` | 200, `skill = null` |
| 11 | `DELETE …/notes/{N}` → 다시 `DELETE` | 204 → 404 `RESOURCE_NOT_FOUND` |
| 12 | 기록 2개를 둔 채 `DELETE /side-projects/{P}` | 204. `side_project_note` 0행(cascade, `04` §8) |
| 13 | 1~12 동안 `learning_event`·`user_skill_state` | 변화 없음 (PN-3) |
| 14 | `GET /me/export` | `sideProjects[].notes[]`에 마스킹본이 `occurredOn` ASC로 들어 있다 |

---

## 12. Flutter 테스트

| 종류 | 대상 · 파일 | 규칙 |
|---|---|---|
| Controller 단위 | `test/features/today/presentation/today_controller_test.dart` 등 모든 `*Controller` | `ProviderContainer(overrides: [todayRepositoryProvider.overrideWithValue(FakeTodayRepository())])`. 상태 전이(loading → data/error), `Idempotency-Key`가 재시도 시 유지되고 새 행동 시 바뀌는지 |
| Repository 매핑 | `*_repository_test.dart` | fake `Dio` adapter(`HttpClientAdapter` 구현)로 요청 path·method·헤더·body와 응답 매핑 확인 |
| 인터셉터 | `test/core/api/interceptors_test.dart` | 순서(`08-coding-conventions.md` §8.4), 401 → refresh 1회 → 재시도, POST에만 `Idempotency-Key`, ProblemDetail → `ApiException` |
| 오류 문구 | `error_message_mapper_test.dart` | 매핑된 code → ARB, 모르는 code → `detail`, 둘 다 없음 → `errorUnknown` |
| JSON round-trip | `test/features/**/data/models/*_test.dart` | fixture `test/fixtures/api/<name>.json`(OpenAPI 예시에서 복사) → `fromJson` → `toJson` → 원본과 동일. **모르는 enum 값**(`"status": "ARCHIVED_V2"`) → `unknown`, `List<Enum>` 안의 모르는 값 → `unknown`, 명시적 `null` 필드 허용 |
| Widget: SCR-TODAY | `today_screen_test.dart` | 상태 5개: loading skeleton / `TODAY_NOT_GENERATED` → 시간 칩·컨디션 입력 / 생성 후 main task 1개 + reason 1~3개 + 복습 N장 / 오류 배너 + 재시도 / AI unavailable 배너(비-AI 기능 버튼 활성 유지). 360×800 화면에서 overflow 없음, "시작" 버튼 44px 이상 |
| Widget: SCR-REVIEW-SESSION | `review_screen_test.dart` | 답 입력 전 `expectedAnswer`·rubric 미표시 / "힌트 보기" → rubric 첫 항목만 표시 후 제출 body `hintLevel = CONCEPT_HINT` / "정답 먼저 보기" → `FULL_EXAMPLE` / 자기평가 4버튼 / `adjustedBy` 안내 표시 / 마지막 카드 후 완료 상태 |
| Widget: Markdown | `markdown_view_test.dart` | `07-security-and-privacy.md` §9.5 (raw HTML 텍스트, 비 https 링크 비활성, 이미지 미렌더링) |
| Widget: SCR-RUBBER-DUCK | `rubber_duck_screen_test.dart` | 설명 입력 → 질문 표시 / 남은 턴 수 / `suggestHint = true`면 CHALLENGE 대상은 hint 단계로 가는 버튼, 그 외 대상은 "정리하기" 안내 / 턴 실패(502·503·504) 시 입력한 설명이 그대로 남음(서버가 저장하지 않으므로) / 같은 제출 재시도는 같은 `Idempotency-Key` / 정리 결과의 gap 목록과 "복습 카드 N장 추가" / `summarySkippedReason`이 있으면 "정리는 못 했지만 대화는 저장됨" 안내 / `aiStatus ∈ {DISABLED, BALANCE_EXHAUSTED}`면 시작 버튼 비활성 |
| Widget: SCR-READ-CODE | `read_code_screen_test.dart` | `cloneHint`·`pinnedCommit`·경로·줄 범위·`question`·`lookFor` 표시(RC-4), 코드 본문 영역 없음, "러버덕으로 설명하기" → `POST /rubber-duck` `{targetType: CODE_READING, targetId: taskId}`, 러버덕 완료 전에는 과제 완료 버튼 비활성(RC-1) |
| Widget: SCR-PROJECTS | `projects_screen_test.dart` | 목록·등록·수정 한 화면, `repoUrl`은 https만 탭 가능한 링크(`07` §9.5), 상태 변경, 삭제 확인(기록도 함께 지워진다는 문구 포함), 카드 탭 → `/projects/{id}` |
| Widget: SCR-PROJECT-DETAIL · SCR-PROJECT-NOTE-EDIT | `project_notes_screen_test.dart` | 유형 필터 3개 / 카드에 유형 배지·날짜·본문 첫 항목 2줄 / "+ 결정 기록"·"+ 장애 기록"이 각각 `noteType` query로 이동 / **편집 화면에 유형 입력이 없다**(PN-2) / 유형별 입력 항목이 3개·4개 / 필수 항목이 비면 "저장" 비활성 / 날짜 선택기가 오늘 이후를 막는다 / `422` → 인라인 `projectNote.secretBlocked` |
| Widget: SCR-TODAY REDO 카드 (S4) | `today_redo_test.dart` | `taskType = REDO`면 배지 "AI 없이 재현" + 잠금 줄 `today.redo.locked`가 `PLANNED`·`IN_PROGRESS` 모두에서 보인다 / `IN_PROGRESS`에 "러버덕으로 설명하기" 버튼이 **없다** / 완료 시트에 질문 2버튼이 뜨고 고르기 전에는 "완료 기록" 비활성 / "아니요" 선택 후 완료 → `redoWithoutAi: false` 전송 + `today.redo.reviewCreated` 토스트 |
| Widget: 재현 잠금 안내 (S4) | `redo_lock_test.dart` | SCR-TRAINING-ATTEMPT·SCR-RUBBER-DUCK이 `409 AI_ASSIST_LOCKED_FOR_REDO`를 받으면 배너가 아니라 버튼 비활성 + `today.redo.lockedElsewhere` 1줄 + "Today로 가기"(§5.1·§6.5) |
| Widget: SCR-ONBOARDING 트랙 선택 (S3) | `onboarding_track_test.dart` | 라디오 2개, 기본 `JAVA_BACKEND` / 필수 skill 수는 `GET /skills/tree?role=` 실패 시 생략 / 트랙을 바꾸면 3단계 입력이 초기화되고 토스트 / 요청 body `learningGoal.targetRole` / SCR-LEARNING-GOAL에서는 읽기 전용 |
| Integration | `integration_test/onboarding_to_today_test.dart` | 아래 |

Integration test (`onboarding_to_today_test.dart`):
- `ProviderScope` override로 `AuthGateway`를 로그인 상태 fake로, 각 repository를 in-memory fake API(`test/fixtures/api/` 응답 사용)로 바꾼다. 실제 backend에 연결하지 않는다(전체 스택은 §11 E2E-01이 검증).
- 흐름: 앱 시작 → 온보딩 입력(3단계 진단/자기평가 중 자기평가 선택, 4단계 사이드 프로젝트는 기본 이름 "주문 시스템" 그대로 등록) → "그대로 시작" → Today 생성 입력 → 45분·보통 선택 → 생성 → main task 제목과 reason 표시 → "시작" → 상태 `IN_PROGRESS` 표시. 온보딩 요청 body에 `runDiagnostic = false`와 `sideProject.name = "주문 시스템"`이 들어가는지 fake API에서 확인한다(기본 이름은 클라이언트가 채운다, `05` §4.1).
- 실행: `fvm flutter drive --driver=test_driver/integration_test.dart --target=integration_test/onboarding_to_today_test.dart -d web-server --browser-name=chrome` (chromedriver 필요).
- **Flutter web은 canvas로 렌더링하므로 Selenium·Playwright 같은 DOM 기반 자동화는 쓰지 않는다.** 요소 찾기는 `find.byKey(const Key('today.generateButton'))`처럼 Flutter finder와 위젯 `Key`로 한다. Key 이름은 `<feature>.<element>` 형식.

---

## 13. 성능 스모크

NFR-02: non-AI API p95 < 1000ms.

| 항목 | 값 |
|---|---|
| 실행 시점 | S3 종료 전(실사용 시작 = M1 완료 전), S5 종료 전. 수동. S2 완료 후 Today·Review를 먼저 쓰기 시작한다면(선택, `11` §3) 그 전에 한 번 더 돌린다. 그 단계에 아직 없는 endpoint의 threshold는 빼고 실행한다 |
| 환경 | 로컬 `infra/compose.perf.yml`: api 컨테이너 `cpus: 2`, `mem_limit: 1.5g`(운영 서버 조건, `10` §3.3), PostgreSQL 16 컨테이너, `SPRING_PROFILES_ACTIVE=perf`(= `prod` 설정 + fake AI + 테스트 JWKS + rate limit 완화 `requests-per-minute: 100000`) |
| 네트워크 가정 | 운영 DB는 같은 호스트라 왕복 지연이 작다. 대신 **현재 배포 기준(임시)** tailnet 구간(클라이언트 ↔ 서버) 지연이 더해진다(공개 전환 후에는 공개망 지연으로 바뀐다, `11` §3.10). 결과 p95가 700ms를 넘으면 원인을 조사한다 |
| 토큰 | `./gradlew perfToken`(test source의 `TestJwtFactory` 사용)으로 2시간짜리 토큰 3개 생성 → `K6_TOKENS` 환경변수 |
| 데이터 | `perf/seed.sql` (사용자당) — skill 80, `user_skill_state` 80, `review_item` 400(오늘 due 40), `review_answer` 3,000, `learning_event` 6,000(180일), `learning_session` 300, `daily_plan` 180 + `learning_task` 540, plan version 3 + milestone 30, `side_project` 3, `rubber_duck_session` 120 + `rubber_duck_turn` 480, `challenge_attempt` 200 + submission 300, `coach_review` 50 + finding 250, `evidence_candidate` 40, `weekly_review` 26. 사용자 3명 |

`perf/smoke.js` 구성:

```javascript
export const options = {
  scenarios: { smoke: { executor: 'constant-vus', vus: 5, duration: '3m' } },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:me}': ['p(95)<1000'],
    'http_req_duration{endpoint:today}': ['p(95)<1000'],
    'http_req_duration{endpoint:reviews_due}': ['p(95)<1000'],
    'http_req_duration{endpoint:review_answer}': ['p(95)<1000'],
    'http_req_duration{endpoint:today_generate}': ['p(95)<1000'],
    'http_req_duration{endpoint:plans_active}': ['p(95)<1000'],
    'http_req_duration{endpoint:plan_budget}': ['p(95)<1000'],
    'http_req_duration{endpoint:skills_me}': ['p(95)<1000'],
    'http_req_duration{endpoint:dashboard}': ['p(95)<1000'],
    'http_req_duration{endpoint:learning_sessions}': ['p(95)<1000'],
    'http_req_duration{endpoint:coach_reviews}': ['p(95)<1000'],
    'http_req_duration{endpoint:replan_preview}': ['p(95)<1000'],
    'http_req_duration{endpoint:side_projects}': ['p(95)<1000'],
    'http_req_duration{endpoint:rubber_duck_get}': ['p(95)<1000'],
    'http_req_duration{endpoint:reading_get}': ['p(95)<1000'],
  },
};
// 각 VU: 토큰 1개 선택 → me → today → reviews/due → review answer(evaluate=false, 새 Idempotency-Key)
// → plans/active → plans/active/budget → skills/me → dashboard → learning-sessions → coach/reviews
// → side-projects → rubber-duck/{id}(기존 세션 조회) → readings/{key}
// → (10회에 1회) today/generate force=true, replan/preview → sleep(1)
// 러버덕 턴·정리는 AI endpoint라 NFR-02(non-AI p95) 대상이 아니다
```

- 실패 기준: threshold 하나라도 실패. 결과 요약(`k6 --summary-export`)을 `perf/results/<date>.json`으로 저장하고 PR 설명에 p95 표를 붙인다(파일은 커밋하지 않음).
- 실패하면 Hibernate SQL 수(`hibernate.generate_statistics` perf profile만)와 느린 쿼리(`EXPLAIN ANALYZE`)를 확인하고 인덱스·projection으로 고친다.

---

## 14. 정적 분석 · 품질 게이트

### 14.1 CI 순서 (`ci.yml`)

트리거: `main`·`developer` push와 **두 브랜치로 향하는 PR**(`08-coding-conventions.md` §10.1). 필수 체크 4개(`security`, `backend`, `app`, `content`)는 두 브랜치 모두에 건다.

앞 단계가 실패하면 같은 job의 뒤 단계는 실행하지 않는다. job끼리는 병렬이다.

**job `backend`** (paths `backend/**`, `content/**`, `database/**`)

| 순서 | 단계 | 실패 기준 |
|---|---|---|
| 1 | `gradle/actions/wrapper-validation` | wrapper checksum 불일치 |
| 2 | `./gradlew spotlessCheck` | 포맷 차이 1건 |
| 3 | `./gradlew compileJava compileTestJava` | 컴파일 오류, main 경고 1건(`-Werror`), lockfile 불일치 |
| 4 | `./gradlew checkstyleMain checkstyleTest` | 위반 1건 |
| 5 | `./gradlew pmdMain spotbugsMain` | 위반 1건 |
| 6 | `./gradlew test` (tag `unit`: 규칙, ArchUnit, web slice) | 실패 1건 |
| 7 | `./gradlew integrationTest` (tag `integration`: Testcontainers, E2E) | 실패 1건 |
| 8 | `./gradlew jacocoTestCoverageVerification` | §15 기준 미달 |
| 9 | migration 불변 검사 (§8.1) | 기존 `V*.sql` 수정 |
| 10 | OpenAPI 스냅샷: 7단계의 `OpenApiSnapshotTest`(tag `integration`, test profile, `springdoc.api-docs.enabled=true`)가 `build/openapi/openapi.yaml`을 쓰고, `./gradlew openApiCheck`가 `docs/api/openapi.yaml`과 비교한다. 갱신은 `./gradlew openApiUpdate` (`18-project-setup-and-local-dev.md`) | 생성 파일 없음 또는 스냅샷과 차이 있음(갱신 커밋 필요) |

**job `app`** (paths `app/**`)

| 순서 | 단계 | 실패 기준 |
|---|---|---|
| 1 | `fvm flutter pub get --enforce-lockfile` | lockfile 불일치 |
| 2 | `dart run build_runner build --delete-conflicting-outputs` | 생성 오류 |
| 3 | `dart format --output=none --set-exit-if-changed lib test integration_test` | 변경 필요 파일 1개 |
| 4 | `fvm flutter analyze --fatal-infos --fatal-warnings` | 1건 |
| 5 | `app/tool/check_identifiers.sh` | 로마자 식별자 1건 |
| 6 | `fvm flutter test --coverage` | 실패 1건 |
| 7 | `fvm flutter build web --release --no-web-resources-cdn` | 빌드 실패 |

**job `content`** (paths `content/**`): `python3 content/tools/validate_content.py`(참조 검증기, `19-content-spec.md` §4) — 오류 1건. Java `ContentValidator`는 backend unit test(`ContentValidatorTest`)와 기동 시 검증에서 같은 규칙을 확인한다.

**job `security`** (모든 PR): `actions-pin-check` → `gitleaks` → `trivy-fs`. 실패 기준은 `07-security-and-privacy.md` §11.

- `main` ruleset의 필수 status check: `backend`, `app`, `content`, `security`. path 필터로 job이 건너뛰어진 경우 성공으로 보고하도록 각 job에 `if` 대신 path 판정 step을 둔다.
- `deploy.yml`은 `backend`·`app`을 다시 실행한 뒤 이미지 빌드 → `trivy-image` → 배포 → `smoke-headers.sh`(GitHub runner에서 공개 도메인 대상, 실패 시 직전 tag로 롤백) 순서다(`10-deployment-and-operations.md`).
- `app-integration` job(§12)은 `workflow_dispatch`와 `v*` tag push에서만 실행한다.

### 14.2 로컬 실행

- backend 전체: `./gradlew spotlessApply check` (`check`는 `test`, `integrationTest`, 정적 분석, 커버리지 검증을 포함하고 `aiEval`은 제외).
- app 전체: `dart format . && fvm flutter analyze && fvm flutter test`.
- PR을 만들기 전에 두 명령이 통과해야 한다(PR 템플릿 "Tests"에 결과 기록).

---

## 15. 커버리지

- 도구: JaCoCo. `test`와 `integrationTest`의 exec 데이터를 합쳐 계산한다.
- **전체 프로젝트 커버리지 게이트는 두지 않는다.**
- 게이트: 아래 패키지 각각 **line coverage ≥ 80%** (`jacocoTestCoverageVerification`, `element = PACKAGE`, `LINE COVEREDRATIO 0.80`).
- `includes`에는 **아래 14개 패키지 이름을 그대로 나열한다.** 와일드카드 `com.devpilot.*.domain`은 `*`가 `.`도 매칭해서 표에 없는 `user.domain`·`goal.domain`·`onboarding.domain`(JPA entity 위주)까지 게이트에 넣고 `./gradlew check`를 막는다.

| 패키지 | 주요 대상 |
|---|---|
| `com.devpilot.plan.domain` | `StudyBudgetCalculator`, `DeadlineRiskEvaluator`, `ReplanSuggestionPolicy`(축소·확장), `PlanTemplatePlacement` |
| `com.devpilot.skill.domain` | `SkillLevelRules`, `PlanningLevelPolicy` |
| `com.devpilot.review.domain` | `FinalRatingPolicy`, `RuleBasedV1Scheduler`, `DueReviewSelector`(RV-INTERLEAVE 포함) |
| `com.devpilot.today.domain` | `PlannerScoring`, `TaskProposalPolicy`(READ_CODE 포함), `TimeAllocator`, `ReasonTemplates` |
| `com.devpilot.training.domain` | `AttemptOutcomeCalculator` |
| `com.devpilot.learning.domain` | `HintLadderPolicy`, `ComebackModePolicy`, `RubricScorer` (여러 모듈이 공유, `03` §3.2) |
| `com.devpilot.rubberduck.domain` | `RubberDuckPolicy` |
| `com.devpilot.coach.domain` | `DiscoveredByResolver` |
| `com.devpilot.evidence.domain` | `MetricsCalculator` |
| `com.devpilot.radar.domain` | `RequirementFitClassifier` (S7부터) |
| `com.devpilot.common.time`, `com.devpilot.common.math` | `PlanDayCalculator`, `FixedPointMath` |
| `com.devpilot.integration.ai.guard`, `.masking`, `.budget` | 가드 7종(`NoAnswerGuard` 포함), `SecretMasker`, `AiBudgetGuard`, `AiCostCalculator` |

- 해당 패키지가 아직 없는 단계에서는 규칙 목록에서 빠진다(빈 패키지로 통과시키지 않음).
- `com.devpilot.project.domain`은 넣지 않는다. `SideProject`(entity)와 `SideProjectStatus`(enum)뿐이고 규칙 클래스가 없다(`03` §3.2). 규칙 클래스가 생기면 이 표에 추가한다.
- 커버리지를 올리려고 getter·record만 호출하는 테스트를 쓰지 않는다. 미달이면 vector·경계값 테스트를 추가한다.
- Flutter는 `--coverage`로 수집만 하고 게이트를 두지 않는다.

---

## 16. Flaky test 정책

| 규칙 | 내용 |
|---|---|
| 정의 | 코드 변경 없이 같은 커밋에서 통과와 실패가 모두 나온 테스트 |
| 예방 | 시간은 `MutableClock`, 대기는 Awaitility(`Thread.sleep` 금지, TEST-01), 사용자 데이터는 테스트마다 새 사용자(§4.2), 순서 의존 금지, 랜덤은 고정 seed, 고정 포트를 쓰는 테스트 서버 금지(`TestJwksServer`는 포트 0으로 띄운다), 외부 네트워크 금지 |
| 재시도 | 자동 재시도 plugin·애노테이션 금지(TEST-05). CI "Re-run failed jobs"로 통과시킨 경우에도 flaky로 기록한다 |
| 발견 시 | GitHub issue(label `flaky`, 실패 로그·커밋 SHA) 생성 → **2 작업일 안에** 원인 수정 |
| 격리 | 2 작업일 안에 못 고치면 `@Disabled("flaky: #<issue>")`. 동시에 격리된 테스트는 최대 1개(TEST-03). 2개째가 필요하면 새 기능 작업을 멈추고 먼저 고친다 |
| 격리 금지 | `07-security-and-privacy.md` §16의 보안 테스트, **이 문서** §9 권한 격리, §6 JWT, §10.3 예산 경계(한도 정의는 `07` §12.1), ArchUnit. 이 테스트가 flaky면 수정 전까지 머지를 막는다 |
| 기록 | 수정 PR에 원인 분류(시간, 동시성, 순서, 환경, 데이터)를 적는다 |

---

## 17. 테스트 데이터 개인정보

| 규칙 | 내용 |
|---|---|
| 코드 | 업무 코드·고객 데이터·실제 운영 코드를 fixture, vector, eval case, 스크린샷에 쓰지 않는다. 모든 코드 예시는 직접 작성한 합성 코드 |
| 로드맵·기술 목록 | 실제 로드맵 문서를 그대로 붙여넣지 않는다. 가상 이름(`Sample Roadmap`)과 직접 작성한 합성 항목만 |
| 코드 읽기 · 러버덕 | 테스트 reading은 가상 저장소(`repo.example.invalid`)와 합성 경로만 쓴다. 큐레이션 저장소(`content/curated-repos.yaml`)의 실제 코드를 fixture·eval case에 복사하지 않는다(라이선스가 명시되지 않은 저장소가 있다 — 읽기만, `19` §3.8). 러버덕 설명·질문 fixture도 직접 쓴 합성 문장이다 |
| 사람 정보 | 실명, 실제 이메일, 실제 GitHub 계정 금지. 이메일은 `*@devpilot.test`, 표시 이름은 `Test Owner`, `Test Invited` |
| Secret | 실제 키 금지. fake secret은 런타임 문자열 조합(`07` §11.3) |
| 운영 데이터 | 운영 DB 덤프·백업을 테스트·perf·demo 데이터로 쓰지 않는다. `perf/seed.sql`은 생성 스크립트로 만든다 |
| 로그·리포트 | CI 아티팩트(테스트 리포트)에 토큰·secret이 찍히지 않게 테스트 로그도 `07` §6.1을 따른다 |
| 리뷰 | fixture를 추가하는 PR은 "Security impact"에 합성 데이터임을 적는다 |

---

## 18. Definition of Done

완료 기준은 `16-definition-of-ready-done.md`가 유일한 기준이다. 이 문서와 관련된 최소 조건은 다음과 같다.

- 해당 기능의 `06` vector(계획 템플릿 배치는 `19` §5.4) 전부 통과, 관련 AC 테스트 존재·통과
- 사용자 소유 endpoint를 추가했으면 §9 catalog 갱신
- §14.1 CI job 전부 green
- 새 테스트가 §3 명명·태그 규칙과 §17 데이터 규칙을 지킴
