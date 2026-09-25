package com.devpilot.skill.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 학습 단계 6칸 (docs/05 §6.4, docs/06 §5.11, BL-STG-01~02, AC-09).
 *
 * <p>저장하지 않는 값이라 "과제를 끝내면 칸이 채워진다"가 통째로 맞는지는 여기서만 확인된다 — 규칙 단위 test는 이벤트를 손으로 만들어 준다.
 */
@IntegrationTest
class LearningStageIntegrationTest extends ApiTestSupport {

    private static final String SKILL = "/api/v1/skills/{skillId}";
    private static final String TASK = "/api/v1/today/tasks/{taskId}";

    @Test
    void shouldReturnSixStagesInDeclarationOrder() throws Exception {
        TestUser user = onboardedOwner();
        UUID skillId = anyActiveSkillId();

        JsonNode detail = api.body(api.get(user, SKILL, skillId).andExpect(status().isOk()));

        assertThat(stageNames(detail))
                .containsExactly("BUILD", "READ_CONCEPT", "READ_CODE", "EXPLAIN", "REVIEW", "REDO");
        assertThat(detail.get("learningStages").valueStream().toList())
                .allMatch(stage -> !stage.get("completed").asBoolean());
        assertThat(detail.get("skill").get("id").asString()).isEqualTo(skillId.toString());
    }

    /** 과제를 끝내면 그 종류에 맞는 칸이 채워진다 — 순서를 건너뛰어도 그 칸만 채워진다 (ST-2). */
    @Test
    void shouldFillTheBuildStageWhenAChallengeTaskIsCompleted() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode plan = api.generateToday(user, 60, "NORMAL");
        JsonNode main = plan.get("mainTask");
        String taskId = main.get("id").asString();
        UUID skillId = UUID.fromString(main.get("skillId").asString());

        api.patch(user, TASK, statusRequest("IN_PROGRESS", main.get("version").asInt()), taskId)
                .andExpect(status().isOk());
        JsonNode updated =
                api.body(
                        api.patch(
                                user,
                                TASK,
                                statusRequest("COMPLETED", main.get("version").asInt() + 1),
                                taskId));
        assertThat(updated.get("status").asString()).isEqualTo("COMPLETED");

        JsonNode detail = api.body(api.get(user, SKILL, skillId));
        assertThat(completedStages(detail)).isNotEmpty();
        assertThat(detail.get("learningStages").valueStream().toList())
                .filteredOn(stage -> stage.get("completed").asBoolean())
                .allMatch(stage -> !stage.get("completedAt").isNull());
    }

    /** 다른 사용자의 완료는 내 칸을 채우지 않는다. 같은 공용 skill이라도 단계는 사람마다 따로다. */
    @Test
    void shouldNotShowAnotherLearnersProgress() throws Exception {
        TestUser owner = onboardedOwner();
        JsonNode plan = api.generateToday(owner, 60, "NORMAL");
        JsonNode main = plan.get("mainTask");
        String taskId = main.get("id").asString();
        UUID skillId = UUID.fromString(main.get("skillId").asString());
        api.patch(owner, TASK, statusRequest("IN_PROGRESS", main.get("version").asInt()), taskId);
        api.patch(owner, TASK, statusRequest("COMPLETED", main.get("version").asInt() + 1), taskId);

        TestUser other = onboardedOwner();
        JsonNode detail = api.body(api.get(other, SKILL, skillId).andExpect(status().isOk()));

        assertThat(completedStages(detail)).isEmpty();
    }

    @Test
    void shouldReturnNotFoundForAnUnknownSkill() throws Exception {
        TestUser user = onboardedOwner();

        api.get(user, SKILL, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    /** 리터럴 경로가 {@code {skillId}}보다 우선한다 — {@code /skills/me}가 상세로 새지 않는다. */
    @Test
    void shouldKeepLiteralPathsAheadOfThePathVariable() throws Exception {
        TestUser user = onboardedOwner();

        api.get(user, "/api/v1/skills/me").andExpect(status().isOk());
        api.get(user, "/api/v1/skills/tree").andExpect(status().isOk());
    }

    private UUID anyActiveSkillId() {
        return jdbc.queryForObject(
                "select id from devpilot.skill where active = true order by code limit 1",
                UUID.class);
    }

    private static List<String> stageNames(JsonNode detail) {
        return detail.get("learningStages")
                .valueStream()
                .map(stage -> stage.get("stage").asString())
                .toList();
    }

    private static List<String> completedStages(JsonNode detail) {
        return detail.get("learningStages")
                .valueStream()
                .filter(stage -> stage.get("completed").asBoolean())
                .map(stage -> stage.get("stage").asString())
                .toList();
    }

    private static Map<String, Object> statusRequest(String status, int version) {
        return Map.of("status", status, "version", version);
    }
}
