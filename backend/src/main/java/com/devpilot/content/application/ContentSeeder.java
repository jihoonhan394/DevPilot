package com.devpilot.content.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.content.domain.RawContent;
import com.devpilot.content.infrastructure.YamlContentReader;
import com.devpilot.plan.application.PlanTemplateRegistry;
import com.devpilot.review.application.SeedCardAssignmentService;
import com.devpilot.review.application.SeedCardRegistry;
import com.devpilot.skill.application.SkillCatalogSeedService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 기동 시 seed 적재 (docs/04 §9, docs/19 §3.9, BL-CNT-02). 검증 실패는 기동 실패다.
 *
 * <ol>
 *   <li>{@code seed-on-startup = false}면 끝낸다.
 *   <li>읽기 → {@link ContentValidator}. ERROR가 있으면 기동 실패, WARN은 {@code CONTENT_VALIDATION_WARNING}
 *       로그.
 *   <li>{@code catalogVersion < DB 버전}이면 적재하지 않고 WARN, 메모리 등록만 한다(이전 이미지로 롤백한 경우).
 *   <li>skill·prerequisite·role target upsert(한 트랜잭션). 사라진 skill은 {@code active = false}.
 *   <li>challenge upsert는 S3(training 모듈)부터다. 이 빌드는 {@code seed-challenges}와 무관하게 건너뛴다.
 *   <li>plan template을 {@link PlanTemplateRegistry}에, review card를 {@link SeedCardRegistry}에 등록한다.
 *       curated reading(S3) 등록은 그 단계에 붙는다.
 *   <li>새 seed card를 기존 온보딩 완료 사용자에게 추가한다({@link SeedCardAssignmentService#backfillAll()},
 *       BL-MEM-08). 이미 있는 concept key는 건너뛴다.
 * </ol>
 */
@Component
public class ContentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContentSeeder.class);

    private final YamlContentReader yamlContentReader;
    private final ContentValidator contentValidator;
    private final SkillCatalogSeedService skillCatalogSeedService;
    private final PlanTemplateRegistry planTemplateRegistry;
    private final SeedCardRegistry seedCardRegistry;
    private final SeedCardAssignmentService seedCardAssignmentService;
    private final DevPilotProperties.Content settings;

    public ContentSeeder(
            YamlContentReader yamlContentReader,
            ContentValidator contentValidator,
            SkillCatalogSeedService skillCatalogSeedService,
            PlanTemplateRegistry planTemplateRegistry,
            SeedCardRegistry seedCardRegistry,
            SeedCardAssignmentService seedCardAssignmentService,
            DevPilotProperties properties) {
        this.yamlContentReader = yamlContentReader;
        this.contentValidator = contentValidator;
        this.skillCatalogSeedService = skillCatalogSeedService;
        this.planTemplateRegistry = planTemplateRegistry;
        this.seedCardRegistry = seedCardRegistry;
        this.seedCardAssignmentService = seedCardAssignmentService;
        this.settings = properties.content();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!settings.seedOnStartup()) {
            log.info("content seed skipped: devpilot.content.seed-on-startup=false");
            return;
        }
        seed();
    }

    /** 적재 1회. 검증 ERROR가 있으면 {@link IllegalStateException}(기동 실패). */
    public void seed() {
        long started = System.nanoTime();
        RawContent content = yamlContentReader.read(settings.location());
        ContentValidationReport report = contentValidator.validate(content);
        report.warnings()
                .forEach(
                        issue ->
                                log.warn(
                                        "CONTENT_VALIDATION_WARNING rule={} where={} message={}",
                                        issue.rule(),
                                        issue.where(),
                                        issue.message()));
        if (report.hasErrors()) {
            report.errors()
                    .forEach(
                            issue ->
                                    log.error(
                                            "content validation error rule={} where={} message={}",
                                            issue.rule(),
                                            issue.where(),
                                            issue.message()));
            throw new IllegalStateException(
                    "content validation failed with " + report.errors().size() + " error(s)");
        }
        Map<String, Object> catalog = RawYaml.asMap(content.catalog().root());
        int catalogVersion = CatalogMapping.catalogVersion(catalog);
        int dbVersion = skillCatalogSeedService.currentCatalogVersion();
        if (catalogVersion < dbVersion) {
            log.warn(
                    "CONTENT_SEED_SKIPPED_OLDER_CATALOG catalogVersion={} dbVersion={}",
                    catalogVersion,
                    dbVersion);
        } else {
            upsertCatalog(content, catalog, catalogVersion);
        }
        log.info(
                "CONTENT_CHALLENGE_SEED_SKIPPED challenge upsert starts with the training module"
                        + " (S3), seedChallenges={}",
                settings.seedChallenges());
        documents(content, catalog, "planTemplates")
                .forEach(
                        document ->
                                planTemplateRegistry.register(CatalogMapping.template(document)));
        seedCardRegistry.register(
                CatalogMapping.reviewCards(documents(content, catalog, "reviewCards")));
        int backfilled = seedCardAssignmentService.backfillAll();
        log.info(
                "content seed finished catalogVersion={} dbVersion={} warnings={} seedCards={}"
                        + " backfilledCards={} durationMs={}",
                catalogVersion,
                dbVersion,
                report.warnings().size(),
                seedCardRegistry.cards().size(),
                backfilled,
                (System.nanoTime() - started) / 1_000_000);
    }

    private void upsertCatalog(
            RawContent content, Map<String, Object> catalog, int catalogVersion) {
        SkillCatalogSeedService.SeedOutcome outcome =
                skillCatalogSeedService.upsert(
                        new SkillCatalogSeedService.CatalogSeedCommand(
                                catalogVersion,
                                CatalogMapping.skills(documents(content, catalog, "skillTrees")),
                                CatalogMapping.roleTargets(
                                        documents(content, catalog, "roleTargets")),
                                CatalogChecks.stringSet(
                                        RawYaml.asMap(catalog.get("retired")).get("skillCodes"))));
        outcome.implicitlyRetired()
                .forEach(code -> log.warn("CONTENT_IMPLICIT_RETIREMENT skillCode={}", code));
        log.info(
                "content catalog upserted catalogVersion={} createdSkills={} updatedSkills={}",
                catalogVersion,
                outcome.createdSkills(),
                outcome.updatedSkills());
    }

    private static List<Map<String, Object>> documents(
            RawContent content, Map<String, Object> catalog, String key) {
        Map<String, Object> files = RawYaml.asMap(catalog.get("files"));
        List<Map<String, Object>> documents = new ArrayList<>();
        for (Object path : RawYaml.asList(files.get(key))) {
            documents.add(RawYaml.asMap(content.document((String) path).root()));
        }
        return documents;
    }
}
