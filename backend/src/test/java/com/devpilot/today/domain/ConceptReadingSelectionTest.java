package com.devpilot.today.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.config.TrackDefaults;
import com.devpilot.common.web.AxisLevels;
import com.devpilot.testsupport.UnitTest;
import com.devpilot.testsupport.VectorLoader;
import com.devpilot.today.domain.TaskProposalPolicy.Proposal;
import com.devpilot.today.domain.TaskProposalPolicy.ProposalInput;
import com.devpilot.today.domain.TaskProposalPolicy.SkillContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * docs/06 §5.3 "개념 읽기 선택" vector ({@code 06-05-concept-reading.yaml}, C-1~C-8 8행). 완료·최근 14
 * plan-day 제안·은퇴로 후보를 거르고({@link ConceptReadingSelection}) key ASC 첫 번째를 {@code READING} 과제에
 * 붙인다({@link TaskProposalPolicy}). 후보가 비면 자료 없이 제안한다(회귀 없음). FR-07, AC-02.
 */
@UnitTest
class ConceptReadingSelectionTest {

    private static final String VECTOR_FILE = "06-05-concept-reading.yaml";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    /** 3번 분기(READING)로 내려오는 상태: KNOWLEDGE 0, challenge·코드 읽기 후보 없음. */
    private static final AxisLevels BEGINNER = new AxisLevels(0, 0, 0, 0);

    /** {@code devpilot.tracks.JAVA_BACKEND} (docs/06 §5.3 표). */
    private static final TrackDefaults BASE_TRACK = new TrackDefaults(5, 1, false);

    private final TaskProposalPolicy policy = new TaskProposalPolicy();

    @ParameterizedTest(name = "[{index}] {0} {1}")
    @MethodSource("vectors")
    void shouldMatchVectorWhenConceptReadingIsSelected(
            String id, String caseName, Map<String, Object> row) {
        String description = id + " " + caseName;
        String skillCode = (String) row.get("skill");
        List<ConceptReading> candidates =
                ConceptReadingSelection.candidates(
                        registry(row.get("conceptReadings")), skillCode, excludedKeys(row));

        Proposal proposal =
                policy.propose(
                        new ProposalInput(
                                new SkillContext(skillCode, "Skill", "설명", BEGINNER, false),
                                EnergyLevel.NORMAL,
                                false,
                                true,
                                BASE_TRACK,
                                List.of(),
                                List.of(),
                                candidates,
                                null));

        Map<String, Object> expected = map(row.get("expected"));
        assertThat(proposal.taskType()).as(description).isEqualTo(TaskType.READING);
        assertThat(proposal.readingKey()).as(description).isEqualTo(expected.get("readingKey"));
        assertThat(proposal.estimatedMinutes())
                .as(description)
                .isEqualTo(expected.get("estimated"));
        assertThat(proposal.difficulty()).as(description).isEqualTo(expected.get("difficulty"));
    }

    @Test
    void shouldUseContentTitleAndWhyReadWhenMaterialIsChosen() {
        SkillContext skill = new SkillContext("DEVOPS.GIT", "Git 협업", "브랜치 전략", BEGINNER, false);
        ConceptReading material = reading("DOC.GIT.BRANCHING.001", "DEVOPS.GIT", 25, false);

        Proposal proposal = TaskProposalPolicy.reading(skill, material);

        assertThat(proposal.title()).isEqualTo("Git 협업 개념 읽기 — " + material.title());
        assertThat(proposal.description())
                .isEqualTo(material.whyRead() + "\n읽고 나서 핵심 3가지를 스스로 적어 보세요.");
        assertThat(proposal.readingKey()).isEqualTo("DOC.GIT.BRANCHING.001");
        assertThat(proposal.estimatedMinutes()).isEqualTo(25);
        assertThat(proposal.difficulty()).isEqualTo(1);
        assertThat(proposal.challengeId()).isNull();
        assertThat(proposal.sideProjectId()).isNull();
    }

    @Test
    void shouldKeepMaterialOutOfWindowBoundary() {
        assertThat(ConceptReadingSelection.recentProposalFrom(TODAY))
                .isEqualTo(TODAY.minusDays(13));
        assertThat(ConceptReadingSelection.proposedRecently(TODAY.minusDays(13), TODAY)).isTrue();
        assertThat(ConceptReadingSelection.proposedRecently(TODAY.minusDays(14), TODAY)).isFalse();
    }

    static Stream<Arguments> vectors() {
        return VectorLoader.yamlRows(VECTOR_FILE).stream()
                .map(row -> Arguments.of(row.get("id"), row.get("case"), row));
    }

    /** 완료했거나 최근 14 plan-day 안에 제안된 key (docs/06 §5.3). */
    private static Set<String> excludedKeys(Map<String, Object> row) {
        Set<String> excluded = new HashSet<>();
        for (Object key : (List<?>) row.get("completed")) {
            excluded.add((String) key);
        }
        for (Object item : (List<?>) row.get("proposed")) {
            Map<String, Object> proposed = map(item);
            LocalDate proposedOn = TODAY.minusDays(((Integer) proposed.get("daysAgo")).longValue());
            if (ConceptReadingSelection.proposedRecently(proposedOn, TODAY)) {
                excluded.add((String) proposed.get("key"));
            }
        }
        return excluded;
    }

    private static List<ConceptReading> registry(Object value) {
        List<ConceptReading> readings = new ArrayList<>();
        for (Object item : (List<?>) value) {
            Map<String, Object> entry = map(item);
            readings.add(
                    reading(
                            (String) entry.get("key"),
                            (String) entry.get("skill"),
                            (Integer) entry.get("estimated"),
                            Boolean.TRUE.equals(entry.get("retired"))));
        }
        return readings;
    }

    private static ConceptReading reading(
            String key, String skillCode, int estimatedMinutes, boolean retired) {
        return new ConceptReading(
                key,
                "문서 " + key,
                "https://git-scm.com/book/en/v2",
                "Git",
                "버전 없음 (2026-09-21 기준 내용)",
                List.of(skillCode),
                estimatedMinutes,
                "이 자료를 읽으면 이 기술에서 무엇을 할 수 있게 되는지 한 문단으로 적어 둔 자리다.",
                List.of("첫 번째 질문입니다", "두 번째 질문입니다", "세 번째 질문입니다"),
                LocalDate.of(2026, 9, 21),
                retired);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
