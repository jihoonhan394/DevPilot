package com.devpilot.plan.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 지금 만들 수 있는 것 (docs/05 §7.10, docs/06 §11.4 BS-6·BS-7·BS-10, ADR-060).
 *
 * <p>테스트 계획은 milestone 3개다 — 기반 다지기(MUST 4: JAVA.EXCEPTION, JAVA.COLLECTION, WEB_HTTP.HTTP_BASICS,
 * TESTING.JUNIT), 주문 흐름(MUST 2 + SHOULD DEVOPS.DOCKER), 설명과 정리(MUST 1 + SHOULD
 * ALGORITHM.SORT_SEARCH).
 */
@IntegrationTest
class BuildableStepServiceIntegrationTest extends ApiTestSupport {

    private static final String BUILDABLE = "/api/v1/plans/active/buildable";

    /**
     * BS-10: 기본 온보딩은 JAVA를 3으로 적어 계획 레벨이 올라가지만 근거는 아직 0이다. 그래도 1단계가 "지금 만들 차례"다 — 이 화면은 안다고 답한 것을
     * 세지 않는다.
     */
    @Test
    void shouldCountEvidenceOnlyAndPointAtTheFirstStep() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode buildable = api.body(api.get(user, BUILDABLE).andExpect(status().isOk()));

