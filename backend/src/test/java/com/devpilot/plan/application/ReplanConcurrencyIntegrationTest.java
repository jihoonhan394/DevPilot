package com.devpilot.plan.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;

/**
 * AC-24 S1 (실제 PostgreSQL 동시성): 같은 {@code version}의 replan 2개를 동시에 보내면 매번 201 1개와 409 1개이고, ACTIVE
 * plan은 항상 1행, 실패한 요청이 만든 행은 없다.
 */
@IntegrationTest
class ReplanConcurrencyIntegrationTest extends ApiTestSupport {

    private static final int ITERATIONS = 20;

    @Test
    void shouldAllowExactlyOneReplanWhenTwoRaceWithSameVersion() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                runIteration(executor, iteration);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void runIteration(ExecutorService executor, int iteration) throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        String path = "/api/v1/plans/" + v1.path("id").asString() + "/replan";
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Map<String, Object> request = replanRequest(v1, "동시 replan " + iteration + "-" + i);
            Callable<MockHttpServletResponse> call =
                    () -> {
                        start.await();
                        return api.post(user, path, request).andReturn().getResponse();
                    };
            futures.add(executor.submit(call));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        List<String> conflictCodes = new ArrayList<>();
        for (Future<MockHttpServletResponse> future : futures) {
            MockHttpServletResponse response = future.get(60, TimeUnit.SECONDS);
            statuses.add(response.getStatus());
            if (response.getStatus() == 409) {
                conflictCodes.add(
                        jsonMapper.readTree(response.getContentAsString()).path("code").asString());
            }
        }

        assertThat(statuses).as("iteration %d", iteration).containsExactlyInAnyOrder(201, 409);
        assertThat(conflictCodes)
                .as("iteration %d", iteration)
                .allSatisfy(
                        code ->
                                assertThat(code)
                                        .isIn("CONCURRENT_MODIFICATION", "PLAN_NOT_ACTIVE"));
        UUID userId = userId(user);
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_plan where user_id = ? and"
                                        + " status = 'ACTIVE'",
                                userId))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForList(
                                "select plan_version from devpilot.learning_plan where user_id = ?"
                                        + " order by plan_version",
                                Integer.class,
                                userId))
                .containsExactly(1, 2);
        assertThat(
                        count(
                                "select count(*) from devpilot.plan_milestone m join"
                                        + " devpilot.learning_plan p on p.id = m.plan_id where"
                                        + " p.user_id = ?",
                                userId))
                .isEqualTo(6);
        assertThat(
                        count(
                                "select count(*) from devpilot.plan_skill_target t join"
                                        + " devpilot.learning_plan p on p.id = t.plan_id where"
                                        + " p.user_id = ?",
                                userId))
                .isEqualTo(20);
    }
}
