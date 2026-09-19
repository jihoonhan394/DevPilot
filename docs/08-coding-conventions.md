# 08. Coding Conventions

> Status: Accepted (v2) · Last updated: 2026-09-18 · Related: ADR-019, DEC-09, DEC-10, DEC-12, `03-system-architecture.md`, `09-test-and-quality.md`
>
> 이 문서는 backend(Java/Spring/JPA)와 app(Flutter/Dart)의 **코드 작성 규칙과 자동 강제 도구 설정**을 정의한다. 사람과 AI 에이전트 모두 같은 규칙을 따른다. 규칙 대부분은 §11의 도구로 CI에서 강제되며, 도구로 강제할 수 없는 규칙은 PR 리뷰에서 확인한다.

---

## 1. 우선순위

규칙이 충돌하면 위의 것이 이긴다.

1. 확정 기준 문서: `03-system-architecture.md`(모듈·클래스 이름·트랜잭션), `04-domain-model-and-db.md`(entity·enum·불변식), `06-learning-engine-rules.md`(수치 규약)
2. 이 문서
3. 자동 도구 결과: google-java-format AOSP, `dart format`
4. Google Java Style Guide (포맷 외 규칙), Effective Dart
5. Spring·Flutter 공식 문서의 관례

- 포맷은 사람이 판단하지 않는다. `./gradlew spotlessApply`, `dart format`이 만든 결과가 정답이다.
- 이 문서에 없는 결정이 필요하면 구현을 멈추고 Proposed ADR을 만든다.

---

## 2. 식별자 규칙

### 2.1 공통

| 규칙 | 내용 |
|---|---|
| 언어 | 모든 식별자(패키지, 클래스, 메서드, 필드, 변수, enum, DB 이름, JSON 필드, ARB 키, 파일 이름)는 **영어만** 쓴다. 한글 식별자 금지 |
| 로마자 한국어 | 한국어 발음을 로마자로 적은 식별자 금지. 예: `sawonList` → `employees`, `hakseupGyehoek` → `learningPlan` |
| `Yn` 접미사 | `useYn`, `delYn` 같은 "여부" 표기 금지. boolean은 `is`/`has`/`can` 접두사 또는 형용사(`active`, `deferred`) |
| 약어 | 한 단어처럼 쓴다: `HttpClient`, `JsonMapper`, `AiGateway`, `userId`, `ics`. 예외 없음. `ID`, `URL`, `AI`도 `Id`, `Url`, `Ai`로 쓴다 |
| 의미 없는 이름 | `data`, `info`, `tmp`, `obj`, `result2`, `a` 금지. 한 줄 lambda 매개변수와 `for (int i …)` 인덱스는 허용 |
| 도메인 용어 | 같은 개념은 같은 영어 단어를 쓴다. 기준은 `15-glossary.md`. 사전에 없는 도메인 용어가 필요하면 glossary에 먼저 추가한다 |
| 테스트 이름 | `should<Expected>When<Condition>` (`09-test-and-quality.md` §3) |

### 2.2 금지 로마자 사전

Checkstyle `RomanizedKorean` 규칙(§11.3)과 Dart CI 스크립트가 아래 토큰을 **camelCase 단어 경계**에서 찾으면 실패한다.

```text
sawon hoesa gyoyuk jumun gogaek mokrok sangse deungrok sujeong sujung sakje johoe jeonsong
gyeolje seungin hakseup bokseup gyehoek mokpyo sayongja jeongbo bunseok pyeongga munje
dapbyeon sijak jongryo sangtae yocheong eungdap gisul hoewon
gwalli seolmyeong yeonseup jindan gubun yeobu beonho ireum nalja geumaek jeojang cheori
gyeolgwa naeyong jemok gaesu suryang bunryu ilja
```

정규식 (Java 소스 한 줄 단위, 주석 제외):

```regex
(?:\b|_|(?<=[a-z])(?=[A-Z]))(?i:sawon|hoesa|gyoyuk|jumun|gogaek|mokrok|sangse|deungrok|sujeong|sujung|sakje|johoe|jeonsong|gyeolje|seungin|hakseup|bokseup|gyehoek|mokpyo|sayongja|jeongbo|bunseok|pyeongga|munje|dapbyeon|sijak|jongryo|sangtae|yocheong|eungdap|gisul|hoewon|gwalli|seolmyeong|yeonseup|jindan|gubun|yeobu|beonho|ireum|nalja|geumaek|jeojang|cheori|gyeolgwa|naeyong|jemok|gaesu|suryang|bunryu|ilja)(?=[A-Z0-9_]|\b)
```

`Yn` 접미사:

```regex
(?<=[a-z0-9])Yn\b
```

- 토큰 앞은 단어 시작, `_`, 또는 소문자→대문자 경계여야 하고, 토큰 뒤는 대문자·숫자·`_`·단어 끝이어야 한다. 그래서 `getSangseInfo`, `SAWON_LIST`, `bokseup`은 잡고 영어 단어 내부의 우연한 일치는 잡지 않는다.
- 오탐이면 사전에서 토큰을 빼는 PR을 만든다. `@SuppressWarnings`로 우회하지 않는다.
- 사전은 발견될 때마다 추가한다(추가만 하고 규칙 완화는 ADR 필요).

---

## 3. Java

### 3.1 포맷 · 기본

| 항목 | 규칙 |
|---|---|
| Formatter | google-java-format **AOSP 스타일**(4칸 들여쓰기), 최대 100열 (DEC-09). Spotless로 적용 |
| Java 버전 | Gradle toolchain 25 (fallback 21, DEC-02). `--release` 고정 |
| Import | wildcard 금지, unused 금지, static import는 테스트의 AssertJ·Mockito와 enum 상수 switch에서만 |
| 파일 | top-level 타입 1개. 파일 이름 = 타입 이름 |
| `var` | 오른쪽에서 타입이 명확할 때만(`new`, 팩토리 메서드 이름에 타입 포함). 반환 타입이 불명확한 메서드 호출 결과에는 쓰지 않는다 |
| Lombok | 사용 금지 (record, IDE 생성, 명시 코드로 대체) |
| 컴파일 경고 | main: `-Xlint:all,-processing,-serial -Werror` |
| `final` | 필드는 가능한 한 `final`. 지역 변수·매개변수에는 붙이지 않는다(포맷 노이즈) |
| switch | enum switch는 `->` 형식 + exhaustive(`default` 없이 컴파일러가 누락 검출). 문(statement) 형식 fall-through 금지 |

### 3.2 Record와 DTO

- 요청·응답·명령·조회 결과·규칙 입출력·설정은 모두 `record`다.
- record의 컬렉션 컴포넌트는 compact constructor에서 방어 복사한다.

```java
public record DueReviewView(UUID reviewItemId, String prompt, List<RubricItemView> rubric) {
    public DueReviewView {
        rubric = List.copyOf(rubric);
    }
}
```

- record에 비즈니스 메서드를 넣지 않는다. 파생 값 계산은 규칙 클래스가 한다. 예외: 입력 검증과 정규화(compact constructor), `from(...)` 매핑 static 메서드(§6).

### 3.3 Null 처리 (JSpecify)

| 규칙 | 내용 |
|---|---|
| 기본 | 모든 main 패키지에 `package-info.java`를 두고 `@org.jspecify.annotations.NullMarked`를 붙인다. 그 패키지의 타입은 기본 non-null이다 |
| nullable | null이 가능한 필드·매개변수·반환·타입 인자에만 `@Nullable`(`org.jspecify.annotations`). 배열·제네릭 위치는 JSpecify 문법(`String @Nullable []`, `List<@Nullable String>`)을 따른다 |
| 강제 | ArchUnit `ARCH-18`이 `@NullMarked` 누락 패키지를 실패시킨다. **NullAway·Error Prone 검사는 MVP에서 쓰지 않는다** (Java 25 호환 확인 비용) |
| 입력 경계 | 외부 입력은 Bean Validation `@NotNull`로 막는다. `@Nullable` 주석은 문서·IDE 경고용이며 런타임 검증이 아니다 |
| DB nullable 컬럼 | entity 필드에 `@Nullable`을 붙이고, getter도 `@Nullable`을 반환한다 |
| `Objects.requireNonNull` | public 생성자·static factory의 non-null 인자에 사용. 메시지는 인자 이름 |

### 3.4 Optional

| 허용 | 금지 |
|---|---|
| repository·query service의 단건 조회 반환 (`Optional<AppUser> findByExternalAuthId`) | 필드, 매개변수, record 컴포넌트, JSON DTO |
| 짧은 체인(`map`, `filter`, `orElseThrow`) | `Optional.get()` (대신 `orElseThrow()`), `isPresent()` 후 `get()` |
| | `Optional<List<…>>`, `Optional<Optional<…>>`. 빈 컬렉션을 반환한다 |
| | `Optional.ofNullable(x).ifPresent(...)`로 단순 null 검사 대체 |

application service는 "없음"을 `Optional`로 올리지 않고 `NotFoundException`으로 바꾼다.

### 3.5 불변성

- 규칙 클래스(`03` §3.2 굵은 이름)는 상태가 없고 설정 record만 생성자로 받는다. 입력·출력은 불변 record다.
- entity 컬렉션 getter는 `List.copyOf` 또는 `Collections.unmodifiableList`를 반환한다. 추가·삭제는 aggregate root의 도메인 메서드로만 한다.
- `static` 가변 상태 금지(캐시 포함). 캐시가 필요하면 Spring bean 필드로 두고 동시성 타입(`ConcurrentHashMap`)을 쓴다.

### 3.6 의존성 주입

- 생성자 주입만 쓴다. 생성자가 하나면 `@Autowired`를 붙이지 않는다.
- 필드·setter 주입 금지(ArchUnit `ARCH-06`).
- 주입 필드는 `private final`.
- 순환 의존이 생기면 `@Lazy`로 풀지 않고 설계를 바꾼다(이벤트 또는 query service 분리).

### 3.7 설정 (`@ConfigurationProperties`)

