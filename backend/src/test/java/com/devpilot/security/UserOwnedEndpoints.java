package com.devpilot.security;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;

/**
 * 격리 테스트 endpoint catalog (docs/09 §9.2). S1에 있는 endpoint만 넣는다 — 새 endpoint를 만들면 여기에 추가해야 {@code
 * EndpointCatalogCompletenessTest}가 통과한다.
 */
public final class UserOwnedEndpoints {

    private UserOwnedEndpoints() {}

    /** docs/09 §9.1 검증 종류. */
    public enum Kind {
        OWNED_RESOURCE,
        SCOPED_COLLECTION,
        SHARED_CONTENT,
        ACCOUNT_ACTION
    }

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
     */
    public record IsolationFixture(
            long invitedMeVersion,
            String planId,
            String milestoneId,
            String sideProjectId,
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
                // SCOPED_COLLECTION
                scoped("E10", HttpMethod.GET, "/api/v1/me"),
                scoped("E11", HttpMethod.GET, "/api/v1/learning-goal"),
                scoped("E12", HttpMethod.GET, "/api/v1/skills/me"),
                scoped("E13", HttpMethod.GET, "/api/v1/plans/active"),
                scoped("E14", HttpMethod.GET, "/api/v1/plans"),
                scoped("E15", HttpMethod.GET, "/api/v1/side-projects"),
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
                        IsolationFixture::learningGoalBody));
    }

    /** 인증 없이 열리는 경로와 Bearer를 쓰지 않는 경로 (docs/07 §4.1, docs/09 §9.2 끝). */
    public static List<String> excludedMappings() {
        return List.of(
                "POST /api/v1/dev/token",
                "GET /api/v1/dev/jwks.json",
                "GET /api/v1/calendar/{token}.ics");
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
