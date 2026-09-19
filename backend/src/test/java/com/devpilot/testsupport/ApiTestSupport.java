package com.devpilot.testsupport;

import com.devpilot.integration.ai.api.AiBalance;
import com.devpilot.integration.ai.budget.AiBalanceMonitor;
import com.devpilot.integration.ai.fake.FakeAiProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * API 통합 테스트 공통 필드 (docs/09 §4.1). 하위 클래스는 {@link IntegrationTest}를 직접 붙인다(ArchUnit TEST-02). 매 테스트
 * 전에 시계를 기본값으로 되돌린다. 테스트는 테이블을 비우지 않으므로 항상 새 {@link TestUser}를 만들고 자기 사용자 범위만 센다.
 */
public abstract class ApiTestSupport {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected MutableClock clock;
    @Autowired protected TestJwksServer jwksServer;
    @Autowired protected JsonMapper jsonMapper;
    @Autowired protected JdbcTemplate jdbc;

    @Autowired protected ObjectProvider<FakeAiProvider> fakeAiProviders;
    @Autowired protected AiBalanceMonitor aiBalanceMonitor;

    protected TestApi api;

    @BeforeEach
    void setUpApi() {
        clock.setInstant(TestClockConfig.DEFAULT_INSTANT);
        api = new TestApi(mockMvc, jwksServer, clock, jsonMapper);
        FakeAiProvider fake = fakeAiProviders.getIfAvailable();
        if (fake != null) {
            fake.reset();
        }
        // 잔액 소진 상태는 context 전체가 공유한다 — 테스트마다 정상으로 되돌린다
        aiBalanceMonitor.apply(AiBalance.of(true, new BigDecimal("100.00")));
    }

    /** test profile의 fake provider ({@code devpilot.ai.provider = fake}). */
    protected FakeAiProvider fakeAi() {
        return fakeAiProviders.getObject();
    }

    /** 사용자 행 id ({@code app_user.id}). 아직 없으면 null. */
    protected UUID userId(TestUser user) {
        List<UUID> ids =
                jdbc.queryForList(
                        "select id from devpilot.app_user where external_auth_id = ?",
                        UUID.class,
                        user.sub());
        return ids.isEmpty() ? null : ids.getFirst();
    }

    protected int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    /** 온보딩까지 마친 사용자. */
    protected TestUser onboardedOwner() throws Exception {
        TestUser user = TestUser.owner();
        api.onboard(user);
        return user;
    }

    protected JsonNode activePlan(TestUser user) throws Exception {
        return api.body(api.get(user, "/api/v1/plans/active"));
    }

    /** 현재 plan의 milestone을 그대로 넘기는 replan 요청 (docs/05 §7.8). 목표 조정 목록은 비어 있다. */
    protected static Map<String, Object> replanRequest(JsonNode plan, String reason) {
        List<Object> milestones = new ArrayList<>();
        for (JsonNode milestone : plan.path("milestones")) {
            milestones.add(
                    milestone(
                            milestone.path("id").asString(),
                            milestone.path("title").asString(),
                            milestone.path("startDate").asString(),
                            milestone.path("endDate").asString(),
                            milestone.path("sortOrder").asInt(),
                            codes(milestone.path("skillCodes"))));
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reason", reason);
        request.put("version", plan.path("version").asLong());
        request.put("milestones", milestones);
        request.put("acceptedDeferrals", new ArrayList<>());
        request.put("acceptedTargetReductions", new ArrayList<>());
        request.put("restoredDeferrals", new ArrayList<>());
        request.put("acceptedTargetRaises", new ArrayList<>());
        return request;
    }

    /** replan milestone 입력. {@code id}가 null이면 새 milestone. */
    protected static Map<String, Object> milestone(
            String id,
            String title,
            String startDate,
            String endDate,
            int sortOrder,
            List<String> skillCodes) {
        Map<String, Object> milestone = new LinkedHashMap<>();
        milestone.put("id", id);
        milestone.put("title", title);
        milestone.put("description", null);
        milestone.put("startDate", startDate);
        milestone.put("endDate", endDate);
        milestone.put("priority", "MUST");
        milestone.put("status", "PLANNED");
        milestone.put("sortOrder", sortOrder);
        milestone.put("skillCodes", new ArrayList<>(skillCodes));
        return milestone;
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> milestones(Map<String, Object> replanRequest) {
        return (List<Map<String, Object>>) replanRequest.get("milestones");
    }

    private static List<String> codes(JsonNode array) {
        List<String> codes = new ArrayList<>();
        array.forEach(code -> codes.add(code.asString()));
        return codes;
    }
}
