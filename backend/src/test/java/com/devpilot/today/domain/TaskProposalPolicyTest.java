package com.devpilot.today.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.testsupport.UnitTest;
import com.devpilot.testsupport.VectorLoader;
import com.devpilot.today.domain.TaskProposalPolicy.ChallengeOption;
import com.devpilot.today.domain.TaskProposalPolicy.Proposal;
import com.devpilot.today.domain.TaskProposalPolicy.ProposalInput;
import com.devpilot.today.domain.TaskProposalPolicy.ReadingOption;
import com.devpilot.today.domain.TaskProposalPolicy.SideProjectRef;
import com.devpilot.today.domain.TaskProposalPolicy.SkillContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * docs/06 §5.3 제안 분기 vector ({@code 06-05-task-proposal.yaml}, 13행 — 분기 5개, fallback, 복귀 모드, SP-1,
 * T-1~T-5), AC-02, AC-27 S7, AC-28 S1.
 */
@UnitTest
class TaskProposalPolicyTest {

    private static final String VECTOR_FILE = "06-05-task-proposal.yaml";
    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private final TaskProposalPolicy policy = new TaskProposalPolicy();

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("vectors")
    void shouldMatchVectorWhenTaskIsProposed(
            String id, ProposalInput input, Map<String, Object> expected) {
        Proposal proposal = policy.propose(input);

        assertThat(proposal.taskType())
                .as(id)
                .isEqualTo(TaskType.valueOf((String) expected.get("type")));
        assertThat(proposal.estimatedMinutes()).as(id).isEqualTo(expected.get("estimated"));
        assertThat(proposal.difficulty()).as(id).isEqualTo(expected.get("difficulty"));
        if (expected.containsKey("readingKey")) {
            assertThat(proposal.readingKey()).as(id).isEqualTo(expected.get("readingKey"));
        } else {
            assertThat(proposal.readingKey()).as(id).isNull();
        }
        if (expected.containsKey("challengeSeedKey")) {
            assertThat(proposal.challengeId())
                    .as(id)
                    .isEqualTo(challengeId((String) expected.get("challengeSeedKey")));
        }
        if (expected.containsKey("title")) {
            assertThat(proposal.title()).as(id).isEqualTo(expected.get("title"));
        }
        if (proposal.taskType() == TaskType.PROJECT_TASK) {
            assertThat(proposal.sideProjectId()).as(id).isEqualTo(PROJECT_ID);
        } else {
            assertThat(proposal.sideProjectId()).as(id).isNull();
        }
    }

    @Test
    void shouldFillTitleAndDescriptionTemplates() {
        SkillContext skill =
                new SkillContext(
                        "SPRING.TRANSACTION",
                        "Spring Transaction",
                        "트랜잭션 경계",
                        AxisLevels.ZERO,
                        false);

        assertThat(TaskProposalPolicy.explain(skill).title())
                .isEqualTo("Spring Transaction 내 말로 설명하기");
        assertThat(TaskProposalPolicy.explain(skill).description())
                .isEqualTo("트랜잭션 경계\n5문장 이내로 설명하고 예시를 하나 드세요.");
        assertThat(TaskProposalPolicy.reading(skill).title())
                .isEqualTo("Spring Transaction 핵심 개념 정리");
        assertThat(TaskProposalPolicy.recall(skill, 7).title())
                .isEqualTo("Spring Transaction 5분 떠올리기");
        assertThat(TaskProposalPolicy.recall(skill, 7).estimatedMinutes()).isEqualTo(7);
        assertThat(TaskProposalPolicy.reviewTitle(3)).isEqualTo("복습 3장");
        Proposal readCode =
                TaskProposalPolicy.readCode(
                        new ReadingOption(
                                "READ.PETCLINIC.CONTROLLER_SLICE.001",
                                "Spring PetClinic",
                                "src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java",
                                48,
                                122,
                                15,
                                "이 컨트롤러가 요청을 어떻게 나누는지 설명해 보세요."));
        assertThat(readCode.title())
                .isEqualTo("Spring PetClinic 읽기 — OwnerController.java 48~122줄");
        assertThat(readCode.description()).startsWith("이 컨트롤러가 요청을 어떻게 나누는지 설명해 보세요.\n");
        assertThat(readCode.repoName()).isEqualTo("Spring PetClinic");
    }

    @Test
    void shouldSummarizeChallengeScenarioToTwoHundredCharacters() {
        String scenario = "가".repeat(250);
        Proposal proposal =
                TaskProposalPolicy.challenge(
                        new ChallengeOption(challengeId("LONG"), "LONG", "긴 문제", scenario, 2, 20));

        assertThat(proposal.description()).hasSize(200);
        assertThat(proposal.title()).isEqualTo("긴 문제");
    }

    @Test
    void shouldUseGuideOnlyWhenSkillDescriptionIsBlank() {
        SkillContext skill = new SkillContext("S", "Skill", " ", AxisLevels.ZERO, false);

        assertThat(TaskProposalPolicy.explain(skill).description())
                .isEqualTo("5문장 이내로 설명하고 예시를 하나 드세요.");
    }

    static Stream<Arguments> vectors() {
        return VectorLoader.yamlRows(VECTOR_FILE).stream()
                .map(row -> Arguments.of(row.get("id"), input(row), map(row.get("expected"))));
    }

    private static ProposalInput input(Map<String, Object> row) {
        String[] planning = ((String) row.get("planning")).split("\\|");
        AxisLevels levels =
                new AxisLevels(Integer.parseInt(planning[0]), Integer.parseInt(planning[1]), 0, 0);
        String sideProject = (String) row.get("sideProject");
        return new ProposalInput(
                new SkillContext("S", "Skill S", "설명", levels, (Boolean) row.get("projectNeed")),
                EnergyLevel.valueOf((String) row.get("energy")),
                (Boolean) row.get("comebackMode"),
                (Boolean) row.get("aiAvailable"),
                challenges(row.get("challenges")),
                readings(row.get("readings")),
                List.of(),
                sideProject == null ? null : new SideProjectRef(PROJECT_ID, sideProject));
    }

    private static List<ChallengeOption> challenges(Object value) {
        List<ChallengeOption> options = new ArrayList<>();
        for (Object item : (List<?>) value) {
            Map<String, Object> challenge = map(item);
            String seedKey = (String) challenge.get("seedKey");
            options.add(
                    new ChallengeOption(
                            challengeId(seedKey == null ? "NO_SEED" : seedKey),
                            seedKey,
                            "문제 " + seedKey,
                            "시나리오",
                            (Integer) challenge.get("difficulty"),
                            (Integer) challenge.get("estimated")));
        }
        return options;
    }

    private static List<ReadingOption> readings(Object value) {
        List<ReadingOption> options = new ArrayList<>();
        for (Object item : (List<?>) value) {
            Map<String, Object> reading = map(item);
            options.add(
                    new ReadingOption(
                            (String) reading.get("key"),
                            "Repo",
                            "src/Main.java",
                            1,
                            40,
                            (Integer) reading.get("estimated"),
                            "질문"));
        }
        return options;
    }

    private static UUID challengeId(String seedKey) {
        return UUID.nameUUIDFromBytes(seedKey.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