- 루트는 `common.config.DevPilotProperties` 하나다(`03` §9). 모듈별 설정은 중첩 record로 둔다.

```java
@Validated
@ConfigurationProperties("devpilot")
public record DevPilotProperties(
        @Valid @NotNull Security security,
        @Valid @NotNull Planner planner,
        @Valid @NotNull Ai ai /* … */) {

    public record Security(
            List<String> allowedEmails,
            List<String> allowedSubjects,
            @Positive int maxRequestBodyBytes,
            @NotNull Duration accountDeletionMaxTokenAge,
            String logHashKey) {}
}
```

- 소수 설정값(`0.25`, `1.20`)은 YAML에서 `BigDecimal`로 바인딩하고, 기동 시 `BasisPoints`/`Micros` 정수로 변환한다. 정수로 떨어지지 않으면 기동 실패(N-6). 규칙 클래스에는 변환된 정수 record만 넘긴다.
- 교차 검증(가중치 합 10,000bp, threshold 순서, `prod`에서 `logHashKey` 형식)은 해당 중첩 record의 compact constructor에서 하고, 위반 시 `IllegalArgumentException`을 던진다. 바인딩 실패는 기동 실패다. 별도 validator bean을 만들지 않는다.
- `@Value` 사용 금지. 설정은 `DevPilotProperties`에서만 읽는다.

### 3.8 수치 (N-1 ~ N-7)

- 규칙 계산에 `double`, `float`, `Double`, `Float` 금지(ArchUnit `ARCH-13`). `long`과 `Math.multiplyExact`, `Math.addExact`를 쓴다.
- 나눗셈은 `common.math.FixedPointMath`의 `floorDiv`, `ceilDiv`, `roundHalfUpDiv`만 쓴다(`06` §1 N-4). 인자 음수는 `IllegalArgumentException`.
- 비율·점수 변수 이름에 단위를 붙인다: `ratioBp`, `baseScoreMicro`(DB·JSON은 `04`의 이름을 따른다), `costMicroUsd`.
- `BigDecimal`은 설정 바인딩과 비용 단가 입력에만 쓴다.

### 3.9 시간

| 규칙 | 내용 |
|---|---|
| 현재 시각 | 주입된 `java.time.Clock`만 쓴다: `Instant.now(clock)`. 무인자 `now()` 금지(ArchUnit `ARCH-08`) |
| Clock bean | `common.time.ClockConfig`만 `Clock.systemUTC()`를 호출한다 |
| 저장 | `Instant`(UTC). `LocalDateTime` 필드·컬럼 금지 |
| 사용자 날짜 | `PlanDayCalculator.planDate(Instant, ZoneId, dayStartHour)`만 쓴다. `LocalDate.now(zone)`으로 "오늘"을 계산하지 않는다 |
| 기간 | 설정은 `Duration`, 날짜 차이는 `ChronoUnit.DAYS.between(LocalDate, LocalDate)` |
| 경과 시간 측정 | `System.nanoTime()` 허용(로그·지표용). 비즈니스 판단에 쓰지 않는다 |
| sleep | main 코드에서 `Thread.sleep` 금지 |

### 3.10 예외

| 규칙 | 내용 |
|---|---|
| 계층 | 클라이언트에 전달되는 오류는 `common.error.DevPilotException` 하위(`NotFoundException`, `ConflictException`, `BusinessValidationException`, `ForbiddenException`, `PayloadTooLargeException`, `TooManyRequestsException`)로 던진다. `ErrorCode`는 HTTP status를 `int`로 가진다(Spring 타입 의존 없음) |
| 변환 | `GlobalExceptionHandler`가 `03` §7 표대로 `ProblemDetail`로 바꾼다. 컨트롤러와 서비스는 `ResponseEntity`로 오류를 만들지 않는다 |
| 상태 전이 위반 | entity 메서드가 `ConflictException(ErrorCode.INVALID_STATE_TRANSITION)`을 던진다 |
| checked 예외 | application 공개 메서드 시그니처에 checked 예외를 두지 않는다. `IOException` 등은 발생 지점에서 의미 있는 unchecked 예외로 감싸고 **원인(cause)을 반드시 전달**한다 |
| 메시지 | 예외 메시지는 영어 내부용. 사용자 문구는 `messages_ko.properties`의 `error.<CODE>`에만 둔다 |
| 로그 | 예외는 처리하는 곳에서 한 번만 로그한다. "로그 후 다시 던지기" 금지 |
| 빈 catch | 금지. 무시가 정답이면 이유 주석 + DEBUG 로그 |
| `throws Exception` | 금지 |
| 정리 로직 | 실패 시 되돌리기가 필요하면 broad catch 대신 `finally` + 성공 플래그를 쓴다 (예: `IdempotencyService`의 record 삭제) |

```java
boolean completed = false;
try {
    ResponseEntity<T> response = action.get();
    saveResponse(record, response);
    completed = true;
    return IdempotentResult.executed(response);
} finally {
    if (!completed) {
        deleteRecord(userId, key); // allow the client to retry with the same key
    }
}
```

**`catch (Exception | RuntimeException | Throwable)` 허용 위치 (전부)**

| # | 위치 | 이유 | 필수 처리 |
|---|---|---|---|
| 1 | `GlobalExceptionHandler`의 `@ExceptionHandler(Exception.class)` (catch 절이 아닌 handler) | 최후 방어선, 500 `INTERNAL_ERROR` | ERROR 로그 1회(stack trace 포함), 고정 응답 |
| 2 | 비동기 task 최상위 메서드: `ChallengeGenerationTask`, `SubmissionEvaluationTask`, `ReviewVariantTask`, `CoachAnalysisTask`, `EvidenceDraftTask`, `RequirementAnalysisTask` | 작업을 `FAILED(INTERNAL_ERROR)`로 기록 | 별도 트랜잭션으로 상태 저장, ERROR 로그 1회, 이유 주석 |
| 3 | 스케줄 job의 **사용자 단위 루프 본문**: `ProgressSnapshotJob`, `WeeklyReviewJob`, `CoachContentPurgeJob`, `RetentionCleanupJob`, `AccountDeletionJob`, `OrphanAsyncTaskJob` (job 최상위에서는 잡지 않는다 — 전체 실패는 scheduler 기본 error handler가 ERROR로 기록) | 한 사용자가 실패해도 다음 사용자 처리(`03` §6, §7) | `JOB_FAILED` WARN(`07-security-and-privacy.md` §6.3), 이유 주석 |

`Error`(`OutOfMemoryError` 등)는 어디서도 잡지 않는다. 위 표 외의 broad catch는 ArchUnit `ARCH-17`과 PMD가 실패시킨다.

### 3.11 로깅

| 규칙 | 내용 |
|---|---|
| API | SLF4J만: `private static final Logger log = LoggerFactory.getLogger(Foo.class);` |
| 메시지 | 영어, 소문자 시작, 마침표 없음. 파라미터는 `{}` 치환: `log.info("progress snapshot job finished processed={} durationMs={}", count, ms)` |
| 문자열 연결 | 로그 메시지에 `+` 연결·`String.format` 금지 |
| 구조화 필드 | 감사 이벤트와 key-value가 필요한 로그는 fluent API `log.atInfo().addKeyValue("event", …).log(…)` |
| 금지 데이터 | `07-security-and-privacy.md` §6.1 |
| ERROR | 사람의 조치가 필요한 실패: 예상하지 못한 500, 비동기 task 예외, job 전체 실패, 기동 실패 |
| WARN | 자동 복구되었거나 사용자 영향이 제한된 이상: `JOB_FAILED`, AI 공급자 오류·타임아웃, 가드 재시도 후 실패, `OrphanAsyncTaskJob` 정리 건 |
| INFO | 감사 이벤트, job 요약 1줄, 기동·종료, 일일 요약(`03` §8). 요청마다 INFO를 남기지 않는다 |
| DEBUG | 인증 실패 사유, rate limit 초과, 규칙 계산 중간값. `prod`에서 끈다 |
| `System.out`, `printStackTrace`, JUL | 금지 (`ARCH-07`, PMD) |

### 3.12 자원 관리

- `AutoCloseable`은 try-with-resources로 닫는다(`InputStream`, `Stream<Path>`, `BufferedReader`, JDBC 자원을 직접 쓸 때).
- Spring이 관리하는 bean(`DataSource`, `JsonMapper`, AI 공급자용 `RestClient`, `HttpClient`)은 요청마다 생성·close하지 않는다. 생성은 설정 클래스에서 한 번, 종료는 컨테이너에 맡긴다.
- `Files.lines`, `Files.list`, `Files.walk` 결과 `Stream`은 반드시 try-with-resources.
- 파일 I/O는 `java.nio.file.Files`만 쓴다(`FileInputStream`/`FileOutputStream` 금지, PMD `AvoidFileStream`).
- `ExecutorService`는 `common.async.AsyncConfig`의 bean만 쓴다. 코드에서 `Executors.new…`로 만들지 않는다.

---
## 4. Spring

### 4.1 Controller

- Controller는 `@Valid` 요청 검증, `CurrentUser` 전달, application 결과 → `*Response` 매핑, HTTP status 지정만 한다(`03` §4.4).
- `if`로 비즈니스 분기하지 않는다. repository·`EntityManager`·다른 모듈 서비스를 직접 호출하지 않는다.
- 모든 인증 `POST` handler는 `@RequestHeader("Idempotency-Key") String idempotencyKey`를 받아 `IdempotencyService.execute`로 감싼다. 예외: `POST /plans/{planId}/replan/preview`는 헤더를 받지 않고(보내도 무시) `IdempotencyService`를 거치지 않는다(`05-api-spec.md` §1.7). 파라미터 순서는 `CurrentUser`, `Idempotency-Key`, path/query, `@Valid @RequestBody` (`05` §1.4.3).
- 경로 변수는 `UUID`, `LocalDate` 등 타입으로 받는다. 변환 실패는 400 `VALIDATION_FAILED`(field code `TYPE_MISMATCH`), enum이면 400 `UNKNOWN_ENUM_VALUE` (`03` §7).
- 반환 타입은 `ResponseEntity<XxxResponse>` 또는 `XxxResponse`. 202 응답은 `ResponseEntity.accepted()`.
- `@RequestMapping("/api/v1/…")`은 클래스에 두고, 메서드에는 상대 경로만 쓴다.

