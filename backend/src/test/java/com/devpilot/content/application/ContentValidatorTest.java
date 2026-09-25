package com.devpilot.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.devpilot.content.domain.RawContent;
import com.devpilot.content.infrastructure.YamlContentReader;
import com.devpilot.plan.application.PlanTemplateRegistry;
import com.devpilot.testsupport.TestProperties;
import com.devpilot.testsupport.UnitTest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * docs/19 §4 (BL-CNT-01). 운영 {@code content/}와 테스트 catalog는 오류 없이 통과하고, 규칙마다 테스트 catalog를 한 곳만 바꿔 그
 * 규칙 ID가 나오는지 본다. CV-04(파일시스템)는 CI 전용이라 제외하고, CV-61(배치 smoke)은 구조가 유효한 템플릿에서 실패할 수 없으므로 통과 경로만
 * 확인한다.
 */
@UnitTest
class ContentValidatorTest {

    private static final String TEST_CONTENT = "classpath:test-content/";
    private static final String PRODUCTION_CONTENT = "classpath:content/";
    private static final String RETIRED_READING = "READ.TESTREPO.LEGACY_CONTROLLER.001";
    private static final String RETIRED_CONCEPT_READING = "DOC.TESTJAVA.LEGACY.001";

    private final YamlContentReader reader = new YamlContentReader(new DefaultResourceLoader());
    private final ContentValidator validator =
            new ContentValidator(new PlanTemplateRegistry(), TestProperties.testProfile());

    @Test
    void shouldPassWithoutErrorsWhenProductionContentIsValidated() {
        ContentValidationReport report = validator.validate(reader.read(PRODUCTION_CONTENT));

        assertThat(report.errors()).isEmpty();
    }

