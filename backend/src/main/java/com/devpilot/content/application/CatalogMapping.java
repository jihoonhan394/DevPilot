package com.devpilot.content.application;

import com.devpilot.common.web.AxisLevels;
import com.devpilot.plan.domain.MilestonePhase;
import com.devpilot.plan.domain.PlanTemplate;
import com.devpilot.review.domain.ReviewType;
import com.devpilot.review.domain.RubricItem;
import com.devpilot.review.domain.SeedCard;
import com.devpilot.skill.application.SkillCatalogSeedService;
import com.devpilot.skill.domain.Priority;
import com.devpilot.skill.domain.SkillCategory;
import com.devpilot.skill.domain.TargetRole;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 검증을 통과한 YAML 원본 → 적재용 record (docs/19 §3.2~§3.4). {@link ContentValidator}가 ERROR 없음을 확인한 뒤에만 부른다
 * — 형식이 틀린 값이 오면 {@link ClassCastException}·{@link IllegalArgumentException}이 난다.
 */
final class CatalogMapping {

    private static final int DEFAULT_STEP = 120;

    private CatalogMapping() {}

    static int catalogVersion(Map<String, Object> catalog) {
        return Math.toIntExact(RawYaml.longValue(catalog.get("catalogVersion")));
    }

    /** skill tree 파일들을 catalog 순서대로 이어 붙인다. index = {@code skill.sort_order}. */
    static List<SkillCatalogSeedService.SkillSeed> skills(List<Map<String, Object>> documents) {
        List<SkillCatalogSeedService.SkillSeed> seeds = new ArrayList<>();
        for (Map<String, Object> document : documents) {
            for (Object value : RawYaml.asList(document.get("skills"))) {
                Map<String, Object> skill = RawYaml.asMap(value);
                Object step = skill.get("minutesPerLevelStep");
                seeds.add(
                        new SkillCatalogSeedService.SkillSeed(
                                (String) skill.get("code"),
                                (String) skill.get("name"),
                                SkillCategory.valueOf((String) skill.get("category")),
                                (String) skill.get("parent"),
                                (String) skill.get("description"),
                                step == null
                                        ? DEFAULT_STEP
                                        : Math.toIntExact(RawYaml.longValue(step)),
                                seeds.size(),
                                RawYaml.asList(skill.get("prerequisites")).stream()
                                        .map(String.class::cast)
                                        .toList()));
            }
        }
        return seeds;
    }

    static List<SkillCatalogSeedService.RoleTargetSeed> roleTargets(
            List<Map<String, Object>> documents) {
        List<SkillCatalogSeedService.RoleTargetSeed> seeds = new ArrayList<>();
        for (Map<String, Object> document : documents) {
            TargetRole role = TargetRole.valueOf((String) document.get("targetRole"));
            for (Object value : RawYaml.asList(document.get("targets"))) {
                Map<String, Object> target = RawYaml.asMap(value);
                Map<String, Object> levels = RawYaml.asMap(target.get("target"));
                seeds.add(
                        new SkillCatalogSeedService.RoleTargetSeed(
                                role,
                                (String) target.get("skill"),
                                Priority.valueOf((String) target.get("priority")),
                                importance(target.get("importance")),
                                new AxisLevels(
                                        level(levels, "knowledge"),
                                        level(levels, "implementation"),
                                        level(levels, "explanation"),
                                        level(levels, "debugging"))));
            }
        }
        return seeds;
    }

    static PlanTemplate template(Map<String, Object> document) {
        Map<String, Object> placement = RawYaml.asMap(document.get("placement"));
        List<PlanTemplate.MilestoneTemplate> milestones = new ArrayList<>();
        for (Object value : RawYaml.asList(document.get("milestones"))) {
            Map<String, Object> milestone = RawYaml.asMap(value);
            milestones.add(
                    new PlanTemplate.MilestoneTemplate(
                            (String) milestone.get("key"),
                            (String) milestone.get("title"),
                            (String) milestone.get("description"),
                            Priority.valueOf((String) milestone.get("priority")),
                            Math.toIntExact(RawYaml.longValue(milestone.get("weightBp"))),
                            MilestonePhase.valueOf((String) milestone.get("phase")),
                            RawYaml.asList(milestone.get("skillCodes")).stream()
                                    .map(String.class::cast)
                                    .toList()));
        }
        return new PlanTemplate(
                (String) document.get("templateKey"),
                TargetRole.valueOf((String) document.get("targetRole")),
                (String) document.get("planTitle"),
                Math.toIntExact(RawYaml.longValue(placement.get("minMilestoneDays"))),
                milestones);
    }

    /** review card 파일들을 catalog 순서대로 이어 붙인다 (docs/19 §3.5). */
    static List<SeedCard> reviewCards(List<Map<String, Object>> documents) {
        List<SeedCard> cards = new ArrayList<>();
        for (Map<String, Object> document : documents) {
            for (Object value : RawYaml.asList(document.get("cards"))) {
                Map<String, Object> card = RawYaml.asMap(value);
                List<RubricItem> rubric = new ArrayList<>();
                for (Object item : RawYaml.asList(card.get("rubric"))) {
                    Map<String, Object> criterion = RawYaml.asMap(item);
                    rubric.add(
                            new RubricItem(
                                    (String) criterion.get("id"),
                                    (String) criterion.get("criterion")));
                }
                cards.add(
                        new SeedCard(
                                (String) card.get("conceptKey"),
                                (String) card.get("skill"),
                                ReviewType.valueOf((String) card.get("reviewType")),
                                (String) card.get("prompt"),
                                (String) card.get("expectedAnswer"),
                                rubric));
            }
        }
        return cards;
    }

    /** YAML 원문 문자열 → {@code numeric(3,2)} (docs/19 §3.0: double을 거치지 않는다). */
    private static BigDecimal importance(Object value) {
        return Objects.requireNonNull(RawYaml.decimal(value), "importance").setScale(2);
    }

    private static int level(Map<String, Object> levels, String axis) {
        return Math.toIntExact(RawYaml.longValue(levels.get(axis)));
    }
}