### 4.2 `@Transactional` 배치 (T-1 ~ T-6)

| # | 규칙 |
|---|---|
| TX-1 | `@Transactional`은 **application 패키지 `@Service` 클래스의 public 메서드**에만 붙인다. 예외 두 곳: `integration.ai.log.AiCallLogWriter`(쓰기 메서드, `REQUIRES_NEW`), `common.idempotency.IdempotencyService`(TX-4). controller, domain, repository 인터페이스, `*Task`, `*Job` 클래스·메서드에는 붙이지 않는다 (T-1) |
| TX-2 | `*QueryService` 클래스는 클래스 레벨 `@Transactional(readOnly = true)`. 조회 전용 메서드는 모두 `readOnly = true` |
| TX-3 | `AiGateway`를 호출하는 **그 메서드**에는 `@Transactional`을 붙이지 않고, `AiGateway`/`AiProvider` 필드를 가진 클래스에는 **클래스 레벨** `@Transactional`을 붙이지 않는다(T-2, ArchUnit `ARCH-10`과 같은 범위). 같은 클래스의 다른 메서드는 `@Transactional`을 쓸 수 있다. 동기 AI는 **같은 `*Service` 안의 한 메서드**에서 `TransactionTemplate`으로 tx1 → AI → tx2를 나눈다(별도 bean을 만들지 않는다 — ARCH-20 이름 규칙에 걸린다) |
| TX-4 | 다른 모듈 서비스 호출은 기본 전파(REQUIRED)로 참여한다(T-3). `REQUIRES_NEW`는 두 곳만 허용: `integration.ai.log.AiCallLogWriter`의 쓰기 메서드(`BUDGET_BLOCKED` 포함, `17-ai-integration.md` §5.5), `IdempotencyService`의 record 선삽입(`03` §5.4). `NESTED`, `NOT_SUPPORTED`, `NEVER` 금지 |
| TX-5 | 같은 클래스 안에서 `@Transactional` 메서드를 `this.`로 호출해 트랜잭션을 기대하지 않는다(프록시 우회). 분리가 필요하면 다른 bean으로 옮기거나 `TransactionTemplate`을 쓴다 |
| TX-6 | `private`, `protected`, `final` 메서드에 `@Transactional` 금지 |
| TX-7 | `rollbackFor` 지정 금지(checked 예외를 쓰지 않으므로 기본값으로 충분) |
| TX-8 | 응답 DTO는 트랜잭션 안에서 만든다(T-4). 트랜잭션 밖으로 entity를 반환하지 않는다 |
| TX-9 | `@Version` 충돌과 unique 위반 변환은 `GlobalExceptionHandler`와 도메인별 변환 코드에서 한다(T-5, T-6). 서비스에서 `DataIntegrityViolationException`을 잡을 때는 constraint 이름으로 구분해 도메인 409 코드로 바꾸고, 그 외에는 다시 던진다 |
| TX-10 | flush 순서가 필요한 곳(`ReplanService` SUPERSEDED 후, `TodayPlanService` PLANNED 삭제 후, 새 세션 시작 전 기존 세션 ABANDONED 후)은 `entityManager.flush()`를 명시하고 이유 주석을 단다 |

동기 AI 패턴:

```java
public HintResponse requestHint(CurrentUser user, UUID attemptId, HintCommand command) {
    HintContext context = tx.execute(status -> hintPreparation.prepare(user.userId(), attemptId, command));
    AiResult<HintOutput> result = aiGateway.call(context.toAiRequest()); // no transaction here (T-2)
    return tx.execute(status -> hintCompletion.complete(user.userId(), context, result));
}
```

`AiGateway.call`은 시작 시 `TransactionSynchronizationManager.isActualTransactionActive()`가 `true`면 `IllegalStateException`을 던진다(런타임 이중 방어).

### 4.3 이벤트와 비동기

| 규칙 | 내용 |
|---|---|
| 모듈 간 역방향 알림 | `ApplicationEventPublisher.publishEvent(record)` (`03` §2.2 규칙 2). 이벤트 타입은 발행 모듈 `domain` 패키지의 `record`, 이름은 과거형 `*Recorded`, `*Requested`, `*Completed` |
| 동기 리스너 | `@EventListener` — 같은 트랜잭션에서 실행(`SkillStateUpdater`). 예외는 전체 롤백 |
| 비동기 AI | `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async("aiTaskExecutor")`. **`infrastructure` 패키지의 `*Task` 클래스에서만** (ArchUnit `ARCH-05`) |
| `@Async` | 위 조합 외 사용 금지. executor 이름 생략 금지 |
| `@Scheduled` | `*Job` 클래스에서만. cron은 `03` §6 값, zone은 UTC |
| task 내부 트랜잭션 | task는 `TransactionTemplate` 또는 application service의 `@Transactional` 메서드 호출로 상태를 저장한다 |

### 4.4 검증

- 요청 record에 Bean Validation 애노테이션을 둔다. 한 필드에 여러 상황별 규칙이 필요하면 **validation group을 쓰지 않고** 요청 record를 나눈다(`CreateReviewItemRequest`, `UpdateReviewItemRequest`).
- 커스텀 constraint는 `common.web.validation`에 둔다(`@Utf8MaxBytes`, `@MaxLines`, `@IanaTimeZone`, `@HttpsUrl`, `@DateRange`).
- DB 조회가 필요한 검증(skill code 존재 등)은 application service에서 하고 400 `VALIDATION_FAILED` + `errors[]`를 담은 `BusinessValidationException`을 던진다.
- `@Validated` 클래스 레벨은 설정 record와 `@RequestParam`·`@PathVariable` 제약이 있는 controller에만 붙인다.

### 4.5 금지

- 필드 주입, setter 주입, `@Autowired` on field
- `@Value`
- `ApplicationContext.getBean` 직접 호출
- `@Primary`를 main 코드에서 사용(테스트 설정에서만 허용)
- `RestTemplate`, `WebClient`, `RestClient`, `java.net.http.HttpClient`를 `integration.ai.deepseek` 밖에서 사용 (`ARCH-19`)
- `spring.jpa.open-in-view=true`

---

## 5. JPA

### 5.1 Entity 설계

| 규칙 | 내용 |
|---|---|
| 위치 | 모듈 `domain` 패키지 (`ARCH-14`). Spring bean 주입·repository 호출 금지 |
| 생성자 | `protected` 무인자 생성자(JPA 전용) + `private` 전체 생성자 + `public static` factory(`create…`, `start…`, `copyFrom…`). factory가 불변식을 검증한다 |
| ID | `@Id private UUID id;` factory에서 `UUID.randomUUID()`. `@GeneratedValue` 금지 (`04` §1) |
| new 판단 | `@Version`이 있는 entity는 `@Version private @Nullable Long version;`(wrapper, 저장 전 `null`)으로 Spring Data가 new를 판단한다. `@Version`이 없는 entity(append-only: `LearningEvent`, `SkillStateChange`, `ReviewAnswer`, `HintDisclosure`, `ThinkingPatternObservation`, `AiCallLog`, `PlanProgressSnapshot`)는 `Persistable<UUID>`를 구현한다 |
| setter | public setter 금지. 상태 변경은 의미 있는 도메인 메서드(`task.start(now)`, `plan.supersede(now)`, `finding.close(discoveredBy)`)로만. 전이 규칙은 `04` §4 |
| getter | 필요한 것만. 컬렉션은 수정 불가 뷰 반환 |
| equals/hashCode | ID 기준. `instanceof` 패턴 + `getId()` 비교. `hashCode()`는 `id.hashCode()`(ID가 생성 시점에 정해지므로 안정적). entity 클래스는 `final`로 만들지 않는다(프록시) |
| `toString` | 오버라이드하지 않는다 (지연 로딩·민감정보 노출 방지) |
| enum | `@Enumerated(EnumType.STRING)`만 |
| 시각 | `Instant` ↔ `timestamptz`. 날짜 `LocalDate` ↔ `date` |
| 감사 시각 | `BaseTimeEntity`(`createdAt`, `updatedAt`, JPA Auditing)를 상속하거나 도메인 메서드에서 `Instant.now(clock)` 값을 인자로 받아 설정. entity가 `Clock`을 직접 갖지 않는다 |
| 검증 애노테이션 | entity에는 Bean Validation을 붙이지 않는다(요청 record와 factory에서 검증) |

```java
@Entity
@Table(name = "learning_task")
public class LearningTask {

    @Id private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_plan_id")
    private DailyPlan dailyPlan;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    private @Nullable Instant completedAt;

    @Version private @Nullable Long version;

    protected LearningTask() {}

    public void start() {
        if (status != TaskStatus.PLANNED) {
            throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION);
        }
        status = TaskStatus.IN_PROGRESS;
    }
    // …
}
```

### 5.2 연관관계

| 규칙 | 내용 |
|---|---|
| aggregate 경계 | 다른 aggregate는 `UUID` 필드로만 참조한다 (`04` §2). `@ManyToOne`·`@OneToMany`는 같은 aggregate 안에서만 |
| fetch | 모든 `@ManyToOne`, `@OneToOne`에 `fetch = FetchType.LAZY` 명시(기본 EAGER이므로). `@OneToMany`, `@ManyToMany`는 기본 LAZY 유지. `FetchType.EAGER` 금지 |
| cascade | aggregate root → 소유 자식만 `cascade = CascadeType.ALL, orphanRemoval = true`: `LearningPlan → PlanMilestone, PlanSkillTarget`, `DailyPlan → LearningTask`, `ChallengeAttempt → ChallengeSubmission`, `CoachReview → CoachFinding`, `RequirementDoc → RequirementItem`. aggregate 간 cascade 금지 |
| 단순 연결 테이블 | `milestone_skill`, `challenge_skill`, `learning_goal_focus_skill`은 `@ElementCollection Set<UUID>` + `@CollectionTable` + `@Column(name = "skill_id")` |
| 속성 많은 연결 | `plan_skill_target`은 `@Entity PlanSkillTarget` + `@EmbeddedId PlanSkillTargetId(planId, skillId)` + `@MapsId("planId")` `@ManyToOne(LAZY)` |
| seed 복합키 | `role_skill_target`, `skill_prerequisite`는 `@EmbeddedId` record |
| 양방향 | 필요한 경우만. 양쪽을 맞추는 편의 메서드는 root에 둔다 |