    @Test
    void shouldPassWithoutIssuesWhenTestContentIsValidated() {
        ContentValidationReport report = validator.validate(reader.read(TEST_CONTENT));

        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings()).isEmpty();
    }

    @Test
    void shouldStopAfterCatalogWhenCatalogIsMissing() {
        ContentValidationReport report = validator.validate(reader.read("classpath:no-such-dir/"));

        assertThat(report.errors())
                .extracting(ContentValidationReport.Issue::rule)
                .contains("CV-01")
                .containsOnly("CV-01", "CV-02");
    }

    @ParameterizedTest(name = "[{index}] {0} {1}")
    @MethodSource("errorCases")
    void shouldReportRuleErrorWhenContentBreaksRule(
            String rule, String description, Consumer<Fixture> mutation) {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        mutation.accept(fixture);

        ContentValidationReport report = validator.validate(fixture.content);

        assertThat(report.errors())
                .as(description)
                .extracting(ContentValidationReport.Issue::rule)
                .contains(rule);
    }

    @ParameterizedTest(name = "[{index}] {0} {1}")
    @MethodSource("warningCases")
    void shouldReportRuleWarningWithoutErrorWhenContentDeviates(
            String rule, String description, Consumer<Fixture> mutation) {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        mutation.accept(fixture);

        ContentValidationReport report = validator.validate(fixture.content);

        assertThat(report.errors()).as(description).isEmpty();
        assertThat(report.warnings())
                .as(description)
                .extracting(ContentValidationReport.Issue::rule)
                .contains(rule);
    }

    static Stream<Arguments> errorCases() {
        return Stream.of(
                // catalog
                error("CV-01", "catalogVersion 0", f -> f.catalog().put("catalogVersion", 0)),
                error(
                        "CV-01",
                        "unknown diagnostic category",
                        f -> f.catalog().put("diagnosticCategories", list("COOKING"))),
                error(
                        "CV-02",
                        "listed file missing",
                        f -> f.fileList("skillTrees").add("skill-tree/missing.yaml")),
                error(
                        "CV-02",
                        "duplicate listed file",
                        f -> f.fileList("reviewCards").add("review-cards/test.yaml")),
                error(
                        "CV-03",
                        "unknown field on skill",
                        f -> f.skill("JAVA.COLLECTION").put("owner", "someone")),
                // skill tree
                error(
                        "CV-10",
                        "lowercase code",
                        f -> f.skill("JAVA.COLLECTION").put("code", "JAVA.collection")),
                error(
                        "CV-11",
                        "retired code still used",
                        f -> f.retired("skillCodes").add("JAVA.COLLECTION")),
                error("CV-12", "category without root", f -> f.skills().remove(f.skill("NETWORK"))),
                error(
                        "CV-13",
                        "parent in other category",
                        f -> f.skill("JAVA.COLLECTION").put("parent", "SPRING")),
                error(
                        "CV-14",
                        "description too short",
                        f -> f.skill("JAVA.COLLECTION").put("description", "짧다")),
                error(
                        "CV-15",
                        "non-root step below 60",
                        f -> f.skill("JAVA.COLLECTION").put("minutesPerLevelStep", 59)),
                error(
                        "CV-88",
                        "whyItMatters too short",
                        f -> f.skill("JAVA.COLLECTION").put("whyItMatters", "짧은 문장이다.")),
                error(
                        "CV-88",
                        "whyItMatters on root skill",
                        f ->
                                f.skill("JAVA")
                                        .put(
                                                "whyItMatters",
                                                "묶음 노드에는 이 값을 두지 않는다. 두면 과제 카드가 무엇을 보여야 할지 알 수"
                                                        + " 없다.")),
                error(
                        "CV-16",
                        "self prerequisite",
                        f -> f.prerequisites("SPRING.TRANSACTION").add("SPRING.TRANSACTION")),
                error(
                        "CV-17",
                        "prerequisite cycle",
                        f -> f.prerequisites("JAVA.EXCEPTION").add("SPRING.TRANSACTION")),
                error(
                        "CV-18",
                        "prerequisite with implementation target below 2",
                        f -> f.axisTargets("JAVA.EXCEPTION").put("implementation", 1)),
                error(
                        "CV-19",
                        "root as prerequisite",
                        f -> f.prerequisites("SPRING.TRANSACTION").add("JAVA")),
                // role targets
                error(
                        "CV-20",
                        "target on root skill",
                        f -> f.target("JAVA.COLLECTION").put("skill", "JAVA")),
                error(
                        "CV-21",
                        "missing target",
                        f -> f.targets().remove(f.target("TESTING.JUNIT"))),
                error(
                        "CV-22",
                        "importance with three decimals",
                        f -> f.target("JAVA.COLLECTION").put("importance", "0.855")),
                error(
                        "CV-23",
                        "all axis targets zero",
                        f -> f.target("JAVA.COLLECTION").put("target", axes(0, 0, 0, 0))),
                // plan template
                error("CV-30", "blank plan title", f -> f.template().put("planTitle", "")),
                error(
                        "CV-31",
                        "duplicate milestone key",
                        f -> f.milestone("ORDER_FLOW").put("key", "FOUNDATION")),
                error(
                        "CV-32",
                        "weights do not sum to 10000",
                        f -> f.milestone("FOUNDATION").put("weightBp", 3000)),
                error(
                        "CV-33",
                        "application before preparation",
                        f -> f.milestone("FOUNDATION").put("phase", "CONSOLIDATION")),
                error(
                        "CV-34",
                        "unknown milestone priority",
                        f -> f.milestone("FOUNDATION").put("priority", "URGENT")),
                error(
                        "CV-35",
                        "skill in two milestones",
                        f -> f.milestoneSkills("ORDER_FLOW").add("JAVA.EXCEPTION")),
                error(
                        "CV-36",
                        "MUST skill outside every milestone",
                        f -> f.milestoneSkills("ORDER_FLOW").remove("DATABASE.INDEX")),
                error(
                        "CV-37",
                        "minMilestoneDays above 28",
                        f -> f.placement().put("minMilestoneDays", 29)),
                // review cards
                error(
                        "CV-40",
                        "reserved conceptKey prefix",
                        f ->
                                f.card("JAVA.EXCEPTION.CAUSE_CHAIN")
                                        .put("conceptKey", "COACH:JAVA.EXCEPTION.X")),
                error(
                        "CV-41",
                        "conceptKey not under skill",
                        f -> f.card("JAVA.EXCEPTION.CAUSE_CHAIN").put("skill", "JAVA.COLLECTION")),
                error(
                        "CV-42",
                        "unknown review type",
                        f -> f.card("JAVA.EXCEPTION.CAUSE_CHAIN").put("reviewType", "ESSAY")),
                error(
                        "CV-43",
                        "prompt too short",
                        f -> f.card("JAVA.EXCEPTION.CAUSE_CHAIN").put("prompt", "짧은 질문")),
                error(
                        "CV-44",
                        "rubric ids out of order",
                        f -> f.cardRubric("JAVA.EXCEPTION.CAUSE_CHAIN").get(1).put("id", "R3")),
                error(
                        "CV-45",
                        "BUG_SPOT without code block",
                        f ->
                                f.card("SPRING.TRANSACTION.SELF_INVOCATION")
                                        .put("prompt", "같은 클래스 안에서 트랜잭션 메서드를 부르면 무엇이 문제인가요?")),
                error(
                        "CV-46",
                        "CHOICE answer label missing",
                        f ->
                                f.card("DATABASE.INDEX.COMPOSITE_ORDER")
                                        .put("expectedAnswer", "Z) 없는 선택지를 고른다")),
                error(
                        "CV-47",
                        "R1 equals expected answer",
                        f ->
                                f.cardRubric("JAVA.EXCEPTION.CAUSE_CHAIN")
                                        .getFirst()
                                        .put(
                                                "criterion",
                                                f.card("JAVA.EXCEPTION.CAUSE_CHAIN")
                                                        .get("expectedAnswer"))),
                // challenges
                error(
                        "CV-50",
                        "seedKey level differs from difficulty",
                        f ->
                                f.challenge("PRACTICE.SPRING.TRANSACTION.L2.001")
                                        .put("difficulty", 3)),
                error(
                        "CV-51",
                        "estimatedMinutes above 180",
                        f ->
                                f.challenge("PRACTICE.SPRING.TRANSACTION.L2.001")
                                        .put("estimatedMinutes", 200)),
                error(
                        "CV-52",
                        "no skills",
                        f ->
                                f.challenge("PRACTICE.SPRING.TRANSACTION.L2.001")
                                        .put("skills", list())),
                error(
                        "CV-53",
                        "one expected concept",
                        f ->
                                f.challenge("PRACTICE.SPRING.TRANSACTION.L2.001")
                                        .put("expectedConcepts", list("트랜잭션 경계"))),
                error(
                        "CV-54",
                        "rubric weights do not sum to 10000",
                        f ->
                                f.challengeRubric("PRACTICE.SPRING.TRANSACTION.L2.001")
                                        .get(1)
                                        .put("weightBp", 4000)),
                error(
                        "CV-55",
                        "DIRECTION hint too short",
                        f -> f.hints("PRACTICE.SPRING.TRANSACTION.L2.001").put("DIRECTION", "짧다")),
                error(
                        "CV-56",
                        "backtick in hint",
                        f ->
                                f.hints("PRACTICE.SPRING.TRANSACTION.L2.001")
                                        .put(
                                                "CONCEPT_HINT",
                                                "`@Transactional`이 어디에 붙어 있는지 떠올려 보세요.")),
                error(
                        "CV-57",
                        "QUESTION_ONLY without question mark",
                        f ->
                                f.hints("PRACTICE.SPRING.TRANSACTION.L2.001")
                                        .put("QUESTION_ONLY", "재고 감소가 실패하는 순간의 범위를 떠올려 보세요.")),
                error(
                        "CV-58",
                        "transfer challenge below L3",
                        f ->
                                f.challenge("PRACTICE.SPRING.TRANSACTION.L2.001")
                                        .put("isTransfer", true)),
                error(
                        "CV-59",
                        "diagnostic longer than 15 minutes",
                        f -> f.challenge("DIAGNOSTIC.JAVA.L3.001").put("estimatedMinutes", 20)),
                // curated sources
                error(
                        "CV-70",
                        "duplicate source id",
                        f -> f.sources().add(new LinkedHashMap<>(f.sources().getFirst()))),
                error(
                        "CV-71",
                        "untrusted host",
                        f -> f.sources().getFirst().put("url", "https://blog.example.invalid/tx")),
                error(
                        "CV-72",
                        "invalid verifiedAt",
                        f -> f.sources().getFirst().put("verifiedAt", "2026-13-01")),
                // curated repos
                error("CV-80", "empty readings", f -> f.curatedRepos().put("readings", list())),
                error(
                        "CV-81",
                        "subPath escapes repository",
                        f -> f.repos().getFirst().put("subPath", "../outside")),
                error(
                        "CV-82",
                        "pinnedCommit not a SHA",
                        f -> f.repos().getFirst().put("pinnedCommit", "ABC123")),
                error(
                        "CV-83",
                        "duplicate reading key",
                        f -> f.readings().get(1).put("key", f.readings().getFirst().get("key"))),
                error(
                        "CV-84",
                        "unknown repo",
                        f -> f.readings().getFirst().put("repo", "unknown-repo")),
                error(
                        "CV-85",
                        "descending lines",
                        f -> f.readings().getFirst().put("lines", list(60, 10))),
                error(
                        "CV-86",
                        "question too short",
                        f -> f.readings().getFirst().put("question", "짧은 질문이다")),
                error(
                        "CV-03",
                        "retired is not a boolean",
                        f -> f.readings().getFirst().put("retired", "yes")),
                // 개념 읽기 (docs/19 §3.13, §4.3 표)
                error(
                        "CV-120",
                        "concept reading key pattern invalid",
                        f -> f.conceptReadings().getFirst().put("key", "DOC.git.branching.001")),
                error(
                        "CV-120",
                        "concept reading key collides with a code reading",
                        f ->
                                f.conceptReadings()
                                        .getFirst()
                                        .put("key", "READ.TESTREPO.ORDER_SERVICE.001")),
                error(
                        "CV-120",
                        "retired concept reading is not listed",
                        f -> f.retired("readingKeys").remove(RETIRED_CONCEPT_READING)),
                error(
                        "CV-121",
                        "concept reading url host is not trusted",
                        f ->
                                f.conceptReadings()
                                        .getFirst()
                                        .put("url", "https://blog.example.com/git")),
                error(
                        "CV-122",
                        "verifiedAt is not an ISO date",
                        f -> f.conceptReadings().getFirst().put("verifiedAt", "2026/09/21")),
                error(
                        "CV-123",
                        "estimatedMinutes above 60",
                        f -> f.conceptReadings().getFirst().put("estimatedMinutes", 90)),
                error(
                        "CV-123",
                        "skillCode without a role target",
                        f -> f.conceptReadings().getFirst().put("skillCodes", list("JAVA"))),
                error(
                        "CV-124",
                        "only two checkPoints",
                        f ->
                                f.conceptReadings()
                                        .getFirst()
                                        .put("checkPoints", list("첫 번째 질문입니다", "두 번째 질문입니다"))),
                error(
                        "CV-124",
                        "whyRead too short",
                        f -> f.conceptReadings().getFirst().put("whyRead", "너무 짧은 설명이다.")),
                error(
                        "CV-03",
                        "concept reading retired is not a boolean",
                        f -> f.conceptReadings().getFirst().put("retired", "yes")),
                // checklists (docs/19 §3.11)
                error(
                        "CV-110",
                        "checklist key off pattern",
                        f -> f.checklists().getFirst().put("key", "CHK.TEST")),
                error(
                        "CV-110",
                        "duplicate checklist key",
                        f -> f.checklists().getLast().put("key", "CHK.TEST.EXCEPTION")),
                error(
                        "CV-111",
                        "unknown taskType",
                        f -> f.checklists().getFirst().put("taskTypes", list("HOMEWORK"))),
                error(
                        "CV-111",
                        "duplicate taskType",
                        f ->
                                f.checklists()
                                        .getFirst()
                                        .put("taskTypes", list("CHALLENGE", "CHALLENGE"))),
                error(
                        "CV-111",
                        "skillCode without a role target",
                        f -> f.checklists().getFirst().put("skillCodes", list("JAVA"))),
                error(
                        "CV-111",
                        "more than four skillCodes",
                        f ->
                                f.checklists()
                                        .getFirst()
                                        .put(
                                                "skillCodes",
                                                list(
                                                        "JAVA.EXCEPTION",
                                                        "JAVA.COLLECTION",
                                                        "SPRING.TRANSACTION",
                                                        "DATABASE.INDEX",
                                                        "TESTING.JUNIT"))),
                error(
                        "CV-112",
                        "only two before items",
                        f ->
                                f.checklists()
                                        .getFirst()
                                        .put("before", list("첫 번째 확인입니다", "두 번째 확인입니다"))),
                error(
                        "CV-112",
                        "after item too short",
                        f ->
                                f.checklists()
                                        .getFirst()
                                        .put("after", list("짧다", "두 번째 확인입니다", "세 번째 확인입니다"))));
    }

    /** CV-83 은퇴 규칙은 경우마다 정확히 1건, 위치는 해당 reading 또는 catalog 목록 (docs/19 §8.2). */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("retirementCases")
    void shouldReportExactlyOneRetirementErrorAtItsLocation(
            String description, String location, Consumer<Fixture> mutation) {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        mutation.accept(fixture);

        ContentValidationReport report = validator.validate(fixture.content);

        assertThat(report.errors())
                .as(description)
                .extracting(
                        ContentValidationReport.Issue::rule, ContentValidationReport.Issue::where)
                .containsExactly(tuple("CV-83", location));
    }

    static Stream<Arguments> retirementCases() {
        return Stream.of(
                Arguments.of(
                        "retired reading whose key is not listed",
                        "curated-repos.yaml#" + RETIRED_READING,
                        (Consumer<Fixture>) f -> f.retired("readingKeys").remove(RETIRED_READING)),
                Arguments.of(
                        "active reading whose key is listed",
                        "curated-repos.yaml#READ.TESTREPO.ORDER_SERVICE.001",
                        (Consumer<Fixture>)
                                f ->
                                        f.retired("readingKeys")
                                                .add("READ.TESTREPO.ORDER_SERVICE.001")),
                Arguments.of(
                        "listed key without definition",
                        "catalog.yaml#retired.readingKeys",
                        (Consumer<Fixture>)
                                f -> f.retired("readingKeys").add("READ.TESTREPO.GONE.001")),
                Arguments.of(
                        "retired reading removed from the file",
                        "catalog.yaml#retired.readingKeys",
                        (Consumer<Fixture>) f -> f.readings().removeLast()));
    }

    @Test
    void shouldNotWarnWhenRepoKeepsOnlyRetiredReadings() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        for (Map<String, Object> reading : fixture.readings()) {
            if (!Boolean.TRUE.equals(reading.get("retired"))) {
                reading.put("retired", true);
                fixture.retired("readingKeys").add(reading.get("key"));
            }
        }

        ContentValidationReport report = validator.validate(fixture.content);

        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings()).isEmpty();
    }

    /** 운영 콘텐츠에 실제로 쓰이는 값 모양 (repo key 하이픈, 점으로 시작하는 경로, 긴 license·cloneHint, 조각이 있는 URL). */
    @Test
    void shouldAcceptRealWorldRepoAndSourceValues() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        Map<String, Object> repo = new LinkedHashMap<>(fixture.repos().getFirst());
        repo.put("key", "modular-monolith");
        repo.put("name", "Modular Monolith Sample");
        repo.put("license", "GPL-2.0-only WITH Classpath-exception-2.0");
        repo.put(
                "cloneHint",
                "저장소를 아직 받지 않았다면 먼저 받고, 줄 번호가 맞도록 고정 커밋으로 checkout한다. "
                        + "git clone https://repo.example.invalid/modular-monolith.git && "
                        + "cd modular-monolith && git checkout "
                        + "0123456789abcdef0123456789abcdef01234567 "
                        + "— 이미 받았다면 git fetch 후 같은 커밋으로 checkout한다. ".repeat(4));
        repo.put("licenseNote", "읽기만 한다. 코드를 복사해 쓰지 않는다.");
        fixture.repos().add(repo);
        List<String> paths =
                List.of(".github/workflows/maven-build.yml", "compose.yml", "src/App.java");
        for (int index = 0; index < paths.size(); index++) {
            Map<String, Object> reading = new LinkedHashMap<>(fixture.readings().getFirst());
            reading.put("key", "READ.MODULAR_MONOLITH.TOPIC_" + index + ".001");
            reading.put("repo", "modular-monolith");
            reading.put("path", paths.get(index));
            fixture.readings().add(reading);
        }
        Map<String, Object> source = new LinkedHashMap<>(fixture.sources().getFirst());
        source.put("id", "CS-RFC-9110-STATUS");
        source.put("title", "RFC 9110: HTTP Semantics");
        source.put("url", "https://www.rfc-editor.org/rfc/rfc9110.html#name-status-codes");
        fixture.sources().add(source);

        ContentValidationReport report = validator.validate(fixture.content);

        assertThat(String.valueOf(repo.get("license"))).hasSize(41);
        assertThat(String.valueOf(repo.get("cloneHint")).length()).isBetween(300, 500);
        assertThat(report.errors()).isEmpty();
    }

    /**
     * CV-131은 정확히 일치하는 호스트뿐 아니라 **하위 도메인**도 받아들인다 (docs/19 §7.6, docs/03 §9).
     *
     * <p>한때 이 검사만 정확 일치였다. 다른 검사(CV-121·CV-71)와 python 검증기는 하위 도메인을 받아들였으므로 두 검증기가 같은 콘텐츠를 두고 다른 답을
     * 냈다.
     */
    @Test
    void shouldAcceptSourceOnSubdomainOfTrustedHost() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        fixture.lessonSources().getFirst().put("url", "https://docs.junit.org/current/user-guide/");

        assertThat(validator.validate(fixture.content).errors()).isEmpty();
    }

    @Test
    void shouldRejectSourceOnUntrustedHost() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        fixture.lessonSources().getFirst().put("url", "https://junit.org.example.invalid/guide");

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-131".equals(issue.rule()));
    }

    /**
     * CV-136은 <b>그 트랙에서</b> MUST인 것만 본다 (docs/19 CV-136).
     *
     * <p>한때 "어느 트랙에서든 MUST면" 경고했다. 같은 skill이 `JAVA_BACKEND`에서는 MUST여도 학습 트랙에서는 SHOULD일 수 있어서, 쓸 필요가
     * 없는 노트를 계속 쓰라고 알려 주고 있었다. python 검증기와 답이 달랐다.
     */
    @Test
    void shouldNotWarnWhenTheSkillIsOnlyMustForAnotherRole() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        // 두 학습 트랙에서는 SHOULD로 낮추고, JAVA_BACKEND 에서는 MUST 그대로 둔다
        fixture.trackTarget("test-integration.yaml", "TESTING.JUNIT").put("priority", "SHOULD");
        fixture.trackTarget("test-starter.yaml", "TESTING.JUNIT").put("priority", "SHOULD");
        fixture.lessons().removeIf(lesson -> "TESTING.JUNIT".equals(lesson.get("skillCode")));

        ContentValidationReport report = validator.validate(fixture.content);

        assertThat(report.warnings())
                .filteredOn(issue -> "CV-136".equals(issue.rule()))
                .extracting(ContentValidationReport.Issue::message)
                .noneMatch(message -> message.contains("TESTING.JUNIT"));
    }

    /** 반대로 그 트랙에서 MUST인데 노트가 없으면 여전히 알려 준다. */
    @Test
    void shouldWarnWhenAFirstMilestoneMustSkillOfTheTrackHasNoLesson() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        fixture.lessons().removeIf(lesson -> "TESTING.JUNIT".equals(lesson.get("skillCode")));

        ContentValidationReport report = validator.validate(fixture.content);

        assertThat(report.warnings())
                .filteredOn(issue -> "CV-136".equals(issue.rule()))
                .extracting(ContentValidationReport.Issue::message)
                .anyMatch(message -> message.contains("TESTING.JUNIT"));
    }

    @Test
    void shouldAcceptOptionalRetiredFlagOnReading() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        fixture.readings().getFirst().put("retired", false);

        assertThat(validator.validate(fixture.content).errors()).isEmpty();
    }

    static Stream<Arguments> warningCases() {
        return Stream.of(
                warning(
                        "CV-113",
                        "two checklists match the same taskType and skill",
                        f -> {
                            f.checklists().getLast().put("taskTypes", list("CHALLENGE"));
                            f.checklists().getLast().put("skillCodes", list("JAVA.EXCEPTION"));
                        }),
                warning(
                        "CV-24",
                        "ALGORITHM not SHOULD",
                        f -> f.target("ALGORITHM.SORT_SEARCH").put("priority", "LATER")),
                warning(
                        "CV-48",
                        "MUST skill without seed card",
                        f -> f.cards().remove(f.card("JAVA.EXCEPTION.CAUSE_CHAIN"))),
                warning(
                        "CV-49",
                        "code block in RECALL card",
                        f ->
                                f.card("JAVA.EXCEPTION.CAUSE_CHAIN")
                                        .put(
                                                "prompt",
                                                "아래 코드에서 원인 예외가 사라지는 이유는?\n"
                                                    + "```java\n"
                                                    + "throw new IllegalStateException(\"fail\");\n"
                                                    + "```")),
                warning(
                        "CV-60",
                        "PRACTICE L2 longer than 30 minutes",
                        f ->
                                f.challenge("PRACTICE.SPRING.TRANSACTION.L2.001")
                                        .put("estimatedMinutes", 35)),
                warning(
                        "CV-82",
                        "pinnedCommit null",
                        f -> f.repos().getFirst().put("pinnedCommit", null)),
                warning(
                        "CV-87",
                        "only two active readings for a repo",
                        f -> f.readings().removeFirst()),
                warning(
                        "CV-125",
                        "MUST skill loses its only reading",
                        f -> {
                            f.readings()
                                    .removeIf(
                                            reading ->
                                                    list("SPRING.TRANSACTION")
                                                            .equals(reading.get("skillCodes")));
                            f.conceptReadings()
                                    .removeIf(
                                            reading ->
                                                    list("SPRING.TRANSACTION")
                                                            .equals(reading.get("skillCodes")));
                        }),
                warning(
                        "CV-87",
                        "retired readings are not counted",
                        f -> {
                            f.readings().getFirst().put("retired", true);
                            f.retired("readingKeys").add(f.readings().getFirst().get("key"));
                        }));
    }

    private static Arguments error(String rule, String description, Consumer<Fixture> mutation) {
        return Arguments.of(rule, description, mutation);
    }

    private static Arguments warning(String rule, String description, Consumer<Fixture> mutation) {
        return Arguments.of(rule, description, mutation);
    }

    private static List<Object> list(Object... values) {
        return new ArrayList<>(List.of(values));
    }

    private static Map<String, Object> axes(
            int knowledge, int implementation, int explanation, int debugging) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("knowledge", knowledge);
        target.put("implementation", implementation);
        target.put("explanation", explanation);
        target.put("debugging", debugging);
        return target;
    }

    /** 테스트 catalog 원본(가변 Map/List)에 접근한다. 호출마다 새로 읽으므로 서로 영향이 없다. */

    // ---- 오늘의 팁 CV-90 ~ CV-96 (docs/19 §3.9, §4.3) ----------------------
    // 팁은 운영 콘텐츠에만 있어서 운영 파일을 복사해 한 곳씩 망가뜨린다.

    @Test
    void shouldRejectTipKeyThatDoesNotMatchItsSeries() {
        Fixture fixture = new Fixture(reader.read(PRODUCTION_CONTENT));
        fixture.tips().getFirst().put("series", "CONVENTION");

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-90".equals(issue.rule()));
    }

    @Test
    void shouldRejectDuplicateTipKey() {
        Fixture fixture = new Fixture(reader.read(PRODUCTION_CONTENT));
        List<Map<String, Object>> tips = fixture.tips();
        tips.get(1).put("key", tips.getFirst().get("key"));

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-90".equals(issue.rule()));
    }

    /** 근거가 둘 다 없으면 확인할 길이 없는 이야기가 된다 — WARN이 아니라 ERROR인 이유다. */
    @Test
    void shouldRejectTipWithNeitherSourceUrlNorExperiment() {
        Fixture fixture = new Fixture(reader.read(PRODUCTION_CONTENT));
        Map<String, Object> tip = fixture.tips().getFirst();
        tip.remove("sourceUrl");
        tip.remove("experiment");

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-91".equals(issue.rule()));
    }

    @Test
    void shouldRejectTipSourceOnUntrustedHost() {
        Fixture fixture = new Fixture(reader.read(PRODUCTION_CONTENT));
        fixture.withSourceUrl().put("sourceUrl", "https://blog.example.com/post");

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-92".equals(issue.rule()));
    }

    @Test
    void shouldRejectTipSkillCodeWithoutRoleTarget() {
        Fixture fixture = new Fixture(reader.read(PRODUCTION_CONTENT));
        fixture.tips()
                .getFirst()
                .put("skillCodes", new ArrayList<Object>(List.of("NO.SUCH.SKILL")));

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-93".equals(issue.rule()));
    }

    @Test
    void shouldRejectTipCauseThatIsTooShort() {
        Fixture fixture = new Fixture(reader.read(PRODUCTION_CONTENT));
        fixture.tips().getFirst().put("cause", "짧다");

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-94".equals(issue.rule()));
    }

    /** 은퇴 표시와 catalog의 목록이 어긋나면 조회되지 않는 팁이 생긴다 (docs/19 §8.2). */
    @Test
    void shouldRejectTipRetiredWithoutBeingListedInCatalog() {
        Fixture fixture = new Fixture(reader.read(PRODUCTION_CONTENT));
        fixture.tips().getFirst().put("retired", true);

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-95".equals(issue.rule()));
    }

    @Test
    void shouldWarnWhenASeriesHasNoActiveTipLeft() {
        Fixture fixture = new Fixture(reader.read(PRODUCTION_CONTENT));
        String series = String.valueOf(fixture.tips().getFirst().get("series"));
        fixture.tips().removeIf(tip -> series.equals(tip.get("series")));

        assertThat(validator.validate(fixture.content).warnings())
                .anyMatch(
                        issue -> "CV-96".equals(issue.rule()) && issue.message().endsWith(series));
    }

    // ---- 용어 사전 CV-100 ~ CV-106 (docs/19 §3.10, §4.3) --------------------
    // 표기가 흔들리면 읽을 때마다 같은 것인지 다시 확인해야 한다. 그래서 유일성 규칙이 대부분 ERROR다.

    @Test
    void shouldRejectTermKeyThatDoesNotMatchThePattern() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        fixture.terms().getFirst().put("key", "TERM.DATABASE");

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-100".equals(issue.rule()));
    }

    @Test
    void shouldRejectDuplicateTermKey() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        List<Map<String, Object>> terms = fixture.terms();
        terms.get(1).put("key", terms.getFirst().get("key"));

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-100".equals(issue.rule()));
    }

    /** 한 표기가 두 뜻을 가리키면 검색 결과에서 어느 쪽인지 알 수 없다. */
    @Test
    void shouldRejectTwoTermsWithTheSameRepresentative() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        List<Map<String, Object>> terms = fixture.terms();
        terms.get(1).put("representative", terms.getFirst().get("representative"));

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-101".equals(issue.rule()));
    }

    @Test
    void shouldRejectAliasThatIsAnotherTermsRepresentative() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        List<Map<String, Object>> terms = fixture.terms();
        terms.get(1).put("aliases", list(terms.getFirst().get("representative")));

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-101".equals(issue.rule()));
    }

    @Test
    void shouldRejectDefinitionThatIsMoreThanOneSentence() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        fixture.terms().getFirst().put("definition", "앞 문장은 여기까지다. 뒤 문장은 여기서 시작한다.");

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-102".equals(issue.rule()));
    }

    @Test
    void shouldRejectTermSourceOnAnUntrustedHost() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        fixture.terms().getFirst().put("sourceUrl", "https://blog.example.com/what-is-an-index");

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-102".equals(issue.rule()));
    }

    @Test
    void shouldRejectConfusableWithThatPointsAtNothing() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        fixture.terms().getFirst().put("confusableWith", list("TERM.DATABASE.NOT_THERE"));

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-103".equals(issue.rule()));
    }

    @Test
    void shouldRejectTermSkillWithoutARoleTarget() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        fixture.terms().getFirst().put("skillCodes", list("DATABASE"));

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-103".equals(issue.rule()));
    }

    /**
     * CV-106: 정의 안에 대표 표기를 쓰면 역방향 카드({@code TERM:{key}:REVERSE})의 답이 문제에 그대로 나온다 — 맞혀도 아무것도 말해 주지
     * 않는 카드가 된다.
     */
    @Test
    void shouldRejectDefinitionThatContainsItsOwnRepresentative() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        Map<String, Object> term = fixture.terms().getFirst();
        term.put("definition", term.get("representative") + "는 찾을 자리를 미리 정렬해 둔 자료 구조다.");

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-106".equals(issue.rule()));
    }

    @Test
    void shouldRejectActiveTermThatIsListedAsRetired() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        fixture.retired("termKeys").add(fixture.terms().getFirst().get("key"));

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-105".equals(issue.rule()));
    }

    @Test
    void shouldRejectConfusableWithThatPointsAtARetiredTerm() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        List<Map<String, Object>> terms = fixture.terms();
        Map<String, Object> retiredTerm =
                terms.stream()
                        .filter(term -> Boolean.TRUE.equals(term.get("retired")))
                        .findFirst()
                        .orElseThrow();
        terms.getFirst().put("confusableWith", list(retiredTerm.get("key")));

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-105".equals(issue.rule()));
    }

    /** CV-104는 WARN이다 — 고칠 곳은 용어가 아니라 그 표기를 쓴 <b>본문</b>이다. */
    @Test
    void shouldWarnWhenOtherContentUsesAnAliasSpelling() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        fixture.testTips().getFirst().put("title", "검사 예외를 언제 그대로 올릴지 정한다");

        ContentValidationReport report = validator.validate(fixture.content);

        assertThat(report.errors()).isEmpty();
        assertThat(report.warnings())
                .anyMatch(
                        issue ->
                                "CV-104".equals(issue.rule()) && issue.message().contains("체크 예외"));
    }

    /**
     * CV-89 (docs/19 §3.2·§7.5): MUST인 skill의 한 문장은 과제 카드 맨 위에 그대로 붙는다 — 비어 있으면 가장 자주 나오는 과제가 이유 없이
     * 나온다.
     */
    @Test
    void shouldRejectAMustSkillWithoutWhyItMatters() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        Map<String, Object> mustSkill =
                fixture.skills().stream()
                        .filter(skill -> "JAVA.EXCEPTION".equals(skill.get("code")))
                        .findFirst()
                        .orElseThrow();
        mustSkill.remove("whyItMatters");

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-89".equals(issue.rule()));
    }

    /** SHOULD·LATER인 skill에는 없어도 된다 — 자주 나오지 않는 과제까지 한 문장씩 강요하지 않는다. */
    @Test
    void shouldAllowASkillWithoutWhyItMattersWhenItIsNeverMust() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        for (Map<String, Object> target : fixture.targets()) {
            if ("JAVA.EXCEPTION".equals(target.get("skill"))) {
                target.put("priority", "SHOULD");
            }
        }
        fixture.trackTarget("test-starter.yaml", "JAVA.EXCEPTION").put("priority", "SHOULD");
        fixture.trackTarget("test-integration.yaml", "JAVA.EXCEPTION").put("priority", "SHOULD");
        fixture.skills().stream()
                .filter(skill -> "JAVA.EXCEPTION".equals(skill.get("code")))
                .findFirst()
                .orElseThrow()
                .remove("whyItMatters");

        assertThat(validator.validate(fixture.content).errors())
                .noneMatch(issue -> "CV-89".equals(issue.rule()));
    }

    /** root skill은 이 문장을 가질 수 없다 — 과제가 붙는 자리가 아니다 (CV-88). */
    @Test
    void shouldRejectWhyItMattersOnARootSkill() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        fixture.skills().stream()
                .filter(skill -> !skill.containsKey("parent"))
                .findFirst()
                .orElseThrow()
                .put("whyItMatters", "이 문장은 root skill에 있을 수 없고 길이는 충분히 길다.");

        assertThat(validator.validate(fixture.content).errors())
                .anyMatch(issue -> "CV-88".equals(issue.rule()));
    }

    static final class Fixture {

        final RawContent content;

        Fixture(RawContent content) {
            this.content = content;
        }

        Map<String, Object> catalog() {
            return map(content.catalog().root());
        }

        List<Object> fileList(String key) {
            return rawList(map(catalog().get("files")).get(key));
        }

        List<Object> retired(String key) {
            return rawList(map(catalog().get("retired")).get(key));
        }

        List<Map<String, Object>> skills() {
            return maps(document("skill-tree/test.yaml").get("skills"));
        }

        Map<String, Object> skill(String code) {
            return find(skills(), "code", code);
        }

        List<Object> prerequisites(String code) {
            return rawList(skill(code).get("prerequisites"));
        }

        List<Map<String, Object>> targets() {
            return maps(document("role-targets/test.yaml").get("targets"));
        }

        /** 학습 트랙의 role target (CV-136은 트랙별 우선순위를 본다). */
        Map<String, Object> trackTarget(String roleFile, String skill) {
            return find(maps(document("role-targets/" + roleFile).get("targets")), "skill", skill);
        }

        Map<String, Object> target(String skill) {
            return find(targets(), "skill", skill);
        }

        Map<String, Object> axisTargets(String skill) {
            return map(target(skill).get("target"));
        }

        Map<String, Object> template() {
            return document("plan-templates/test.yaml");
        }

        Map<String, Object> placement() {
            return map(template().get("placement"));
        }

        Map<String, Object> milestone(String key) {
            return find(maps(template().get("milestones")), "key", key);
        }

        List<Object> milestoneSkills(String key) {
            return rawList(milestone(key).get("skillCodes"));
        }

        List<Map<String, Object>> checklists() {
            return maps(document("checklists/test.yaml").get("checklists"));
        }

        List<Map<String, Object>> lessons() {
            return maps(document("lessons/test.yaml").get("lessons"));
        }

        /** {@code sourceUrl}이 있는 첫 팁. 없는 팁에 host 검사를 걸면 CV-92가 아니라 CV-91이 난다. */
        Map<String, Object> withSourceUrl() {
            return tips().stream()
                    .filter(tip -> tip.get("sourceUrl") != null)
                    .findFirst()
                    .orElseThrow();
        }

        List<Map<String, Object>> tips() {
            return maps(document("tips/practical.yaml").get("tips"));
        }

        List<Map<String, Object>> terms() {
            return maps(document("terms/test.yaml").get("terms"));
        }

        /** 테스트 catalog의 팁. {@link #tips()}는 운영 파일을 읽는다. */
        List<Map<String, Object>> testTips() {
            return maps(document("tips/test.yaml").get("tips"));
        }

        List<Map<String, Object>> lessonSources() {
            return maps(lessons().getFirst().get("sources"));
        }

        List<Map<String, Object>> cards() {
            return maps(document("review-cards/test.yaml").get("cards"));
        }

        Map<String, Object> card(String conceptKey) {
            return find(cards(), "conceptKey", conceptKey);
        }

        List<Map<String, Object>> cardRubric(String conceptKey) {
            return maps(card(conceptKey).get("rubric"));
        }

        Map<String, Object> challenge(String seedKey) {
            return find(
                    maps(document("challenges/test.yaml").get("challenges")), "seedKey", seedKey);
        }

        List<Map<String, Object>> challengeRubric(String seedKey) {
            return maps(challenge(seedKey).get("rubric"));
        }

        Map<String, Object> hints(String seedKey) {
            return map(challenge(seedKey).get("hints"));
        }

        List<Map<String, Object>> sources() {
            return maps(document("curated-sources.yaml").get("sources"));
        }

        Map<String, Object> curatedRepos() {
            return document("curated-repos.yaml");
        }

        List<Map<String, Object>> repos() {
            return maps(curatedRepos().get("repos"));
        }

        List<Map<String, Object>> readings() {
            return maps(curatedRepos().get("readings"));
        }

        List<Map<String, Object>> conceptReadings() {
            return maps(document("concept-readings.yaml").get("conceptReadings"));
        }

        private Map<String, Object> document(String path) {
            return map(content.document(path).root());
        }

        private static Map<String, Object> find(
                List<Map<String, Object>> items, String key, String value) {
            return items.stream()
                    .filter(item -> value.equals(item.get(key)))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("no " + key + "=" + value));
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> map(Object value) {
            return (Map<String, Object>) value;
        }

        @SuppressWarnings("unchecked")
        private static List<Map<String, Object>> maps(Object value) {
            return (List<Map<String, Object>>) value;
        }

        @SuppressWarnings("unchecked")
        private static List<Object> rawList(Object value) {
            return (List<Object>) value;
        }
    }
}
