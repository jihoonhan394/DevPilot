package com.devpilot.content.application;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.content.domain.RawContent;
import com.devpilot.content.infrastructure.YamlContentReader;
import com.devpilot.plan.application.PlanTemplateRegistry;
import com.devpilot.skill.application.SkillCatalogSeedService;
import com.devpilot.skill.domain.TargetRole;
import com.devpilot.testsupport.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** docs/04 §9, docs/19 §3.9 (BL-CNT-02): 기동 적재, 재적재 멱등, SD-01(오래된 catalog 건너뜀), 암묵 은퇴. */
@IntegrationTest
class ContentSeederIntegrationTest {

    @Autowired private ContentSeeder contentSeeder;
    @Autowired private SkillCatalogSeedService skillCatalogSeedService;
    @Autowired private PlanTemplateRegistry planTemplateRegistry;
    @Autowired private YamlContentReader yamlContentReader;
    @Autowired private DevPilotProperties properties;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void shouldSeedTestCatalogAndRegisterTemplateOnStartup() {
        assertThat(count("select count(*) from devpilot.skill where active")).isEqualTo(24);
        assertThat(count("select count(*) from devpilot.skill where parent_id is null"))
                .isEqualTo(14);
        assertThat(
                        count(
                                "select count(*) from devpilot.role_skill_target where target_role"
                                        + " = 'JAVA_BACKEND'"))
                .isEqualTo(10);
        assertThat(count("select count(*) from devpilot.skill_prerequisite")).isEqualTo(1);
        assertThat(planTemplateRegistry.get(TargetRole.JAVA_BACKEND).templateKey())
                .isEqualTo("TEST_BACKEND_DEFAULT");
        assertThat(skillCatalogSeedService.currentCatalogVersion()).isEqualTo(1);
    }

    @Test
    void shouldKeepIdsAndCountsWhenSeededAgain() {
        List<Map<String, Object>> before =
                jdbc.queryForList(
                        "select id, code, parent_id, active from devpilot.skill order by code");

        contentSeeder.seed();

        assertThat(
                        jdbc.queryForList(
                                "select id, code, parent_id, active from devpilot.skill order by"
                                        + " code"))
                .isEqualTo(before);
        assertThat(count("select count(*) from devpilot.role_skill_target")).isEqualTo(20);
    }

    @Test
    void shouldSkipUpsertWhenDatabaseCatalogIsNewer() {
        Logger logger = (Logger) LoggerFactory.getLogger(ContentSeeder.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        jdbc.update(
                "update devpilot.skill set catalog_version = 2, name = 'DB 이름' where code = 'CS'");
        try {
            contentSeeder.seed();

            assertThat(
                            jdbc.queryForObject(
                                    "select name from devpilot.skill where code = 'CS'",
                                    String.class))
                    .isEqualTo("DB 이름");
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.startsWith("CONTENT_SEED_SKIPPED_OLDER_CATALOG"));
        } finally {
            logger.detachAppender(appender);
            jdbc.update("update devpilot.skill set catalog_version = 1 where code = 'CS'");
            contentSeeder.seed();
        }
        assertThat(
                        jdbc.queryForObject(
                                "select name from devpilot.skill where code = 'CS'", String.class))
                .isEqualTo("CS");
    }

    @Test
    void shouldDeactivateSkillMissingFromCatalog() {
        RawContent content = yamlContentReader.read(properties.content().location());
        Map<String, Object> catalog =
                Objects.requireNonNull(RawYaml.asMap(content.catalog().root()));
        List<SkillCatalogSeedService.SkillSeed> skills =
                CatalogMapping.skills(documents(content, catalog, "skillTrees")).stream()
                        .filter(skill -> !skill.code().equals("SYSTEM_DESIGN.CACHING"))
                        .toList();
        List<SkillCatalogSeedService.RoleTargetSeed> targets =
                CatalogMapping.roleTargets(documents(content, catalog, "roleTargets")).stream()
                        .filter(target -> !target.skillCode().equals("SYSTEM_DESIGN.CACHING"))
                        .toList();

        transactionTemplate.executeWithoutResult(
                status -> {
                    SkillCatalogSeedService.SeedOutcome outcome =
                            skillCatalogSeedService.upsert(
                                    new SkillCatalogSeedService.CatalogSeedCommand(
                                            1, skills, targets, Set.of()));

                    assertThat(outcome.implicitlyRetired())
                            .containsExactly("SYSTEM_DESIGN.CACHING");
                    entityManager.flush();
                    assertThat(
                                    jdbc.queryForObject(
                                            "select active from devpilot.skill where code ="
                                                    + " 'SYSTEM_DESIGN.CACHING'",
                                            Boolean.class))
                            .isFalse();
                    status.setRollbackOnly();
                });

        assertThat(
                        jdbc.queryForObject(
                                "select active from devpilot.skill where code ="
                                        + " 'SYSTEM_DESIGN.CACHING'",
                                Boolean.class))
                .isTrue();
    }

    private static List<Map<String, Object>> documents(
            RawContent content, Map<String, Object> catalog, String key) {
        Map<String, Object> files = Objects.requireNonNull(RawYaml.asMap(catalog.get("files")));
        return RawYaml.asList(files.get(key)).stream()
                .map(path -> RawYaml.asMap(content.document(String.valueOf(path)).root()))
                .filter(Objects::nonNull)
                .toList();
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