### 5.3 조회

| 규칙 | 내용 |
|---|---|
| 목록 | 목록 API는 entity가 아니라 **projection record**(`*View`)를 반환하는 repository 메서드를 쓴다. Spring Data의 class-based DTO projection 또는 JPQL `select new` |
| 명령용 로딩 | 수정 대상 aggregate는 `findByIdAndUserId`로 로딩. 자식이 필요하면 `@EntityGraph(attributePaths = …)` 또는 `join fetch` |
| N+1 | 반복문 안 repository 호출 금지. `in` 조회로 묶는다. `hibernate.default_batch_fetch_size: 50` 설정 |
| 쿼리 작성 | derived query(짧은 이름) 또는 `@Query` JPQL. native는 PostgreSQL 전용 기능(partial index 조건, `ON CONFLICT`, 배열 연산)일 때만 `@Query(nativeQuery = true)` |
| 파라미터 | **named parameter(`:userId`)만**. 문자열 연결·`String.format`으로 쿼리 조립 금지. `EntityManager.createQuery(String)`/`createNativeQuery(String)`는 `infrastructure`에서 상수 문자열로만 (`ARCH-14`) |
| upsert | `INSERT … ON CONFLICT`(사용자 프로비저닝, idempotency record)는 `infrastructure`에서 `JdbcClient` + named parameter |
| 동적 조건 | 필터 조합은 고정 JPQL + `(:status is null or e.status = :status)` 패턴. Criteria·Specification·QueryDSL은 MVP에서 쓰지 않는다 |
| 정렬 | 고정 정렬(JPQL `order by`). 클라이언트 정렬 입력을 받으면 enum → `Sort` 매핑만 |
| 페이지 | cursor는 `where (sortKey, id) < (:sortKey, :id) order by sortKey desc, id desc limit :limitPlusOne` |
| 벌크 수정 | `@Modifying(clearAutomatically = true, flushAutomatically = true)` + JPQL. purge job에서만 |

### 5.4 JSON · 배열 컬럼 (Hibernate 7)

| 컬럼 | Java 타입 | 매핑 |
|---|---|---|
| `jsonb` (`04` §5) | record 또는 `List<record>` | `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition = "jsonb")` |
| `varchar[]` | `List<String>` | `@JdbcTypeCode(SqlTypes.ARRAY)` |
| `uuid[]` | `List<UUID>` | `@JdbcTypeCode(SqlTypes.ARRAY)` |
| enum 배열 (`reason_codes`, `adjusted_by`, `self_review_axes`) | entity 필드는 `List<String>`, getter/도메인 메서드에서 enum `List`로 변환 | 위와 같음 |

- JSON 직렬화는 애플리케이션 `JsonMapper`와 같은 설정(Jackson 3)을 써야 한다. **S0 확인**: Hibernate 7이 Jackson 3 `FormatMapper`를 자동 선택하는지 확인하고, 아니면 `hibernate.type.json_format_mapper`에 Spring `JsonMapper`를 감싼 `FormatMapper` 구현(`common.jpa.JacksonJsonFormatMapper`)을 등록한다.
- JSON record는 저장 전에 Bean Validation을 거친다(`04` §5). 알 수 없는 JSON 필드는 읽을 때 무시한다(과거 데이터 호환).
- `hypersistence-utils` 같은 추가 타입 라이브러리는 쓰지 않는다.

### 5.5 설정 고정값

```yaml
spring:
  jpa:
    open-in-view: false
    hibernate.ddl-auto: validate
    properties:
      hibernate.default_schema: devpilot
      hibernate.jdbc.time_zone: UTC
      hibernate.default_batch_fetch_size: 50
      hibernate.order_updates: true
```

---

## 6. API 계층

| 규칙 | 내용 |
|---|---|
| 요청 DTO | `presentation` 패키지의 `*Request` record. 검증 애노테이션 포함. application 호출 시 `toCommand()`로 `*Command` record 변환 |
| 응답 DTO | `presentation` 패키지의 `*Response` record. `static XxxResponse from(XxxResult result)` 매핑 메서드 |
| application 입출력 | `application` 패키지의 `*Command`(입력), `*Result`(명령 결과), `*View`(조회 결과) record |
| entity 노출 | controller 반환 타입·`*Response` 컴포넌트에 `@Entity` 금지(`ARCH-09`). application 공개 메서드도 entity를 반환하지 않는다 |
| null | 응답 JSON은 null 필드를 생략하지 않는다(`JsonInclude.Include.ALWAYS`). 빈 목록은 `[]` |
| enum | 응답·요청 모두 enum 이름 문자열(`04` §3) |
| 시각·날짜 | `Instant` → `2026-09-17T12:30:00Z`, `LocalDate` → `2026-09-17` |
| 비율·점수 | bp·micro 정수 그대로(`ratioBp`). 소수로 바꿔 내보내지 않는다 |
| 오류 | 예외를 던지고 `GlobalExceptionHandler`가 `ProblemDetail`을 만든다. controller에서 `ProblemDetail`, 오류 `ResponseEntity`를 만들지 않는다 |
| 목록 | `CursorPage<T>` 그대로 응답 (`items`, `nextCursor`) |
| 버전 | 호환되지 않는 변경은 `/api/v2`가 아니라 ADR + 클라이언트 동시 배포로 처리한다(사용자 3명, 앱 1개) |
| OpenAPI | springdoc 애노테이션은 설명이 필요한 곳에만. 변경 시 `docs/api/openapi.yaml` 스냅샷 갱신 |

---

## 7. 패키지 · 클래스 명명

패키지 구조는 `03` §2.3, 핵심 클래스 이름은 `03` §3이 기준이다.

| 접미사 | 패키지 | 역할 | 예 |
|---|---|---|---|
| `*Controller` | `presentation` | `@RestController` | `TodayController` |
| `*Request` | `presentation` | 요청 record | `GenerateTodayRequest` |
| `*Response` | `presentation` | 응답 record | `TodayResponse` |
| `*Service` | `application` | 명령 use case, 트랜잭션 경계 | `TodayPlanService` |
| `*QueryService` | `application` | 조회 전용, 다른 모듈에 공개 | `ReviewQueryService` |
| `*Command` | `application` | 서비스 입력 record | `ReplanCommand` |
| `*Result` | `application` | 명령 결과 record | `ReplanResult` |
| `*View` | `application`, `infrastructure` | 조회 결과·projection record | `DueReviewView` |
| (명사) | `domain` | entity, 값 객체 | `LearningPlan`, `HintLevel` |
| `*Policy` | `domain` | 판단 규칙 (순수 Java) | `HintLadderPolicy` |
| `*Calculator` | `domain`, `common.time` | 수치 계산 규칙 (순수 Java) | `StudyBudgetCalculator` |
| `*Evaluator`, `*Scheduler`, `*Selector`, `*Scorer`, `*Resolver`, `*Classifier`, `*Allocator`, `*Rules`, `*Templates` | `domain` | `03` §3.2의 규칙 클래스 이름 그대로 | `DeadlineRiskEvaluator` |
| `*Recorded`, `*Requested`, `*Completed` | `domain` | 이벤트 record | `CoachReviewRequested` |
| `*Exception` | `common.error`, 모듈 `domain` | 예외 | `NotFoundException` |
| `*Repository` | `infrastructure` | Spring Data 인터페이스 | `ReviewItemRepository` |
| `*Task` | `infrastructure` | 비동기 AI 작업 | `CoachAnalysisTask` |
| `*Job` | `infrastructure`, `common` | `@Scheduled` 작업 | `AccountDeletionJob` |
| `*Config` | `common.*` | `@Configuration` | `SecurityConfig` |
| `*Properties` | `common.config` | 설정 record | `DevPilotProperties` |
| `*Filter` | `common.web`, `common.security` | servlet filter | `TraceIdFilter` |
| `*Guard` | `integration.ai.guard`, `integration.ai.budget` | AI 출력·예산 검사 | `VerificationGuard` |
| `*Registry` | `application` | 기동 시 등록되는 메모리 조회 저장소 | `PlanTemplateRegistry` |
| `*Provider`, `*Counter` | `application` | `03` §2.2 port 구현 | `StudyHistoryProvider` |
| `*Test`, `*IntegrationTest`, `*FlowTest`, `*ArchTest` | test | `09-test-and-quality.md` §3 | `PlannerScoringTest` |

- 패키지 이름은 소문자 한 단어(`common.idempotency`). 밑줄·대문자 금지.
- `Util`, `Utils`, `Helper`, `Manager`, `Common` 접미사 클래스 금지. 역할이 드러나는 이름을 쓴다.
- interface에 `I` 접두사, 구현에 `Impl` 접미사 금지. 구현이 하나뿐이면 interface를 만들지 않는다(예외: `03`에 정의된 port — `AiProvider`, `AuthenticatedUserResolver`, `ReviewSchedulingStrategy`, `UserTimeSettingsProvider`, `AiPendingJobCounter`, `StudyHistoryProvider`, `RequirementCoverageProvider`). port 구현 클래스 이름은 `*Provider`/`*Counter`로 끝나거나 `03` §3.2에 적힌 서비스 이름(예: `UserProvisioningService`)을 쓴다.

---
## 8. Flutter / Dart

### 8.1 폴더 구조

