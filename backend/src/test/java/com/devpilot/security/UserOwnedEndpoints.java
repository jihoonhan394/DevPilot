package com.devpilot.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;

/**
 * 격리 테스트 endpoint catalog (docs/09 §9.2). 지금 있는 endpoint만 넣는다(S1·S2와 S3 backend) — 새 endpoint를 만들면
 * 여기에 추가해야 {@code EndpointCatalogCompletenessTest}가 통과한다.
 */
public final class UserOwnedEndpoints {

    private UserOwnedEndpoints() {}

    /** docs/09 §9.1 검증 종류. */
    public enum Kind {
        OWNED_RESOURCE,
        BODY_REFERENCE,
        SCOPED_COLLECTION,
        SHARED_CONTENT,
        ACCOUNT_ACTION
    }

    /** 공용 콘텐츠 조회에 쓰는 테스트 catalog reading key (docs/09 §9.2 SHARED_CONTENT). */
    public static final String SHARED_READING_KEY = "READ.TESTREPO.ORDER_SERVICE.001";

    /** 공용 콘텐츠 조회에 쓰는 테스트 개념 노트 key (docs/05 §21). */
    public static final String SHARED_LESSON_KEY = "LESSON.TESTSPRING.MVC.001";

    /** 그 노트의 첫 단위. */
    public static final String SHARED_UNIT_KEY = SHARED_LESSON_KEY + ".U1";

    /**
     * @param pathVariables A(소유자)의 리소스로 path 변수를 채운다
     * @param body 요청 body. 없으면 null
     */
    public record EndpointCase(
            String id,
            HttpMethod method,
            String pathTemplate,
            Kind kind,
            Function<IsolationFixture, Object[]> pathVariables,
            Function<IsolationFixture, @Nullable Object> body,
            @Nullable String expectedNotFoundCode) {

        @Override
        public String toString() {
            return id + " " + method + " " + pathTemplate;
        }
    }

    /**
     * A가 가진 리소스 id와 요청 body 재료.
     *
     * @param invitedMeVersion B의 현재 {@code app_user.version} (B의 {@code PATCH /me}용)
     * @param taskId A의 오늘 main 과제
     * @param sessionId A의 {@code IN_PROGRESS} 학습 세션
     * @param reviewItemId A의 due 복습 카드
     * @param rubberDuckSessionId A의 {@code IN_PROGRESS} 러버덕 세션 (턴 1개)
     * @param readCodeTaskId A의 {@code READ_CODE} 과제 (러버덕 {@code CODE_READING} 대상)
     * @param challengeId A 소유 challenge (docs/09 §9.2 "A 소유 AI 생성 challenge")
     * @param attemptId A의 challenge attempt ({@code STARTED})
     * @param submissionNo 평가 재시도 path 변수. attempt 소유권 검사가 submission 조회보다 먼저라 값 자체는 1로 둔다(docs/05
     *     §10.10)
     * @param skillId 공용 catalog skill id ({@code GET /skills/&#123;skillId&#125;/history}용, docs/09
     *     §9.2)
     * @param lessonSkillId fixture 개념 노트가 붙은 skill id ({@code GET
     *     /skills/&#123;skillId&#125;/lesson}용, docs/05 §21.3)
     */
    public record IsolationFixture(
            long invitedMeVersion,
            String planId,
            String milestoneId,
            String sideProjectId,
            String taskId,
            String sessionId,
            String reviewItemId,
            String rubberDuckSessionId,
            String readCodeTaskId,
            String challengeId,
            String attemptId,
            int submissionNo,
            String skillId,
            String lessonSkillId,
            Map<String, Object> replanBody,
            Map<String, Object> onboardingBody,
            Map<String, Object> sideProjectBody,
            Map<String, Object> learningGoalBody) {}

    private static final Function<IsolationFixture, Object[]> NO_VARIABLES =
            fixture -> new Object[0];
    private static final Function<IsolationFixture, @Nullable Object> NO_BODY = fixture -> null;

