package com.devpilot.content.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.content.domain.RawContent;
import com.devpilot.content.infrastructure.YamlContentReader;
import com.devpilot.review.application.SeedCardAssignmentService;
import com.devpilot.skill.application.SkillCatalogSeedService;
import com.devpilot.training.application.ChallengeSeedService;
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
 *   <li>{@code seed-challenges = true}면 seed challenge를 upsert한다({@link ChallengeSeedService}). 구조가
 *       바뀐 seed는 기동 실패다.
 *   <li>{@link ContentRegistration}이 plan template·review card·curated reading·개념 읽기(은퇴한 것 포함)를 메모리
 *       registry에 등록한다.
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
    private final ContentRegistration contentRegistration;
    private final SeedCardAssignmentService seedCardAssignmentService;
    private final ChallengeSeedService challengeSeedService;
    private final DevPilotProperties.Content settings;

    ContentSeeder(
            YamlContentReader yamlContentReader,
            ContentValidator contentValidator,
            SkillCatalogSeedService skillCatalogSeedService,
            ContentRegistration contentRegistration,
            SeedCardAssignmentService seedCardAssignmentService,
            ChallengeSeedService challengeSeedService,
            DevPilotProperties properties) {
        this.yamlContentReader = yamlContentReader;
        this.contentValidator = contentValidator;
        this.skillCatalogSeedService = skillCatalogSeedService;
        this.contentRegistration = contentRegistration;
        this.seedCardAssignmentService = seedCardAssignmentService;
        this.challengeSeedService = challengeSeedService;
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
        seedChallenges(content, catalog);
        ContentRegistration.Registered registered = contentRegistration.register(content, catalog);
        int backfilled = seedCardAssignmentService.backfillAll();
        log.info(
                "content seed finished catalogVersion={} dbVersion={} warnings={} seedCards={}"
                        + " readings={} conceptReadings={} backfilledCards={} durationMs={}",
                catalogVersion,
                dbVersion,
                report.warnings().size(),
                registered.seedCards(),
                registered.readings(),
                registered.conceptReadings(),
                backfilled,
                (System.nanoTime() - started) / 1_000_000);
    }

    /** challenge upsert (docs/04 §9 5단계). 꺼져 있으면 YAML을 검증만 한다. */
    private void seedChallenges(RawContent content, Map<String, Object> catalog) {
        if (!settings.seedChallenges()) {
            log.info("CONTENT_CHALLENGE_SEED_SKIPPED devpilot.content.seed-challenges=false");
            return;
        }
        ChallengeSeedService.SeedOutcome outcome =
                challengeSeedService.upsert(
                        CatalogMapping.challenges(
                                ContentRegistration.documents(content, catalog, "challenges")));
        if (outcome.rejected() > 0) {
            challengeSeedService
                    .rejectedSeedKeys()
                    .forEach(key -> log.warn("CONTENT_CHALLENGE_REJECTED seedKey={}", key));
        }
        log.info(
                "content challenges upserted created={} updated={} rejected={} retired={}",
                outcome.created(),
                outcome.updated(),
                outcome.rejected(),
                outcome.retired());
    }

    private void upsertCatalog(
            RawContent content, Map<String, Object> catalog, int catalogVersion) {
        SkillCatalogSeedService.SeedOutcome outcome =
                skillCatalogSeedService.upsert(
                        new SkillCatalogSeedService.CatalogSeedCommand(
                                catalogVersion,
                                CatalogMapping.skills(
                                        ContentRegistration.documents(
                                                content, catalog, "skillTrees")),
                                CatalogMapping.roleTargets(
                                        ContentRegistration.documents(
                                                content, catalog, "roleTargets")),
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
}