```text
app/lib/
├── main.dart                        # ProviderScope + DevPilotApp만
├── app/
│   ├── devpilot_app.dart            # MaterialApp.router, theme, localization
│   └── router.dart                  # go_router 설정, 인증 redirect
├── l10n/                            # app_ko.arb, 생성된 AppLocalizations (Flutter gen-l10n 기본 위치, 18 §6.5)
├── core/
│   ├── api/                         # Dio 생성, 인터셉터, ApiException, CursorPage
│   ├── auth/                        # AuthGateway(AUTH_MODE=dev: POST /api/v1/dev/token / supabase: Later), 세션 provider
│   ├── config/                      # AppConfig (--dart-define-from-file 값)
│   ├── theme/                       # ColorScheme, TextTheme, spacing 상수
│   └── widgets/                     # 공용 위젯 (AsyncValueView, ErrorBanner, SkeletonBox, MarkdownView)
└── features/<feature>/
    ├── data/                        # <Feature>Repository(API 호출), freezed API 모델
    ├── domain/                      # 화면 무관 상태 모델·순수 로직 (필요할 때만)
    └── presentation/                # <Name>Screen, 위젯, <Name>Controller(Notifier)
```

- feature 이름: `onboarding`, `today`, `review`, `training`, `coach`, `plan`, `skill`, `dashboard`, `evidence`, `weekly`, `radar`, `settings`, `project`, `rubber_duck`, `reading`. backend 모듈 이름과 맞춘다(Dart 파일·디렉터리 이름은 snake_case라 `rubberduck` 모듈은 `rubber_duck`로 쓴다).
- feature 간 import는 다른 feature의 `data` 모델과 repository provider만 허용한다. 다른 feature의 `presentation`을 import하지 않는다(라우팅은 `app/router.dart`에서만).
- 빈 폴더(`domain/` 등)는 만들지 않는다.
- 파일 이름 snake_case, 한 파일에 public 위젯 1개.

### 8.2 Riverpod (DEC-10)

- `riverpod_annotation` + `riverpod_generator`의 `@riverpod`/`@Riverpod(keepAlive: true)`로 provider를 선언한다. 수기 `Provider(...)` 선언은 쓰지 않는다.

| 종류 | 선언 | 생성 이름 | scope |
|---|---|---|---|
| 인프라 (Dio, AuthGateway, AppConfig) | 함수 `@Riverpod(keepAlive: true) Dio dio(Ref ref)` | `dioProvider` | 앱 전체 유지 |
| Repository | `@Riverpod(keepAlive: true) TodayRepository todayRepository(Ref ref)` | `todayRepositoryProvider` | 앱 전체 유지 |
| 화면 상태 (조회 + 명령) | 클래스 `@riverpod class TodayController extends _$TodayController` (`build()`가 `Future<TodayState>` 반환) | `todayControllerProvider` | autoDispose (화면 이탈 시 해제) |
| ID별 상태 | family 인자: `build(String attemptId)` | `challengeAttemptControllerProvider(attemptId)` | autoDispose |
| 파생 값 | 함수 `@riverpod bool hasDueReviews(Ref ref)` | `hasDueReviewsProvider` | autoDispose |

- 클래스 이름: 화면 상태는 `<Screen>Controller`, repository는 `<Feature>Repository`. `Bloc`, `ViewModel`, `Manager` 이름 금지.
- 위젯 `build`에서는 `ref.watch`, 콜백에서는 `ref.read(...notifier)`만 쓴다. `build`에서 `ref.read` 금지.
- 명령 메서드(`generate`, `answer`, `submit`)는 controller에 두고 `AsyncValue.guard`로 상태를 갱신한다. 명령마다 `Idempotency-Key`를 controller가 만든다(§8.4).
- 로그인 사용자 변경 시 사용자 데이터 provider는 `ref.watch(currentSessionProvider)`로 의존해 자동 폐기되게 한다.
- `ProviderContainer` override는 테스트와 `main.dart`의 설정 주입에서만 쓴다.

### 8.3 API 모델 (freezed, DEC-12)

- `features/<feature>/data/models/`에 OpenAPI 스냅샷(`docs/api/openapi.yaml`)을 기준으로 수기 작성한다.
- 모든 모델: `@freezed` + `fromJson`/`toJson`(`json_serializable`). `dynamic`, `Map<String, dynamic>` 필드 금지.
- JSON null 필드는 Dart nullable 타입으로 받는다. 서버가 null을 명시하므로 `includeIfNull`은 기본값을 유지한다.
- enum: 모든 API enum에 마지막 값 `unknown`을 둔다. JSON 값은 `@JsonValue('PLANNED')`로 서버 이름과 1:1. 모델 필드에는 `@JsonKey(unknownEnumValue: TaskStatus.unknown)`를 붙인다. `List<Enum>` 필드도 같다.

```dart
enum TaskStatus {
  @JsonValue('PLANNED') planned,
  @JsonValue('IN_PROGRESS') inProgress,
  @JsonValue('COMPLETED') completed,
  @JsonValue('SKIPPED') skipped,
  @JsonValue('DEFERRED') deferred,
  unknown,
}

@freezed
abstract class LearningTaskModel with _$LearningTaskModel {
  const factory LearningTaskModel({
    required String id,
    @JsonKey(unknownEnumValue: TaskStatus.unknown) required TaskStatus status,
    required int estimatedMinutes,
    required int version,
    String? skillCode,
  }) = _LearningTaskModel;

  factory LearningTaskModel.fromJson(Map<String, Object?> json) =>
      _$LearningTaskModelFromJson(json);
}
```

- 요청 모델에 `unknown`을 넣어 보내지 않는다. repository가 `assert(status != TaskStatus.unknown)`로 막는다.
- `unknown` 값을 받은 화면은 해당 항목을 "앱 업데이트가 필요해요" 상태로 표시하고 앱을 멈추지 않는다.
- 생성 파일(`*.g.dart`, `*.freezed.dart`)은 커밋하지 않는다. CI와 로컬에서 `dart run build_runner build --delete-conflicting-outputs`로 만든다.

### 8.4 Dio 인터셉터

`core/api/dio_provider.dart`에서 아래 **순서**로 추가한다.

| 순서 | 인터셉터 | onRequest | onResponse / onError |
|---|---|---|---|
| 1 | `TraceIdInterceptor` | `X-Trace-Id`가 없으면 32자 소문자 hex 생성 | 응답 `X-Trace-Id`를 `RequestOptions.extra['traceId']`에 보관 |
| 2 | `AuthTokenInterceptor` | `AuthGateway.currentAccessToken`으로 `Authorization: Bearer` 설정. 세션 없으면 요청하지 않고 `ApiException(code: 'AUTHENTICATION_REQUIRED')` | 401이면 `refreshSession()` 1회 후 같은 요청 재시도(같은 `Idempotency-Key`, `extra['authRetried']=true`). 다시 401이면 로컬 sign-out → 로그인 화면 |
| 3 | `IdempotencyKeyInterceptor` | method가 `POST`이면 `extra['idempotencyKey']`를 `Idempotency-Key` 헤더로 설정. 값이 없으면 debug에서 `assert` 실패, release에서 UUID v4 생성. `/replan/preview`는 생략 | — |
| 4 | `ProblemDetailsInterceptor` | — | `application/problem+json` 응답을 `ApiException(status, code, detail, traceId, errors)`로 변환. 네트워크 오류는 `code: 'NETWORK_ERROR'`(클라이언트 전용), timeout은 `code: 'CLIENT_TIMEOUT'` |

- Idempotency-Key 수명: 사용자 행동 1회(버튼 탭 1회)마다 controller가 `Uuid().v4()`를 만들고, 그 명령이 성공하거나 재시도 불가 오류(4xx, `IDEMPOTENCY_IN_PROGRESS` 제외)로 끝날 때까지 같은 키를 재사용한다(`03` §5.4).
- 자동 재시도 인터셉터는 두지 않는다. 사용자가 "다시 시도"를 누르면 controller가 보관한 같은 키로 재요청한다. 예외: `IDEMPOTENCY_IN_PROGRESS`(409)를 받으면 controller가 1초 후 같은 키로 최대 5회 다시 보낸다(`05-api-spec.md` §1.7).
- 재생 응답(`Idempotent-Replayed: true`)은 최초 처리 시점 스냅샷이다. 최신 상태는 GET으로 다시 읽는다. `POST /me/calendar-token` 재생 응답은 `feedUrl = null`이므로 화면은 "새로 발급" 버튼을 보여 주고 새 키로 요청한다.
- timeout: connect 10초, receive 30초(동기 AI 20초 + 여유). polling은 `GET`이므로 영향 없음.
- 요청·응답 body 로깅 인터셉터(`LogInterceptor`)는 debug 빌드에서도 body를 끈다(`requestBody: false, responseBody: false`).

### 8.5 오류 → 화면 문구

- 클라이언트 분기는 **`code`로만** 한다. HTTP status나 `detail` 문자열로 분기하지 않는다.
- `core/api/error_message_mapper.dart`의 `String messageFor(ApiException e, AppLocalizations l10n)`:
  1. `code`가 매핑표에 있으면 ARB 문구 (`AI_DAILY_LIMIT_EXCEEDED` → `l10n.errorAiDailyLimitExceeded`)
  2. 없으면 서버 `detail`(사용자용 한국어 문장, `05-api-spec.md` §1.3)
  3. `detail`도 없으면 `l10n.errorUnknown`
- 특정 코드는 화면 동작으로 처리한다: `ONBOARDING_REQUIRED` → 온보딩 route, `TODAY_NOT_GENERATED` → 생성 입력 화면, `RECENT_LOGIN_REQUIRED` → 재로그인 흐름, `CONCURRENT_MODIFICATION` → 새로고침 안내 후 재조회, `USER_NOT_ALLOWED` → 초대 필요 화면.
- `traceId`는 오류 화면의 "문제 신고" 펼침 영역에만 보여 준다.

### 8.6 l10n (ARB)

