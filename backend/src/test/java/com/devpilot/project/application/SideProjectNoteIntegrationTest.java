package com.devpilot.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 프로젝트 기록 (docs/05 §19.8~§19.12, BL-PRJ-02, AC-33).
 *
 * <p>결정 기록은 <b>골랐다 · 무엇 중에 · 왜</b> 셋이 다 있어야 하고, 장애 기록은 <b>증상 · 찾은 법 · 고친 법 · 막을 법</b> 넷이다. 하나라도 비면
 * 나중에 읽을 때 답이 없다.
 */
@IntegrationTest
class SideProjectNoteIntegrationTest extends ApiTestSupport {

    private static final String NOTES = "/api/v1/side-projects/{sideProjectId}/notes";
    private static final String NOTE = "/api/v1/side-projects/{sideProjectId}/notes/{noteId}";

    @Test
    void shouldKeepWhyItWasChosenInADecisionNote() throws Exception {
        TestUser user = onboardedOwner();
        UUID projectId = anyProjectId(user);

        JsonNode note =
                api.body(
                        api.postWithKey(user, TestApi.newKey(), NOTES, decision(), projectId)
                                .andExpect(status().isCreated()));

        assertThat(note.get("noteType").asString()).isEqualTo("DECISION");
        assertThat(note.get("decisionRationale").asString()).contains("읽기가 훨씬 많다");
        // 유형에 맞지 않는 항목은 null이다
        assertThat(note.get("incidentSymptom").isNull()).isTrue();
        assertThat(note.get("version").asInt()).isZero();
    }

    /** 결정 기록에 이유가 빠지면 받지 않는다 — 이유 없는 결정 기록은 나중에 아무것도 말해 주지 않는다. */
    @Test
    void shouldRejectADecisionNoteWithoutItsRationale() throws Exception {
        TestUser user = onboardedOwner();
        UUID projectId = anyProjectId(user);
        Map<String, Object> body = decision();
        body.remove("decisionRationale");

        api.postWithKey(user, TestApi.newKey(), NOTES, body, projectId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'decisionRationale')].code")
                                .value("VALUE_REQUIRED"));
    }

    /** 유형이 섞이면 받지 않는다 (I-22) — 결정 기록에 장애 항목을 넣는 것은 유형을 헷갈린 것이다. */
    @Test
    void shouldRejectFieldsThatBelongToTheOtherType() throws Exception {
        TestUser user = onboardedOwner();
        UUID projectId = anyProjectId(user);
        Map<String, Object> body = decision();
        body.put("incidentSymptom", "요청이 멈췄다");

        api.postWithKey(user, TestApi.newKey(), NOTES, body, projectId)
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'incidentSymptom')].code")
                                .value("VALUE_NOT_ALLOWED"));
    }

    @Test
    void shouldRejectAFutureDate() throws Exception {
        TestUser user = onboardedOwner();
        UUID projectId = anyProjectId(user);
        Map<String, Object> body = decision();
        body.put("occurredOn", "2099-01-01");

        api.postWithKey(user, TestApi.newKey(), NOTES, body, projectId)
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'occurredOn')].code")
                                .value("DATE_OUT_OF_RANGE"));
    }

    /** 유형은 바꿀 수 없다 (PN-2) — body에 넣으면 알 수 없는 속성이다. */
    @Test
    void shouldRefuseToChangeTheNoteType() throws Exception {
        TestUser user = onboardedOwner();
        UUID projectId = anyProjectId(user);
        JsonNode note =
                api.body(api.postWithKey(user, TestApi.newKey(), NOTES, decision(), projectId));

        api.patch(
                        user,
                        NOTE,
                        Map.of("noteType", "INCIDENT", "version", 0),
                        projectId,
                        note.get("id").asString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    /** 바뀐 값이 없으면 version을 올리지 않는다 — 아무것도 안 고친 저장이 이력을 늘리지 않는다. */
    @Test
    void shouldLeaveTheVersionAloneWhenNothingActuallyChanged() throws Exception {
        TestUser user = onboardedOwner();
        UUID projectId = anyProjectId(user);
        JsonNode note =
                api.body(api.postWithKey(user, TestApi.newKey(), NOTES, decision(), projectId));
        String noteId = note.get("id").asString();

        JsonNode unchanged =
                api.body(
                        api.patch(
                                user,
                                NOTE,
                                Map.of("title", note.get("title").asString(), "version", 0),
                                projectId,
                                noteId));

        assertThat(unchanged.get("version").asInt()).isZero();
    }

    @Test
    void shouldListNewestFirstAndFilterByType() throws Exception {
        TestUser user = onboardedOwner();
        UUID projectId = anyProjectId(user);
        api.postWithKey(user, TestApi.newKey(), NOTES, decision(), projectId);
        Map<String, Object> incident = incident();
        incident.put("occurredOn", "2026-10-04");
        api.postWithKey(user, TestApi.newKey(), NOTES, incident, projectId);

        JsonNode all = api.body(api.get(user, NOTES, projectId).andExpect(status().isOk()));
        assertThat(all.get("items").valueStream().map(n -> n.get("noteType").asString()).toList())
                .containsExactly("INCIDENT", "DECISION");

        JsonNode onlyDecisions = api.body(api.get(user, NOTES + "?noteType=DECISION", projectId));
        assertThat(onlyDecisions.get("items")).hasSize(1);
    }

    /** 다른 프로젝트의 노트 id를 넣어도 404다 — 부모와 자식의 소유를 함께 본다 (docs/07 §4.3). */
    @Test
    void shouldNotFindANoteThroughAnotherProject() throws Exception {
        TestUser user = onboardedOwner();
        UUID projectId = anyProjectId(user);
        JsonNode note =
                api.body(api.postWithKey(user, TestApi.newKey(), NOTES, decision(), projectId));

        api.get(user, NOTE, UUID.randomUUID(), note.get("id").asString())
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteAndThenNotFindIt() throws Exception {
        TestUser user = onboardedOwner();
        UUID projectId = anyProjectId(user);
        String noteId =
                api.body(api.postWithKey(user, TestApi.newKey(), NOTES, decision(), projectId))
                        .get("id")
                        .asString();

        api.delete(user, NOTE, projectId, noteId).andExpect(status().isNoContent());
        api.get(user, NOTE, projectId, noteId).andExpect(status().isNotFound());
    }

    private UUID anyProjectId(TestUser user) {
        return jdbc.queryForObject(
                "select id from devpilot.side_project where user_id = ? order by created_at limit"
                        + " 1",
                UUID.class,
                userId(user));
    }

    private static Map<String, Object> decision() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("noteType", "DECISION");
        body.put("title", "조회 전용 복제본을 두지 않기로 했다");
        body.put("occurredOn", "2026-10-03");
        body.put("decisionChoice", "단일 DB로 간다");
        body.put("decisionOptions", "읽기 복제본 추가 / 캐시 계층 / 지금 그대로");
        body.put("decisionRationale", "읽기가 훨씬 많다고 짐작했지만 실제 비율을 재 보지 않았다");
        return body;
    }

    private static Map<String, Object> incident() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("noteType", "INCIDENT");
        body.put("title", "배포 뒤 응답이 멈췄다");
        body.put("occurredOn", "2026-10-02");
        body.put("incidentSymptom", "요청이 30초쯤 걸리다 타임아웃");
        body.put("incidentDetection", "응답 시간 그래프가 배포 시각부터 꺾였다");
        body.put("incidentFix", "커넥션 풀 최대치를 늘리고 타임아웃을 줄였다");
        body.put("incidentPrevention", "풀 사용률에 경보를 걸었다");
        return body;
    }
}
