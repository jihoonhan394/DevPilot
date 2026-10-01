package com.devpilot.today.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import com.devpilot.today.domain.ConceptReading;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * docs/19 §3.13·§8.2: 등록은 key ASC로 하고, 은퇴한 개념 읽기도 조회된다. planner 후보({@code active()})에는 은퇴한 것이 없다.
 */
@UnitTest
class ConceptReadingRegistryTest {

    private final ConceptReadingRegistry registry = new ConceptReadingRegistry();

    @Test
    void shouldBeEmptyBeforeRegistration() {
        assertThat(registry.all()).isEmpty();
        assertThat(registry.active()).isEmpty();
        assertThat(registry.find("DOC.GIT.BRANCHING.001")).isEmpty();
    }

    @Test
    void shouldKeepRetiredConceptReadingResolvableButOutOfCandidates() {
        registry.register(
                List.of(
                        reading("DOC.GIT.REBASING.001", false),
                        reading("DOC.GIT.BRANCHING.001", true)));

        assertThat(registry.all())
                .extracting(ConceptReading::key)
                .containsExactly("DOC.GIT.BRANCHING.001", "DOC.GIT.REBASING.001");
        assertThat(registry.active())
                .extracting(ConceptReading::key)
                .containsExactly("DOC.GIT.REBASING.001");
        assertThat(registry.find("DOC.GIT.BRANCHING.001"))
                .get()
                .extracting(ConceptReading::retired)
                .isEqualTo(true);
    }

    @Test
    void shouldReplaceEverythingOnRegistration() {
        registry.register(List.of(reading("DOC.GIT.BRANCHING.001", false)));
        registry.register(List.of(reading("DOC.GIT.REBASING.001", false)));

        assertThat(registry.find("DOC.GIT.BRANCHING.001")).isEmpty();
        assertThat(registry.all()).hasSize(1);
    }

    private static ConceptReading reading(String key, boolean retired) {
        return new ConceptReading(
                key,
                "Pro Git — 3.2 Git Branching",
                "https://git-scm.com/book/en/v2",
                "Git",
                "버전 없음 (2026-09-21 기준 내용)",
                List.of("DEVOPS.GIT"),
                25,
                "브랜치를 복사본이 아니라 커밋을 가리키는 이름으로 이해하게 된다.",
                List.of("첫 번째 질문입니다", "두 번째 질문입니다", "세 번째 질문입니다"),
                LocalDate.of(2026, 9, 21),
                retired);
    }
}