| 규칙 | 내용 |
|---|---|
| 파일 | `lib/l10n/app_ko.arb` (MVP 한국어만). 생성 파일 `lib/l10n/app_localizations*.dart`(커밋하지 않음). `l10n.yaml`: `arb-dir: lib/l10n`, `template-arb-file: app_ko.arb`, `output-class: AppLocalizations` |
| 키 형식 | lowerCamelCase `<feature><Element><Purpose>`. 공용은 `common`, 오류는 `error` 접두사 |
| 예 | `todayGenerateButton`, `todayReasonTitle`, `reviewRevealAnswerButton`, `coachConsentCheckbox`, `commonRetryButton`, `errorAiDailyLimitExceeded` |
| 오류 키 | `error` + 오류 코드의 PascalCase (`RATE_LIMITED` → `errorRateLimited`) |
| 설명 | 모든 키에 `@key.description` 작성 (어느 화면의 무엇인지) |
| placeholder | `{minutes}` 형식 + `placeholders` 타입 선언. 문자열 연결로 문장 조립 금지 |
| 하드코딩 | 위젯 안 사용자 노출 한국어 리터럴 금지. 예외 없음 |

### 8.7 위젯

| 규칙 | 내용 |
|---|---|
| 비즈니스 로직 | 위젯에 두지 않는다. API 호출, 날짜 계산, 상태 전이 판단, 점수 계산은 controller·repository·domain에 |
| 크기 | `build` 메서드 60줄 이하, 파일 300줄 이하. 넘으면 private 위젯 **클래스**로 분리한다(`Widget _buildX()` 헬퍼 함수 금지) |
| const | 가능한 곳은 `const` 생성자 |
| 비동기 상태 | `AsyncValue`는 `core/widgets/async_value_view.dart`로 loading(skeleton)·error·data를 일관되게 표시 |
| 접근성 | 탭 대상 최소 44×44 logical px, 360px 폭에서 overflow 없음, 색 외 텍스트·아이콘으로 상태 표시 (NFR-08) |
| Markdown | `core/widgets/markdown_view.dart` 하나만 사용 (`07-security-and-privacy.md` §9.5) |
| `BuildContext` | `await` 뒤에 쓰기 전에 `context.mounted` 확인 |
| 플랫폼 API | `dart:html`, `package:web` DOM 조작, `HtmlElementView`에 사용자 문자열 삽입 금지 |

### 8.8 Lint · 포맷

- `dart format`(Dart 3.7+ tall style), page width 100. `analysis_options.yaml`의 `formatter: page_width: 100`.
- analyzer 설정은 §11.7.
- `// ignore:` 주석은 이유를 같은 줄에 적는다: `// ignore: invalid_annotation_target — freezed JsonKey`. 파일 단위 `ignore_for_file`은 생성 파일 외 금지.

---

## 9. 주석

- 주석은 **왜**를 쓴다. 코드가 말하는 내용을 반복하지 않는다.
- 언어는 영어. 문서 참조는 `// See 06-learning-engine-rules.md §5.6` 형식.
- 규칙 클래스의 public 메서드에는 Javadoc 1~3줄로 **규칙 ID와 문서 절**을 적는다.

```java
/** Computes review and main-task minutes (06 §5.6). All arithmetic is integer (N-1). */
public TimeAllocation allocate(TimeAllocationInput input) { … }
```

- `TODO`는 `// TODO(BL-TDY-07): …`처럼 백로그 ID를 붙인다. ID 없는 TODO는 PR 리뷰에서 거절한다.
- 주석 처리된 코드를 커밋하지 않는다.
- 허용된 broad catch, flush 호출, `REQUIRES_NEW`, 보안 예외 경로에는 이유 주석이 필수다.

```java
// bad
// save the user
userRepository.save(user);

// good
// Replan creates a new plan version so historical daily plans keep pointing at the old milestones.
```

---

## 10. Git

### 10.1 브랜치

| 형식 | 용도 | 예 |
|---|---|---|
| `main` | 배포용. 태그 `v0.<sprint>.<patch>` push가 릴리스 workflow를 실행 | — |
| `developer` | 개발 통합 브랜치. 기능 브랜치는 여기서 따고 여기로 머지 | — |
| `feature/BL-<EPIC>-<nn>-<short>` | 백로그 기능 | `feature/BL-TDY-03-generate-today` |
| `fix/BL-<EPIC>-<nn>-<short>` 또는 `fix/<short>` | 버그 | `fix/review-horizon-cap` |
| `chore/<short>` | 빌드·의존성·설정 | `chore/bump-boot-4.1.2` |
| `docs/<short>` | 문서만 | `docs/security-csp` |
| `refactor/<short>`, `test/<short>` | 동작 변경 없음 | `test/isolation-catalog` |

- `<short>`는 영어 소문자 kebab-case 3~5단어. 한 브랜치 = 한 백로그 항목 또는 한 인프라 관심사.
- 머지 흐름: `feature/*` → `developer`는 **squash merge**, `developer` → `main`은 **merge commit**(릴리스 단위 보존).
- `main`·`developer` 모두 직접 push 금지(ruleset). 두 브랜치로 향하는 PR에서 필수 체크 4개(`security`, `backend`, `app`, `content`)가 green이어야 머지할 수 있다.

### 10.2 커밋 메시지 (Conventional Commits, 영어)

```text
<type>(<scope>): <imperative summary, lowercase, no period> (BL-XXX-nn)

<body: why, not what — optional, wrap at 72>

<footer: BREAKING CHANGE: … / Refs: ADR-0xx — optional>
```

- `type`: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `build`, `ci`, `perf`
- `scope`: backend 모듈 이름(`today`, `review`, `integration-ai`, `common`), `app`, `infra`, `content`, `docs`
- summary 72자 이하. 예: `feat(today): allocate review and main task minutes (BL-TDY-04)`
- squash 머지 제목도 같은 형식이다.

### 10.3 PR 템플릿 (`.github/pull_request_template.md`)

```markdown
## Changed
- (what changed, 3–7 bullets; link backlog ID and AC IDs)

## Tests
- Commands run and results (e.g. `./gradlew check` ✅, `fvm flutter test` ✅)
- New/updated tests: (class names, vectors)

## Security impact
- [ ] None
- [ ] Auth/authz, input validation, logging, secrets, AI data, dependencies (explain)

## Docs updated
- [ ] Not needed
- [ ] docs/…md §… / OpenAPI snapshot / ADR-0xx / migration V…

## Decisions needed
- (questions for the owner; "None" if none)

## Known limitations / Next
- (out-of-scope items with backlog IDs)
```

- 항목을 지우지 않는다. 해당 없으면 "None"을 적는다.
- AI 에이전트의 작업 보고도 같은 6개 제목을 쓴다.

---
## 11. 자동 강제 도구

### 11.1 도구 목록

| 도구 | 대상 | Gradle/CLI task | 실패 조건 | 설정 파일 |
|---|---|---|---|---|
| Spotless + google-java-format (AOSP) | `backend/src/*/java/**/*.java` | `spotlessCheck` (수정: `spotlessApply`) | 포맷 차이 1건 이상 | `backend/build.gradle.kts` |
| Checkstyle | main + test Java | `checkstyleMain`, `checkstyleTest` | error 1건 이상 (warning 등급 사용 안 함) | `backend/config/checkstyle/checkstyle.xml` |
| PMD 7 | main Java | `pmdMain` (`pmdTest` 비활성) | 위반 1건 이상 | `backend/config/pmd/ruleset.xml` |
| SpotBugs | main bytecode | `spotbugsMain` (`spotbugsTest` 비활성) | 보고 1건 이상 | `backend/config/spotbugs/exclude.xml` |
| ArchUnit | main bytecode (테스트로 실행) | `test` (tag `unit`) | 규칙 위반 1건 이상 | `backend/src/test/java/com/devpilot/architecture/` |
| javac lint | main | `compileJava` | 경고 1건 이상 (`-Werror`) | `build.gradle.kts` |
| `dart format` | `app/lib`, `app/test`, `app/integration_test` | `dart format --output=none --set-exit-if-changed .` | 변경 필요 파일 1개 이상 | `analysis_options.yaml` (`formatter`) |
| Dart analyzer | `app/` | `fvm flutter analyze --fatal-infos --fatal-warnings` | info 포함 1건 이상 | `app/analysis_options.yaml` |
| 로마자 식별자 검사 (Dart) | `app/lib/**/*.dart` (생성 파일 제외) | `tool/check_identifiers.sh` | §2.2 정규식 일치 1건 이상 | 같은 스크립트 |

도구 버전은 `backend/gradle/libs.versions.toml`과 `app/pubspec.yaml`에 고정한다. **S0 확인(SP-4)**: 각 도구의 Java 25 bytecode 지원 버전을 확인하고, 지원하지 않는 도구가 있으면 DEC-02에 따라 Java 21로 내린다.

### 11.2 Spotless

**기준 설정 파일은 `repo-seed/backend/build.gradle.kts`의 `spotless` 블록이다**(Gradle 9.7.1·Boot 4.1.1·Temurin 25.0.4로 실제 빌드 검증, 2026-09-17). 아래는 그 블록 그대로다. `formatAnnotations()`, `removeUnusedImports()`, `*.gradle.kts` ktlint는 S0에서 빌드가 통과하면 추가한다(선택).

```kotlin
spotless {
    java {
        target("src/*/java/**/*.java")
        // DEC-09: google-java-format AOSP 스타일(4칸), 100열
        googleJavaFormat(libs.versions.google.java.format.get()).aosp().reflowLongStrings()
        trimTrailingWhitespace()
        endWithNewline()
        toggleOffOn()
    }
}
```

- import 정렬은 google-java-format에 맡긴다(`importOrder()`를 따로 쓰지 않음).
- IDE: IntelliJ google-java-format plugin을 AOSP 모드로 켠다. 결과가 다르면 Gradle 결과가 기준이다.

### 11.3 Checkstyle

포맷 규칙(들여쓰기, 줄 길이, 공백)은 쓰지 않는다. **명명·import·구조 규칙만** 쓴다. 모든 규칙 severity는 `error`.

