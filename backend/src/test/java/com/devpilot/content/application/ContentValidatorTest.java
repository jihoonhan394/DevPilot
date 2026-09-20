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
                        f -> f.readings().getFirst().put("retired", "yes")));
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

    @Test
    void shouldAcceptOptionalRetiredFlagOnReading() {
        Fixture fixture = new Fixture(reader.read(TEST_CONTENT));
        fixture.readings().getFirst().put("retired", false);

        assertThat(validator.validate(fixture.content).errors()).isEmpty();
    }

    static Stream<Arguments> warningCases() {
        return Stream.of(
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
