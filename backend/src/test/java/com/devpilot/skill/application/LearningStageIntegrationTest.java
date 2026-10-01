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

    /**
     * 설명하기 단계를 과제로 채우는 유일한 길 (docs/06 §5.11). 러버덕 말고는 이것뿐이라, 여기가 끊기면 그 칸은 영영 비어 있다.
     *
     * <p>{@code explainedToPerson = false}로 끝내면 채워지지 않는다 — 혼잣말은 설명이 아니다.
     */
    @Test
    void shouldFillTheExplainStageOnlyWhenItWasSaidToAPerson() throws Exception {
        TestUser user = onboardedOwner();
        UUID skillId = anyActiveSkillId();

        assertThat(completedStages(api.body(api.get(user, SKILL, skillId))))
                .doesNotContain("EXPLAIN");

        UUID taskId = explainTask(user, skillId);
        api.patch(user, TASK, explainedRequest(false, 0), taskId).andExpect(status().isOk());
        assertThat(completedStages(api.body(api.get(user, SKILL, skillId))))
                .doesNotContain("EXPLAIN");

        UUID second = explainTask(user, skillId);
        api.patch(user, TASK, explainedRequest(true, 0), second).andExpect(status().isOk());

        assertThat(completedStages(api.body(api.get(user, SKILL, skillId)))).contains("EXPLAIN");
    }

    /** 고른 답은 Today 응답에 그대로 돌아온다 (docs/05 §8.1, I-24) — 화면이 어제 고른 값을 다시 보일 수 있어야 한다. */
    @Test
    void shouldReturnTheExplainedAnswerInTheTodayResponse() throws Exception {
        TestUser user = onboardedOwner();
        UUID taskId = explainTask(user, anyActiveSkillId());

        api.patch(user, TASK, explainedRequest(true, 0), taskId).andExpect(status().isOk());

        JsonNode earlier = api.body(api.get(user, "/api/v1/today")).get("earlierMainTasks");
        JsonNode explained =
                earlier.valueStream()
                        .filter(task -> taskId.toString().equals(task.get("id").asString()))
                        .findFirst()
                        .orElseThrow();
        assertThat(explained.get("explainedToPerson").asBoolean()).isTrue();
    }

    /** 설명 기록은 {@code EXPLAIN}·{@code READ_CODE}에만 있다 (I-24). */
    @Test
    void shouldRejectTheExplainedAnswerOnAChallengeTask() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode main = api.generateToday(user, 60, "NORMAL").get("mainTask");
        assertThat(main.get("taskType").asString()).isNotEqualTo("EXPLAIN");

        api.patch(
                        user,
                        TASK,
                        explainedRequest(true, main.get("version").asInt()),
                        main.get("id").asString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /**
     * {@code whyItMatters}는 <b>기술 트리</b>의 문장이다 (docs/19 §3.2, BL-CNT-21).
     *
     * <p>개념 노트에도 같은 이름의 필드가 있지만 그것은 그 노트를 왜 읽는지이고, 이건 그 기술을 왜 하는지다. 한때 노트 쪽을 읽고 있었다 — 두 문장이 다르므로 어느
     * 쪽을 보고 있는지 여기서 못 박는다.
     */
    @Test
    void shouldTakeWhyItMattersFromTheSkillTreeNotTheLesson() throws Exception {
        TestUser user = onboardedOwner();
        UUID skillId =
                jdbc.queryForObject(
                        "select id from devpilot.skill where code = 'JAVA.EXCEPTION'", UUID.class);

        JsonNode detail = api.body(api.get(user, SKILL, skillId).andExpect(status().isOk()));

        assertThat(detail.get("whyItMatters").asString())
                .isEqualTo("예외를 삼키면 실패한 요청이 성공처럼 지나가고, 로그에도 아무것도 남지 않는다.");
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

    /** 그 skill의 {@code EXPLAIN} 과제 하나를 오늘 계획에 직접 넣는다 — planner 분기를 기다리지 않는다. */
    private UUID explainTask(TestUser user, UUID skillId) throws Exception {
        UUID planId =
                UUID.fromString(
                        api.generateToday(user, 60, "NORMAL").get("dailyPlanId").asString());
        UUID taskId = UUID.randomUUID();
        jdbc.update(
                """
                insert into devpilot.learning_task
                    (id, user_id, daily_plan_id, skill_id, task_type, title, estimated_minutes,
                     status, is_main, sort_order, version)
                values (?, ?, ?, ?, 'EXPLAIN', ?, 20, 'IN_PROGRESS', false, 90, 0)
                """,
                taskId,
                userId(user),
                planId,
                skillId,
                "설명하기");
        return taskId;
    }

    private static Map<String, Object> explainedRequest(boolean toPerson, int version) {
        return Map.of("status", "COMPLETED", "explainedToPerson", toPerson, "version", version);
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