| 모듈 | 설정 |
|---|---|
| `PackageName` | `format="^com\.devpilot(\.[a-z][a-z0-9]*)*$"` |
| `TypeName` | `format="^[A-Z][a-zA-Z0-9]*$"` |
| `MethodName` | `format="^[a-z][a-zA-Z0-9]*$"` |
| `MemberName` | `format="^[a-z][a-zA-Z0-9]*$"` |
| `ParameterName` | `format="^[a-z][a-zA-Z0-9]*$"` |
| `LambdaParameterName` | `format="^([a-z][a-zA-Z0-9]*\|_)$"` |
| `LocalVariableName` | `format="^([a-z][a-zA-Z0-9]*\|_)$"`, `allowOneCharVarInForLoop=true` |
| `LocalFinalVariableName` | `format="^[a-z][a-zA-Z0-9]*$"` |
| `CatchParameterName` | `format="^([a-z][a-zA-Z0-9]*\|_)$"` |
| `PatternVariableName` | `format="^[a-z][a-zA-Z0-9]*$"` |
| `RecordComponentName` | `format="^[a-z][a-zA-Z0-9]*$"` |
| `StaticVariableName` | `format="^[a-z][a-zA-Z0-9]*$"` |
| `ConstantName` | `format="^log$\|^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$"` (`private static final Logger log`만 소문자 허용) |
| `ClassTypeParameterName`, `MethodTypeParameterName`, `RecordTypeParameterName`, `InterfaceTypeParameterName` | `format="^[A-Z][A-Z0-9]*$"` |
| `AbbreviationAsWordInName` | `allowedAbbreviationLength=1`, `ignoreStatic=true`, `ignoreFinal=true`, `tokens=CLASS_DEF, INTERFACE_DEF, ENUM_DEF, RECORD_DEF, METHOD_DEF, VARIABLE_DEF, PARAMETER_DEF, RECORD_COMPONENT_DEF` |
| `IllegalIdentifierName` | 기본값 (`var`, `record`, `yield`, `sealed`, `permits` 등 금지) |
| `AvoidStarImport` | 기본값 |
| `UnusedImports` | `processJavadoc=true` |
| `IllegalImport` | `illegalPkgs="sun, com.sun, lombok, junit.framework, org.hamcrest, org.joda.time"`, `illegalClasses="org.junit.Assert, org.junit.jupiter.api.Assertions, java.util.Date, java.sql.Timestamp, java.text.SimpleDateFormat, org.springframework.beans.factory.annotation.Value, org.springframework.lang.Nullable, org.springframework.lang.NonNull, javax.annotation.Nullable, jakarta.annotation.Nullable"` |
| `OuterTypeNumber` | `max=1` |
| `OneTopLevelClass` | 기본값 |
| `RegexpSinglelineJava` id=`RomanizedKorean` | `format`=§2.2 첫 번째 정규식, `ignoreComments=true`, `message="Romanized Korean identifier is not allowed (08 §2.2)"` |
| `RegexpSinglelineJava` id=`YnSuffix` | `format="(?<=[a-z0-9])Yn\b"`, `ignoreComments=true` |
| `SuppressWarningsHolder` + `SuppressWarningsFilter` | 쓰지 않는다 (억제 금지) |

test 소스용 설정(`checkstyle-test.xml`)은 `IllegalImport`의 `illegalPkgs`에서 `com.sun`을 빼고 `sun`만 금지한다(`TestJwksServer`가 `com.sun.net.httpserver`를 쓰기 때문). 그 외 test 소스는 같은 설정을 쓰되 `MethodName`을 `^[a-z][a-zA-Z0-9]*$`로 유지한다(`should…When…`는 camelCase이므로 통과).

### 11.4 PMD 7

`backend/config/pmd/ruleset.xml`은 카테고리 전체를 가져오지 않고 아래 규칙만 참조한다. 규칙 이름은 PMD 7.x 기준이며, S0에 `./gradlew pmdMain`으로 ruleset 로딩 오류가 없는지 확인한다.

| 카테고리 | 규칙 | 설정 |
|---|---|---|
| bestpractices | `AvoidPrintStackTrace`, `PreserveStackTrace`, `SystemPrintln`, `UnusedPrivateField`, `UnusedPrivateMethod`, `UnusedLocalVariable`, `UnusedAssignment`, `MissingOverride`, `LiteralsFirstInComparisons`, `UseCollectionIsEmpty`, `ForLoopCanBeForeach`, `AvoidReassigningParameters`, `AvoidUsingHardCodedIP` | 기본값 |
| design | `AvoidCatchingGenericException` | `violationSuppressXPath="//ClassDeclaration[ends-with(@SimpleName,'Task') or ends-with(@SimpleName,'Job')]"` (§3.10 허용 위치) |
| design | `AvoidThrowingRawExceptionTypes`, `AvoidThrowingNullPointerException`, `SignatureDeclareThrowsException`, `ExceptionAsFlowControl`, `AvoidRethrowingException`, `SimplifyBooleanReturns` | 기본값 |
| design | `CognitiveComplexity` | `reportLevel=15` |
| design | `ExcessiveParameterList` | `minimum=8` |
| errorprone | `AvoidCatchingThrowable`, `AvoidCatchingNPE`, `EmptyCatchBlock`, `CloseResource`, `CompareObjectsWithEquals`, `EqualsNull`, `ReturnEmptyCollectionRatherThanNull`, `ImplicitSwitchFallThrough`, `AssignmentInOperand`, `UseEqualsToCompareStrings`, `DoNotTerminateVM`, `DoNotThrowExceptionInFinally`, `ReturnFromFinallyBlock` | `EmptyCatchBlock`: `allowCommentedBlocks=false` |
| multithreading | `NonThreadSafeSingleton`, `UnsynchronizedStaticFormatter`, `DontCallThreadRun` | 기본값 |
| performance | `AvoidFileStream` | 기본값 |
| security | `HardCodedCryptoKey`, `InsecureCryptoIv` | 기본값 |

- `@SuppressWarnings("PMD.…")`는 금지한다. 규칙이 틀렸다고 판단되면 ruleset을 고치는 PR을 만든다.
- `CloseResource`가 Spring 관리 자원에서 오탐하면 `types` 속성으로 대상 타입을 좁힌다.

### 11.5 SpotBugs

| 항목 | 값 |
|---|---|
| `effort` | `max` |
| `reportLevel` | `medium` |
| 대상 | `spotbugsMain`만 |
| 리포트 | HTML(로컬) + SARIF(CI 업로드) |
| 제외 (`exclude.xml`) | `EI_EXPOSE_REP`, `EI_EXPOSE_REP2` (record는 compact constructor에서 방어 복사, entity 컬렉션은 수정 불가 뷰 반환 규칙으로 대체), `CT_CONSTRUCTOR_THROW` (record·factory 검증 예외와 충돌) |
| 제외 금지 | 클래스·패키지 단위 제외, `@SuppressFBWarnings` |

### 11.6 ArchUnit 규칙 카탈로그

- 모든 규칙은 `com.devpilot` main 클래스를 `ImportOption.DoNotIncludeTests`로 가져와 검사한다. 규칙을 약화하거나 `FreezingArchRule`로 기존 위반을 동결하지 않는다.
- 규칙 → 테스트 클래스 매핑은 `09-test-and-quality.md` §7.