        assertThat(buildable.path("buildableStepCount").asInt()).isZero();
        assertThat(buildable.path("stepCount").asInt()).isEqualTo(3);
        JsonNode steps = buildable.path("steps");
        assertThat(titles(steps)).containsExactly("기반 다지기", "주문 흐름", "설명과 정리");
        assertThat(statuses(steps)).containsExactly("NEXT", "NOT_YET", "NOT_YET");
        assertThat(buildable.path("nextStepId").asString())
                .isEqualTo(steps.get(0).path("milestoneId").asString());
        assertThat(steps.get(0).path("metSkillCount").asInt()).isZero();
        assertThat(steps.get(0).path("description").asString())
                .isEqualTo("예외와 컬렉션, HTTP 기초를 다지고 첫 테스트를 쓴다.");
    }

    /**
     * ADR-061: 디버깅 목표가 있어도 단계가 열린다. coach 모듈이 없어 그 축은 근거를 쌓을 길 자체가 없고, 세면 어떤 단계도 영원히 안 열린다. 응답이 세는
     * 축과 뺀 축을 함께 말한다.
     */
    @Test
    void shouldNotLetAnAxisWithoutAWayToMeasureItBlockAStep() throws Exception {
        TestUser user = onboardedOwner();
        // 테스트 role target의 기반 다지기 MUST 넷은 모두 디버깅 목표가 2 이상이다.
        raiseEvidenceToTarget(user, "JAVA.EXCEPTION");
        raiseEvidenceToTarget(user, "JAVA.COLLECTION");
        raiseEvidenceToTarget(user, "WEB_HTTP.HTTP_BASICS");
        raiseEvidenceToTarget(user, "TESTING.JUNIT");
        assertThat(debuggingLevel(user, "JAVA.EXCEPTION")).isZero();

        JsonNode buildable = api.body(api.get(user, BUILDABLE));

        assertThat(buildable.path("steps").get(0).path("status").asString()).isEqualTo("BUILDABLE");
        assertThat(names(buildable.path("countedAxes")))
                .containsExactly("KNOWLEDGE", "IMPLEMENTATION", "EXPLANATION");
        assertThat(names(buildable.path("uncountedAxes"))).containsExactly("DEBUGGING");
    }

    /** BS-6: MUST가 있으면 그것만 관문이다 — SHOULD는 단계를 막지 않는다. */
    @Test
    void shouldGateOnMustTargetsOnly() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode steps = api.body(api.get(user, BUILDABLE)).path("steps");

        assertThat(steps.get(0).path("gateSkillCount").asInt()).isEqualTo(4);
        assertThat(steps.get(1).path("gateSkillCount").asInt()).isEqualTo(2);
        assertThat(steps.get(2).path("gateSkillCount").asInt()).isEqualTo(1);
    }

    /** 근거가 목표에 닿으면 그 단계는 만들 수 있고, 지금 만들 차례가 다음으로 넘어간다. */
    @Test
    void shouldMoveTheNextStepOnWhenEveryGateSkillIsMet() throws Exception {
        TestUser user = onboardedOwner();
        raiseEvidenceToTarget(user, "JAVA.EXCEPTION");
        raiseEvidenceToTarget(user, "JAVA.COLLECTION");
        raiseEvidenceToTarget(user, "WEB_HTTP.HTTP_BASICS");
        raiseEvidenceToTarget(user, "TESTING.JUNIT");

        JsonNode buildable = api.body(api.get(user, BUILDABLE));

        assertThat(buildable.path("buildableStepCount").asInt()).isEqualTo(1);
        JsonNode steps = buildable.path("steps");
        assertThat(statuses(steps)).containsExactly("BUILDABLE", "NEXT", "NOT_YET");
        assertThat(steps.get(0).path("gaps")).isEmpty();
        assertThat(buildable.path("nextStepId").asString())
                .isEqualTo(steps.get(1).path("milestoneId").asString());
    }

    /** 모자란 skill을 이름과 두 레벨로 보여 준다 — 무엇이 얼마나 남았는지가 화면의 답이다. */
    @Test
    void shouldNameTheSkillsThatAreStillShort() throws Exception {
        TestUser user = onboardedOwner();
        raiseEvidenceToTarget(user, "JAVA.EXCEPTION");
        raiseEvidenceToTarget(user, "JAVA.COLLECTION");

        JsonNode step = api.body(api.get(user, BUILDABLE)).path("steps").get(0);

        assertThat(step.path("metSkillCount").asInt()).isEqualTo(2);
        JsonNode gaps = step.path("gaps");
        assertThat(gaps.valueStream().map(gap -> gap.path("skill").path("code").asString()))
                .containsExactly("TESTING.JUNIT", "WEB_HTTP.HTTP_BASICS");
        JsonNode junit = gaps.get(0);
        assertThat(junit.path("evidenceLevels").path("implementation").asInt()).isZero();
        // ADR-070: targets는 **현재 판정 목표**다 — min(원래 목표, evidenceCeiling). 저장된 목표는 4이고
        // I 상한이 3이라 3이 온다. 원래 4가 남아 있다는 사실은 capabilityPending이 보인다(BL-GOL-24).
        assertThat(junit.path("targets").path("implementation").asInt()).isEqualTo(3);
    }

    /** ADR-070: 상한이 목표를 가리는 것이 아니라 낮출 뿐이다 — 상한 아래의 축은 저장된 목표가 그대로 온다. */
    @Test
    void shouldKeepATargetThatSitsBelowTheCeiling() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode gaps = api.body(api.get(user, BUILDABLE)).path("steps").get(0).path("gaps");
        JsonNode first = gaps.get(0);

        // K 상한은 4라 K 목표 3·4는 그대로다. D 상한은 0이라 판정에서 빠진다.
        assertThat(first.path("targets").path("knowledge").asInt()).isPositive();
        assertThat(first.path("targets").path("debugging").asInt()).isZero();
    }

    /**
     * AC-38 S3 · ADR-070: {@code BUILDABLE}이어도 <b>원래 목표가 남았다</b>는 사실이 함께 보인다.
     *
     * <p>{@code gaps}는 "지금 목표에 모자란 것"이고 최대 5개로 자른다. {@code capabilityPending}은 "기능이 열려야 갈 수 있는 곳"이고
     * 자르지 않는다 — 둘을 한 목록으로 묶으면 "지금 할 수 있는 것은 끝났지만 원래 목표는 남았다"를 말할 수 없다.
     */
    @Test
    void shouldShowWhatIsPendingOnCapabilityEvenWhenTheStepIsBuildable() throws Exception {
        TestUser user = onboardedOwner();
        raiseEvidenceToTarget(user, "JAVA.EXCEPTION");
        raiseEvidenceToTarget(user, "JAVA.COLLECTION");
        raiseEvidenceToTarget(user, "WEB_HTTP.HTTP_BASICS");
        raiseEvidenceToTarget(user, "TESTING.JUNIT");

        JsonNode buildable = api.body(api.get(user, BUILDABLE));
        JsonNode step = buildable.path("steps").get(0);

        assertThat(step.path("status").asString()).isEqualTo("BUILDABLE");
        assertThat(step.path("gaps")).isEmpty();

        // 전역 상한이 uncountedAxes와 어긋나지 않는다 — D 상한이 0이고 D가 빠진 축이다.
        JsonNode ceiling = buildable.path("evidenceCeiling");
        assertThat(ceiling.path("knowledge").asInt()).isEqualTo(4);
        assertThat(ceiling.path("implementation").asInt()).isEqualTo(3);
        assertThat(ceiling.path("explanation").asInt()).isEqualTo(3);
        assertThat(ceiling.path("debugging").asInt()).isZero();
        assertThat(names(buildable.path("uncountedAxes"))).containsExactly("DEBUGGING");

        // gaps가 비었는데도 남은 원래 목표가 보인다.
        JsonNode pending = step.path("capabilityPending");
        assertThat(pending).isNotEmpty();
        JsonNode first = pending.get(0);
        assertThat(first.path("skill").path("code").asString()).isNotBlank();
        // 원래 목표는 그대로이고, 현재 판정 목표는 상한으로 낮춘 값이다.
        assertThat(first.path("originalTargets").path("debugging").asInt()).isPositive();
        assertThat(first.path("currentTargets").path("debugging").asInt()).isZero();
        assertThat(first.path("currentTargets").path("implementation").asInt())
                .isLessThanOrEqualTo(3);
        // 보류 축에는 원래 목표 레벨이 담긴다. 상한 이하인 축은 0이다.
        assertThat(first.path("pending").path("debugging").asInt())
                .isEqualTo(first.path("originalTargets").path("debugging").asInt());
    }

    /** BS-7: 미룬 목표는 관문에서 빠진다 — 미루기로 한 것이 단계를 막으면 미룬 것이 아니다. */
    @Test
    void shouldLeaveDeferredTargetsOutOfTheGate() throws Exception {
        TestUser user = onboardedOwner();
        jdbc.update(
                "update devpilot.plan_skill_target t set deferred = true from"
                        + " devpilot.learning_plan p, devpilot.skill k where p.id = t.plan_id and"
                        + " k.id = t.skill_id and p.user_id = ? and p.status = 'ACTIVE' and k.code"
                        + " = 'EXPLANATION.PROJECT_STORY'",
                userId(user));

        JsonNode steps = api.body(api.get(user, BUILDABLE)).path("steps");

        // MUST가 빠지면 SHOULD가 관문이 된다 — 설명과 정리에는 ALGORITHM.SORT_SEARCH가 남는다.
        assertThat(steps.get(2).path("gateSkillCount").asInt()).isEqualTo(1);
        assertThat(steps.get(2).path("gaps").get(0).path("skill").path("code").asString())
                .isEqualTo("ALGORITHM.SORT_SEARCH");
    }

    /**
     * 온보딩 전에는 만들 순서 자체가 없다. 온보딩 가드가 먼저 걸리므로 404가 아니라 409다 (docs/05 §1.4) — 계획이 없는 것이 아니라 아직 시작하지 않은
     * 것이다.
     */
    @Test
    void shouldRequireOnboardingBeforeThereIsAnOrderToBuildIn() throws Exception {
        api.get(TestUser.owner(), BUILDABLE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"));
    }

    /**
     * 근거 레벨을 그 skill의 계획 목표까지 올린다 — <b>디버깅 축은 빼고</b>. coach 모듈이 없어 그 축은 실제로 0에서 움직이지 않는다(docs/06
     * §7.6). 실제와 같은 상태로 두어야 단계가 열리는지가 진짜 검사가 된다.
     */
    private void raiseEvidenceToTarget(TestUser user, String skillCode) {
        jdbc.update(
                "update devpilot.user_skill_state s set knowledge_level = t.target_knowledge_level,"
                    + " implementation_level = t.target_implementation_level, explanation_level ="
                    + " t.target_explanation_level from devpilot.plan_skill_target t,"
                    + " devpilot.learning_plan p, devpilot.skill k where p.id = t.plan_id and k.id"
                    + " = t.skill_id and t.skill_id = s.skill_id and p.user_id = s.user_id and"
                    + " p.status = 'ACTIVE' and s.user_id = ? and k.code = ?",
                userId(user),
                skillCode);
    }

    private int debuggingLevel(TestUser user, String skillCode) {
        Integer level =
                jdbc.queryForObject(
                        "select s.debugging_level from devpilot.user_skill_state s join"
                                + " devpilot.skill k on k.id = s.skill_id where s.user_id = ? and"
                                + " k.code = ?",
                        Integer.class,
                        userId(user),
                        skillCode);
        return level == null ? 0 : level;
    }

    private static List<String> names(JsonNode values) {
        return values.valueStream().map(JsonNode::asString).toList();
    }

    private static List<String> titles(JsonNode steps) {
        return steps.valueStream().map(step -> step.path("title").asString()).toList();
    }

    private static List<String> statuses(JsonNode steps) {
        return steps.valueStream().map(step -> step.path("status").asString()).toList();
    }
}