    public static List<EndpointCase> all() {
        return List.of(
                // OWNED_RESOURCE
                new EndpointCase(
                        "E01",
                        HttpMethod.GET,
                        "/api/v1/plans/{planId}",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.planId()},
                        NO_BODY,
                        "PLAN_NOT_FOUND"),
                new EndpointCase(
                        "E02",
                        HttpMethod.PATCH,
                        "/api/v1/plans/{planId}/milestones/{milestoneId}",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.planId(), fixture.milestoneId()},
                        fixture -> Map.of("status", "DONE", "version", 0),
                        "PLAN_NOT_FOUND"),
                new EndpointCase(
                        "E03",
                        HttpMethod.POST,
                        "/api/v1/plans/{planId}/replan",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.planId()},
                        IsolationFixture::replanBody,
                        "PLAN_NOT_FOUND"),
                new EndpointCase(
                        "E04",
                        HttpMethod.GET,
                        "/api/v1/side-projects/{sideProjectId}",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.sideProjectId()},
                        NO_BODY,
                        "RESOURCE_NOT_FOUND"),
                new EndpointCase(
                        "E05",
                        HttpMethod.PATCH,
                        "/api/v1/side-projects/{sideProjectId}",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.sideProjectId()},
                        fixture -> Map.of("name", "탈취", "version", 0),
                        "RESOURCE_NOT_FOUND"),
                new EndpointCase(
                        "E06",
                        HttpMethod.DELETE,
                        "/api/v1/side-projects/{sideProjectId}",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.sideProjectId()},
                        NO_BODY,
                        "RESOURCE_NOT_FOUND"),
                new EndpointCase(
                        "E07",
                        HttpMethod.POST,
                        "/api/v1/plans/{planId}/replan/preview",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.planId()},
                        IsolationFixture::replanBody,
                        "PLAN_NOT_FOUND"),
                new EndpointCase(
                        "E08",
                        HttpMethod.PATCH,
                        "/api/v1/today/tasks/{taskId}",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.taskId()},
                        fixture -> Map.of("status", "SKIPPED", "version", 0),
                        "RESOURCE_NOT_FOUND"),
                new EndpointCase(
                        "E09",
                        HttpMethod.POST,
                        "/api/v1/learning-sessions/{sessionId}/complete",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.sessionId()},
                        fixture -> Map.of("actualMinutes", 0),
                        "RESOURCE_NOT_FOUND"),
                new EndpointCase(
                        "E17",
                        HttpMethod.POST,
                        "/api/v1/learning-sessions/{sessionId}/abandon",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.sessionId()},
                        NO_BODY,
                        "RESOURCE_NOT_FOUND"),
                new EndpointCase(
                        "E18",
                        HttpMethod.POST,
                        "/api/v1/reviews/{reviewItemId}/answer",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.reviewItemId()},
                        fixture ->
                                Map.of(
                                        "selfRating",
                                        "GOOD",
                                        "hintLevel",
                                        "SELF_EXPLAIN",
                                        "responseSeconds",
                                        30,
                                        "wasVariant",
                                        false,
                                        "evaluate",
                                        false),
                        "RESOURCE_NOT_FOUND"),
                new EndpointCase(
                        "E40",
                        HttpMethod.GET,
                        "/api/v1/rubber-duck/{sessionId}",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.rubberDuckSessionId()},
                        NO_BODY,
                        "RESOURCE_NOT_FOUND"),
                new EndpointCase(
                        "E41",
                        HttpMethod.POST,
                        "/api/v1/rubber-duck/{sessionId}/turns",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.rubberDuckSessionId()},
                        fixture -> Map.of("explanation", "남의 세션에 끼어든 설명"),
                        "RESOURCE_NOT_FOUND"),
                new EndpointCase(
                        "E42",
                        HttpMethod.POST,
                        "/api/v1/rubber-duck/{sessionId}/complete",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.rubberDuckSessionId()},
                        NO_BODY,
                        "RESOURCE_NOT_FOUND"),
                new EndpointCase(
                        "E43",
                        HttpMethod.POST,
                        "/api/v1/rubber-duck/{sessionId}/abandon",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.rubberDuckSessionId()},
                        NO_BODY,
                        "RESOURCE_NOT_FOUND"),
                // Training (docs/05 §10.4~§10.11). 모두 attempt·challenge 소유권 검사가 먼저라 404
                // RESOURCE_NOT_FOUND다. 공용 seed challenge는 누구나 볼 수 있으므로 A 소유 challenge를 쓴다.
                new EndpointCase(
                        "E49",
                        HttpMethod.GET,
                        "/api/v1/challenges/{challengeId}",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.challengeId()},
                        NO_BODY,
                        "RESOURCE_NOT_FOUND"),
                new EndpointCase(
                        "E50",
                        HttpMethod.POST,
                        "/api/v1/challenges/{challengeId}/attempts",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.challengeId()},
                        NO_BODY,
                        "RESOURCE_NOT_FOUND"),
                new EndpointCase(
                        "E51",
                        HttpMethod.GET,
                        "/api/v1/challenge-attempts/{attemptId}",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.attemptId()},
                        NO_BODY,
                        "RESOURCE_NOT_FOUND"),
                // 자기 설명은 text·skipped 중 정확히 하나여야 400이 아니라 404까지 간다 (docs/05 §10.7)
                new EndpointCase(
                        "E52",
                        HttpMethod.POST,
                        "/api/v1/challenge-attempts/{attemptId}/self-explanation",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.attemptId()},
                        fixture -> Map.of("text", "남의 attempt에 끼어든 설명", "skipped", false),
                        "RESOURCE_NOT_FOUND"),
                // requestedLevel이 SELF_EXPLAIN이거나 skipSelfExplanation이 true면 400이 먼저다 (docs/05
                // §10.8 1번)
                new EndpointCase(
                        "E53",
                        HttpMethod.POST,
                        "/api/v1/challenge-attempts/{attemptId}/hints",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.attemptId()},
                        fixture ->
                                Map.of(
                                        "requestedLevel",
                                        "QUESTION_ONLY",
                                        "acknowledgeEvidenceImpact",
                                        false,
                                        "giveUp",
                                        false),
                        "RESOURCE_NOT_FOUND"),
                // answerText·code 중 하나는 있어야 400 ONE_OF_REQUIRED를 피한다 (docs/05 §10.9 1번)
                new EndpointCase(
                        "E54",
                        HttpMethod.POST,
                        "/api/v1/challenge-attempts/{attemptId}/submissions",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.attemptId()},
                        fixture -> Map.of("answerText", "남의 attempt에 낸 답안"),
                        "RESOURCE_NOT_FOUND"),
                new EndpointCase(
                        "E55",
                        HttpMethod.POST,
                        "/api/v1/challenge-attempts/{attemptId}/submissions/{submissionNo}/retry",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.attemptId(), fixture.submissionNo()},
                        NO_BODY,
                        "RESOURCE_NOT_FOUND"),
                new EndpointCase(
                        "E56",
                        HttpMethod.POST,
                        "/api/v1/challenge-attempts/{attemptId}/abandon",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.attemptId()},
                        NO_BODY,
                        "RESOURCE_NOT_FOUND"),
                // 복습 카드 관리 (docs/05 §11.6)
                new EndpointCase(
                        "E57",
                        HttpMethod.PATCH,
                        "/api/v1/review-items/{reviewItemId}",
                        Kind.OWNED_RESOURCE,
                        fixture -> new Object[] {fixture.reviewItemId()},
                        fixture -> Map.of("status", "SUSPENDED", "version", 0),
                        "RESOURCE_NOT_FOUND"),
                // BODY_REFERENCE (docs/09 §9.1 ISO-1b)
                bodyReference(
                        "E44",
                        fixture ->
                                rubberDuckBody(
                                        "CODE_READING",
                                        fixture.readCodeTaskId(),
                                        "SPRING.TRANSACTION")),
                bodyReference(
                        "E45",
                        fixture -> rubberDuckBody("REVIEW_ITEM", fixture.reviewItemId(), null)),
                bodyReference(
                        "E46",
                        fixture ->
                                rubberDuckBody(
                                        "PROJECT_WORK",
                                        fixture.sideProjectId(),
                                        "SPRING.TRANSACTION")),
                // SCOPED_COLLECTION
                scoped("E10", HttpMethod.GET, "/api/v1/me"),
                scoped("E11", HttpMethod.GET, "/api/v1/learning-goal"),
                scoped("E12", HttpMethod.GET, "/api/v1/skills/me"),
                scoped("E13", HttpMethod.GET, "/api/v1/plans/active"),
                scoped("E14", HttpMethod.GET, "/api/v1/plans"),
                scoped("E15", HttpMethod.GET, "/api/v1/side-projects"),
                scoped("E19", HttpMethod.GET, "/api/v1/plans/active/budget"),
                scoped("E21", HttpMethod.GET, "/api/v1/today"),
                scoped("E22", HttpMethod.GET, "/api/v1/learning-sessions"),
                scoped("E23", HttpMethod.GET, "/api/v1/reviews/due"),
                scoped("E24", HttpMethod.GET, "/api/v1/dashboard"),
                // 목록은 호출자 범위로만 조회한다 — A의 challenge·카드·제안은 B 응답에 없다 (docs/05 §10.2·§11.4·§4.2)
                scoped("E58", HttpMethod.GET, "/api/v1/challenges"),
                scoped("E59", HttpMethod.GET, "/api/v1/review-items"),
                scoped("E60", HttpMethod.GET, "/api/v1/diagnostics/suggestions"),
                // skill은 공용 catalog지만 이력은 본인 것만 나온다 (docs/05 §6.3, docs/09 §9.2 "공용 skill ID")
                new EndpointCase(
                        "E61",
                        HttpMethod.GET,
                        "/api/v1/skills/{skillId}/history",
                        Kind.SCOPED_COLLECTION,
                        fixture -> new Object[] {fixture.skillId()},
                        NO_BODY,
                        null),
                new EndpointCase(
                        "E16",
                        HttpMethod.PATCH,
                        "/api/v1/me",
                        Kind.SCOPED_COLLECTION,
                        NO_VARIABLES,
                        fixture -> Map.of("dayStartHour", 4, "version", fixture.invitedMeVersion()),
                        null),
                // SHARED_CONTENT
                new EndpointCase(
                        "E20",
                        HttpMethod.GET,
                        "/api/v1/skills/tree",
                        Kind.SHARED_CONTENT,
                        NO_VARIABLES,
                        NO_BODY,
                        null),
                new EndpointCase(
                        "E48",
                        HttpMethod.GET,
                        "/api/v1/readings/{readingKey}",
                        Kind.SHARED_CONTENT,
                        fixture -> new Object[] {SHARED_READING_KEY},
                        NO_BODY,
                        null),
                // 개념 노트 (docs/05 §21). 본문은 콘텐츠라 모든 사용자가 같은 것을 본다 — 사용자별인 것은 진행과 마침 기록뿐이다.
                new EndpointCase(
                        "E63",
                        HttpMethod.GET,
                        "/api/v1/lessons/{lessonKey}",
                        Kind.SHARED_CONTENT,
                        fixture -> new Object[] {SHARED_LESSON_KEY},
                        NO_BODY,
                        null),
                new EndpointCase(
                        "E64",
                        HttpMethod.POST,
                        "/api/v1/lessons/{lessonKey}/units/{unitKey}/predict",
                        Kind.SHARED_CONTENT,
                        fixture -> new Object[] {SHARED_LESSON_KEY, SHARED_UNIT_KEY},
                        fixture -> Map.of("answer", "400"),
                        null),
                new EndpointCase(
                        "E65",
                        HttpMethod.POST,
                        "/api/v1/lessons/{lessonKey}/units/{unitKey}/complete",
                        Kind.SHARED_CONTENT,
                        fixture -> new Object[] {SHARED_LESSON_KEY, SHARED_UNIT_KEY},
                        fixture -> Map.of("answers", List.of("GetMapping")),
                        null),
                new EndpointCase(
                        "E66",
                        HttpMethod.GET,
                        "/api/v1/lessons/{lessonKey}/units/{unitKey}/answer",
                        Kind.SHARED_CONTENT,
                        fixture -> new Object[] {SHARED_LESSON_KEY, SHARED_UNIT_KEY},
                        NO_BODY,
                        null),
                new EndpointCase(
                        "E67",
                        HttpMethod.POST,
                        "/api/v1/lessons/{lessonKey}/units/{unitKey}/finish",
                        Kind.SHARED_CONTENT,
                        fixture -> new Object[] {SHARED_LESSON_KEY, SHARED_UNIT_KEY},
                        fixture -> Map.of("helpLevel", "NONE"),
                        null),
                // skill → 노트 (docs/05 §21.3). skill id는 공용 catalog 값이고 본문도 모두에게 같다.
                new EndpointCase(
                        "E68",
                        HttpMethod.GET,
                        "/api/v1/skills/{skillId}/lesson",
                        Kind.SHARED_CONTENT,
                        fixture -> new Object[] {fixture.lessonSkillId()},
                        NO_BODY,
                        null),
                // ACCOUNT_ACTION
                account("E30", HttpMethod.DELETE, "/api/v1/me", NO_BODY),
                account(
                        "E31",
                        HttpMethod.POST,
                        "/api/v1/onboarding",
                        IsolationFixture::onboardingBody),
                account(
                        "E32",
                        HttpMethod.POST,
                        "/api/v1/side-projects",
                        IsolationFixture::sideProjectBody),
                account(
                        "E33",
                        HttpMethod.PUT,
                        "/api/v1/learning-goal",
                        IsolationFixture::learningGoalBody),
                account(
                        "E34",
                        HttpMethod.POST,
                        "/api/v1/today/generate",
                        fixture ->
                                Map.of(
                                        "availableMinutes",
                                        30,
                                        "energyLevel",
                                        "NORMAL",
                                        "force",
                                        false)),
                account("E35", HttpMethod.POST, "/api/v1/learning-sessions", fixture -> Map.of()),
                account(
                        "E47",
                        HttpMethod.POST,
                        "/api/v1/rubber-duck",
                        fixture -> rubberDuckBody("CONCEPT", null, null)),
                // 수동 카드 생성은 A의 id를 넣을 자리가 없다 (docs/05 §11.5, docs/09 §9.2 ACCOUNT_ACTION 목록)
                account(
                        "E62",
                        HttpMethod.POST,
                        "/api/v1/review-items",
                        fixture -> manualCardBody()));
    }

    /** 인증 없이 열리는 경로와 Bearer를 쓰지 않는 경로 (docs/07 §4.1, docs/09 §9.2 끝). */
    public static List<String> excludedMappings() {
        return List.of(
                "POST /api/v1/dev/token",
                "GET /api/v1/dev/jwks.json",
                "GET /api/v1/calendar/{token}.ics");
    }

    /** body에 A의 id를 넣는 case (ISO-1b). 400 + field error {@code REFERENCE_NOT_FOUND}다. */
    private static EndpointCase bodyReference(
            String id, Function<IsolationFixture, @Nullable Object> body) {
        return new EndpointCase(
                id,
                HttpMethod.POST,
                "/api/v1/rubber-duck",
                Kind.BODY_REFERENCE,
                NO_VARIABLES,
                body,
                null);
    }

    private static Map<String, Object> rubberDuckBody(
            String targetType, @Nullable String targetId, @Nullable String skillCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targetType", targetType);
        body.put("targetId", targetId);
        body.put("conceptKey", "CONCEPT".equals(targetType) ? "SPRING.TRANSACTION.BOUNDARY" : null);
        body.put("skillCode", skillCode);
        return body;
    }

    /** 수동 복습 카드 생성 요청 (docs/05 §11.5 {@code ReviewItemCreateRequest}). */
    private static Map<String, Object> manualCardBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("skillCode", "SPRING.TRANSACTION");
        body.put("conceptKey", "SPRING.TRANSACTION.ISOLATION");
        body.put("reviewType", "EXPLAIN");
        body.put("prompt", "트랜잭션 격리 수준을 설명해 보세요.");
        body.put("expectedAnswer", "READ COMMITTED가 기본이고 팬텀 읽기는 막지 못한다.");
        body.put("rubric", List.of("격리 수준 이름을 든다", "각 수준이 막는 이상 현상을 설명한다"));
        return body;
    }

    private static EndpointCase scoped(String id, HttpMethod method, String path) {
        return new EndpointCase(
                id, method, path, Kind.SCOPED_COLLECTION, NO_VARIABLES, NO_BODY, null);
    }

    private static EndpointCase account(
            String id,
            HttpMethod method,
            String path,
            Function<IsolationFixture, @Nullable Object> body) {
        return new EndpointCase(id, method, path, Kind.ACCOUNT_ACTION, NO_VARIABLES, body, null);
    }
}