| ID | 규칙 | 근거 |
|---|---|---|
| ARCH-01 | 모듈 의존 matrix: `com.devpilot.<module>..`의 클래스는 `03` §2.2 표에 적힌 모듈 외의 `com.devpilot` 모듈 패키지에 의존하지 않는다. 표를 `Map<String, Set<String>>` 상수로 테스트에 옮긴다. `integration.ai`의 패키지는 `com.devpilot.integration.ai..`. `common`의 허용 대상은 **(없음)**이므로 `common.job`의 스케줄 job도 다른 모듈을 직접 참조하지 않는다 — `RetentionCleanupTarget`·`OrphanAsyncTaskSweeper` port로만 호출한다(`03` §2.2 규칙 4) | `03` §2.2 |
| ARCH-02 | 다른 모듈 접근 범위: 모듈 A가 모듈 B를 쓸 때 대상은 (1) `B.application..`의 public 클래스, (2) `B.domain..`의 `enum`·`record` 중 `@Entity`가 아닌 것(공유 enum, 이벤트 record)만. `B.infrastructure..`, `B.presentation..`, `B.domain`의 entity·규칙 클래스 접근 금지. 예외: **여러 모듈이 공유하는 순수 규칙 클래스** `learning.domain.ComebackModePolicy`(today·review), `learning.domain.RubricScorer`(training·review)는 다른 모듈에서 쓸 수 있다(`03` §2.2 끝). `common..`은 모든 하위 패키지 접근 가능 | `03` §2.2 규칙 1·2 |
| ARCH-03 | `integration.ai` 공개 범위: 다른 모듈은 `integration.ai.api..`(`AiProvider`, `AiRequest`, `AiResult`, `GuardContext`, `AiOperation`, `AiCallStatus`, `AiStatus`, `AiUsageSnapshot`, `AiBudgetDecision`, `AiConcurrencyReservation`, `AiPendingJobCounter`), `integration.ai.AiGateway`, `integration.ai.masking.SecretMasker`, `integration.ai.masking.MaskingResult`, `integration.ai.budget.AiBudgetGuard`만 사용. 공급자 HTTP 타입(요청·응답 DTO, `RestClient`)은 `integration.ai.deepseek..` 밖으로 나가지 않는다 | `03` §3.3, §5.3 |
| ARCH-04 | 모듈 내부 계층: `presentation` → `application`, `domain`(enum·record) / `application` → `domain`, `infrastructure` / `infrastructure` → `domain`, `application`(`*Task`·`*Job`, 그리고 repository가 반환하는 `application`의 `*View` projection record만) / `domain` → 같은 모듈 `domain`, ARCH-02가 허용한 다른 모듈 `domain`의 enum·record, `common`만. `presentation`을 의존하는 클래스 없음. `presentation`은 `infrastructure`에 의존하지 않는다(controller → repository 금지) | `03` §2.3, §7 |
| ARCH-05 | `@Async` 메서드는 `..infrastructure..`의 이름이 `Task`로 끝나는 클래스에만 있고 반드시 `@TransactionalEventListener`와 함께 쓴다. `@Scheduled` 메서드는 이름이 `Job`으로 끝나는 클래스에만 | §4.3 |
| ARCH-06 | 필드 주입 금지 (`GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION`) | §3.6 |
| ARCH-07 | `System.out`/`System.err` 접근 금지 (`NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS`), `java.util.logging` 금지 (`NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING`), `Throwable.printStackTrace()` 호출 금지, `Thread.sleep` 호출 금지(예외: `integration.ai.deepseek..` — 공급자 재시도 사이의 `Retry-After` 대기, `17` §5.2. virtual thread라 캐리어를 막지 않는다), 일반 예외(`Exception`, `RuntimeException`, `Throwable`) throw 금지 (`NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS`), Joda-Time 금지 | §3.11 |
| ARCH-08 | 무인자 `now()` 호출 금지: `Instant.now()`, `LocalDate.now()`, `LocalDateTime.now()`, `LocalTime.now()`, `OffsetDateTime.now()`, `ZonedDateTime.now()`, `Year.now()`, `YearMonth.now()`. `ZoneId` 인자 버전도 금지(`Clock` 인자 버전만 허용). `Clock.systemUTC()`, `Clock.systemDefaultZone()`, `System.currentTimeMillis()`는 `common.time.ClockConfig`에서만 | §3.9 |
| ARCH-09 | `@RestController` 클래스는 `..presentation..`에 있고 이름이 `Controller`로 끝난다. 그 public 메서드의 반환 타입과 반환 타입의 제네릭 인자(재귀)에 `@Entity` 클래스가 없다. `*Response` record 컴포넌트 타입에도 `@Entity`가 없다 | §6 |
| ARCH-10 | `@Transactional`(`org.springframework.transaction.annotation`, `jakarta.transaction`)이 붙은 메서드, 또는 그 애노테이션이 붙은 클래스의 메서드는 `AiGateway`, `AiProvider`의 메서드를 호출하지 않는다. `AiGateway`/`AiProvider` 타입 필드를 가진 클래스에는 클래스 레벨 `@Transactional`이 없다 | T-2 |
| ARCH-11 | `UserSkillState`의 레벨 변경 메서드(`applyLevelChange`, `applyDiagnosticLevel`, `deactivateSelfAssessment`, `initializeSelfAssessment`)는 `com.devpilot.skill.application..`에서만 호출한다 | I-12 |
| ARCH-12 | 규칙 클래스(`SkillLevelRules`, `PlanningLevelPolicy`, `HintLadderPolicy`, `StudyBudgetCalculator`, `DeadlineRiskEvaluator`, `ReplanSuggestionPolicy`, `FinalRatingPolicy`, `RuleBasedV1Scheduler`, `DueReviewSelector`, `PlannerScoring`, `TaskProposalPolicy`, `TimeAllocator`, `ReasonTemplates`, `ComebackModePolicy`(learning.domain), `AttemptOutcomeCalculator`, `RubricScorer`(learning.domain), `DiscoveredByResolver`, `MetricsCalculator`, `RequirementFitClassifier`, `PlanTemplatePlacement`)와 그 입력·출력 record는 `org.springframework..`, `jakarta.persistence..`, `org.hibernate..`, `tools.jackson..`, `com.fasterxml.jackson..`, 모듈의 `application`·`infrastructure`에 의존하지 않는다. 클래스에 Spring stereotype 애노테이션이 없다 | `03` §2.3, §3.2 |
| ARCH-13 | ARCH-12의 규칙 클래스는 `double`, `float`, `Double`, `Float` 타입의 필드·매개변수·반환값·지역 호출(`Math.round(double)` 등 floating 오버로드 포함)을 갖지 않는다 | N-1 |
| ARCH-14 | `@Entity`·`@Embeddable`은 `..domain..`과 `com.devpilot.integration.ai..`(`AiCallLog`, `03` §3.3)에만. `org.springframework.data.repository.Repository` 하위 인터페이스는 `..infrastructure..`와 `com.devpilot.integration.ai..`(`AiCallLogRepository`)에만. `EntityManager.createQuery(String…)`, `createNativeQuery(String…)`, `JdbcClient` 사용은 `..infrastructure..`와 `com.devpilot.common.idempotency..`에서만 | §5 |
| ARCH-15 | `@ConfigurationProperties`는 `common.config.DevPilotProperties`에만. `@Value` 사용 금지 | §3.7 |
| ARCH-16 | `LearningEvent`, `SkillStateChange`, `ReviewAnswer`, `HintDisclosure`, `ThinkingPatternObservation`, `AiCallLog`는 `set`으로 시작하는 public 메서드가 없고, `LearningEvent`의 상태 변경 public 메서드는 `invalidate` 하나뿐이다 | I-13 |
| ARCH-17 | `catch` 대상이 `Exception`, `RuntimeException`, `Throwable`인 try-catch 블록(`JavaCodeUnit.getTryCatchBlocks()`)은 이름이 `Task` 또는 `Job`으로 끝나는 클래스에만 있다 | §3.10 |
| ARCH-18 | `com.devpilot` 아래 클래스가 있는 모든 패키지는 `package-info`에 `@NullMarked`가 있다 | §3.3 |
| ARCH-19 | `java.net.http.HttpClient`, `java.net.URL#openConnection`/`openStream`, `java.net.HttpURLConnection`, `org.springframework.web.client.RestTemplate`, `RestClient`, `org.springframework.web.reactive.function.client.WebClient`, `okhttp3..` 사용은 `integration.ai.deepseek..`에서만 | `07-security-and-privacy.md` §1.1 A10 |
| ARCH-20 | 명명: `@RestController` → `*Controller`, `application`의 `@Service` → `*Service` 또는 `*QueryService` 또는 `03` §3에 적힌 이름(`LearningEventRecorder`, `ThinkingPatternRecorder`, `SkillStateUpdater`, `ContentSeeder`, `ContentValidator`, `IdempotencyService`) 또는 `*Registry`(`PlanTemplateRegistry`, `SeedCardRegistry`), `03` §2.2 port(`UserTimeSettingsProvider`, `AiPendingJobCounter`, `StudyHistoryProvider`, `RequirementCoverageProvider`, `RetentionCleanupTarget`, `OrphanAsyncTaskSweeper`)를 구현하는 클래스 → `*Provider`·`*Counter`·`*Sweeper` **또는 `03` §3.2·§3.3 표에 적힌 서비스 이름**(예: `UserProvisioningService`가 `UserTimeSettingsProvider`를, `CoachReviewService`가 `OrphanAsyncTaskSweeper`를, `AiCallLogRetentionService`가 `RetentionCleanupTarget`을 구현), Spring Data 인터페이스 → `*Repository`, `@Configuration` → `*Config`, `Util`/`Utils`/`Helper`/`Manager`/`Impl`로 끝나는 클래스 없음 | §7 |
| ARCH-21 | `@Transactional`은 `..application..` 패키지 클래스(및 `common.idempotency`, `integration.ai.log`)의 public 메서드 또는 클래스에만. 이름이 `QueryService`로 끝나는 클래스는 클래스 레벨 `@Transactional(readOnly = true)` | TX-1, TX-2 |

규칙 정의에서 모듈 목록·예외 클래스 이름은 `03` 문서와 같아야 한다. `03`의 이름이 바뀌면 이 표와 테스트를 같은 PR에서 고친다.

### 11.7 Dart analyzer (`app/analysis_options.yaml`)

**기준 파일은 `repo-seed/app/analysis_options.yaml`이다**(Flutter 3.47 프로젝트에서 설정 로드 검증). 아래는 목표 규칙 요지이며, `riverpod_lint` 등 plugin은 S0에서 Riverpod 버전과 호환을 확인한 뒤 seed 파일에 추가한다. 생성 l10n 파일 경로는 `lib/l10n/app_localizations*.dart`다.

```yaml
include: package:flutter_lints/flutter.yaml

formatter:
  page_width: 100

analyzer:
  language:
    strict-casts: true
    strict-inference: true
    strict-raw-types: true
  errors:
    invalid_annotation_target: ignore   # freezed @JsonKey on factory parameters
    missing_required_param: error
    unawaited_futures: error
  exclude:
    - "**/*.g.dart"
    - "**/*.freezed.dart"
    - "lib/l10n/app_localizations*.dart"
    - "build/**"
  plugins:
    - riverpod_lint   # 사용 방식은 Riverpod 3 문서 기준, S0 확인

linter:
  rules:
    - always_declare_return_types
    - always_use_package_imports
    - avoid_dynamic_calls
    - avoid_print
    - avoid_relative_lib_imports
    - avoid_slow_async_io
    - avoid_type_to_string
    - avoid_web_libraries_in_flutter
    - cancel_subscriptions
    - close_sinks
    - comment_references
    - directives_ordering
    - no_adjacent_strings_in_list
    - only_throw_errors
    - prefer_const_constructors
    - prefer_const_declarations
    - prefer_final_fields
    - prefer_final_locals
    - prefer_single_quotes
    - sort_child_properties_last
    - test_types_in_equals
    - throw_in_finally
    - unawaited_futures
    - unnecessary_await_in_return
    - unnecessary_lambdas
    - unnecessary_statements
    - use_build_context_synchronously
    - use_key_in_widget_constructors
    - use_super_parameters
```

- `require_trailing_commas`는 Dart 3.7+ formatter가 trailing comma를 관리하므로 켜지 않는다.
- `flutter analyze --fatal-infos --fatal-warnings`에서 0건이어야 한다.
- `tool/check_identifiers.sh`: `grep -rnP` 로 §2.2 두 정규식을 `lib/`의 `*.dart`(생성 파일, `l10n` 제외)에 적용하고, 일치하면 파일·줄을 출력하고 exit 1.
