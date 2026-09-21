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
import com.devpilot.today.domain.CompleteQuestion;
import com.devpilot.today.domain.ConceptReading;
import com.devpilot.today.domain.CuratedReading;
import com.devpilot.today.domain.CuratedRepo;
import com.devpilot.today.domain.Lesson;
import com.devpilot.today.domain.LessonExample;
import com.devpilot.today.domain.LessonProblem;
import com.devpilot.today.domain.LessonSource;
import com.devpilot.today.domain.LessonUnit;
import com.devpilot.today.domain.PredictQuestion;
import com.devpilot.training.application.ChallengeSeedService;
import com.devpilot.training.domain.ChallengePurpose;
import com.devpilot.training.domain.ChallengeRubricItem;
import com.devpilot.training.domain.RubricAxis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

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

    /**
     * curated repo·reading (docs/19 §3.8). 은퇴한 reading도 넣는다({@code retired = true}) — 조회는 되고
     * planner만 제외한다 (docs/19 §8.2).
     */
    static List<CuratedReading> curatedReadings(Map<String, Object> document) {
        Map<String, CuratedRepo> repos = new HashMap<>();
        for (Object value : RawYaml.asList(document.get("repos"))) {
            Map<String, Object> repo = RawYaml.asMap(value);
            String key = (String) repo.get("key");
            repos.put(
                    key,
                    new CuratedRepo(
                            key,
                            (String) repo.get("name"),
                            (String) repo.get("url"),
                            (String) repo.get("subPath"),
                            (String) repo.get("license"),
                            (String) repo.get("licenseNote"),
                            (String) repo.get("stack"),
                            (String) repo.get("why"),
                            (String) repo.get("cloneHint"),
                            (String) repo.get("pinnedCommit")));
        }
        List<CuratedReading> readings = new ArrayList<>();
        for (Object value : RawYaml.asList(document.get("readings"))) {
            Map<String, Object> reading = RawYaml.asMap(value);
            List<?> lines = RawYaml.asList(reading.get("lines"));
            readings.add(
                    new CuratedReading(
                            (String) reading.get("key"),
                            Objects.requireNonNull(
                                    repos.get((String) reading.get("repo")), "reading repo"),
                            (String) reading.get("path"),
                            Math.toIntExact(RawYaml.longValue(lines.get(0))),
                            Math.toIntExact(RawYaml.longValue(lines.get(1))),
                            strings(reading.get("skillCodes")),
                            Math.toIntExact(RawYaml.longValue(reading.get("estimatedMinutes"))),
                            (String) reading.get("question"),
                            strings(reading.get("lookFor")),
                            Boolean.TRUE.equals(reading.get("retired"))));
        }
        return readings;
    }

    /**
     * 개념 읽기 (docs/19 §3.13). 은퇴한 것도 넣는다({@code retired = true}) — 조회는 되고 planner만 제외한다 (docs/19
     * §8.2).
     */
    static List<ConceptReading> conceptReadings(Map<String, Object> document) {
        List<ConceptReading> readings = new ArrayList<>();
        for (Object value : RawYaml.asList(document.get("conceptReadings"))) {
            Map<String, Object> reading = RawYaml.asMap(value);
            readings.add(
                    new ConceptReading(
                            (String) reading.get("key"),
                            (String) reading.get("title"),
                            (String) reading.get("url"),
                            (String) reading.get("publisher"),
                            (String) reading.get("versionScope"),
                            strings(reading.get("skillCodes")),
                            Math.toIntExact(RawYaml.longValue(reading.get("estimatedMinutes"))),
                            (String) reading.get("whyRead"),
                            strings(reading.get("checkPoints")),
                            LocalDate.parse(String.valueOf(reading.get("verifiedAt"))),
                            Boolean.TRUE.equals(reading.get("retired"))));
        }
        return readings;
    }

    /** 개념 노트 (docs/19 §3.14). 은퇴한 것도 넣는다 — 지난 학습 이벤트가 그 key를 가리킨다 (docs/19 §8.2). */
    static List<Lesson> lessons(List<Map<String, Object>> documents) {
        List<Lesson> lessons = new ArrayList<>();
        for (Map<String, Object> document : documents) {
            for (Object value : RawYaml.asList(document.get("lessons"))) {
                Map<String, Object> lesson = RawYaml.asMap(value);
                lessons.add(
                        new Lesson(
                                (String) lesson.get("key"),
                                (String) lesson.get("skillCode"),
                                (String) lesson.get("title"),
                                (String) lesson.get("whyItMatters"),
                                (String) lesson.get("oneLine"),
                                units(lesson.get("units")),
                                strings(lesson.get("commonMistakes")),
                                (String) lesson.get("inProject"),
                                sources(lesson.get("sources")),
                                sources(lesson.get("readMore")),
                                LocalDate.parse(String.valueOf(lesson.get("verifiedAt"))),
                                Boolean.TRUE.equals(lesson.get("retired"))));
            }
        }
        return lessons;
    }

    private static List<LessonUnit> units(@Nullable Object value) {
        List<LessonUnit> units = new ArrayList<>();
        for (Object item : RawYaml.asList(value)) {
            Map<String, Object> unit = RawYaml.asMap(item);
            Map<String, Object> example = RawYaml.asMap(unit.get("example"));
            Map<String, Object> predict = RawYaml.asMap(unit.get("predict"));
            Map<String, Object> complete = RawYaml.asMap(unit.get("complete"));
            units.add(
                    new LessonUnit(
                            (String) unit.get("key"),
                            (String) unit.get("title"),
                            Math.toIntExact(RawYaml.longValue(unit.get("minutes"))),
                            Boolean.TRUE.equals(unit.get("core")),
                            (String) unit.get("explain"),
                            new LessonExample(
                                    (String) example.get("language"),
                                    (String) example.get("code"),
                                    (String) example.get("output"),
                                    (String) example.get("note")),
                            new PredictQuestion(
                                    (String) predict.get("question"),
                                    (String) predict.get("code"),
                                    strings(predict.get("choices")),
                                    (String) predict.get("answer"),
                                    (String) predict.get("explanation")),
                            new CompleteQuestion(
                                    (String) complete.get("question"),
                                    (String) complete.get("code"),
                                    blankAnswers(complete.get("answers")),
                                    (String) complete.get("explanation")),
                            problem(unit.get("problem")),
                            strings(unit.get("prerequisiteUnits")),
                            RawYaml.asList(unit.get("variants")).stream()
                                    .map(CatalogMapping::problem)
                                    .toList()));
        }
        return units;
    }

    private static LessonProblem problem(@Nullable Object value) {
        Map<String, Object> problem = RawYaml.asMap(value);
        return new LessonProblem(
                (String) problem.get("prompt"),
                strings(problem.get("deliverables")),
                (String) problem.get("starterCode"),
                strings(problem.get("hints")),
                (String) problem.get("modelAnswer"),
                strings(problem.get("selfChecks")));
    }

    private static List<List<String>> blankAnswers(@Nullable Object value) {
        return RawYaml.asList(value).stream().map(CatalogMapping::strings).toList();
    }

    private static List<LessonSource> sources(@Nullable Object value) {
        List<LessonSource> sources = new ArrayList<>();
        for (Object item : RawYaml.asList(value)) {
            Map<String, Object> source = RawYaml.asMap(item);
            sources.add(
                    new LessonSource(
                            (String) source.get("title"),
                            (String) source.get("url"),
                            (String) source.get("versionScope")));
        }
        return sources;
    }

    /** seed challenge (docs/19 §3.6). {@code hints}는 1~3단계만 있고 검증이 이미 끝났다. */
    static List<ChallengeSeedService.ChallengeSeed> challenges(
            List<Map<String, Object>> documents) {
        List<ChallengeSeedService.ChallengeSeed> seeds = new ArrayList<>();
        for (Map<String, Object> document : documents) {
            for (Object value : RawYaml.asList(document.get("challenges"))) {
                Map<String, Object> challenge = RawYaml.asMap(value);
                seeds.add(challengeSeed(challenge));
            }
        }
        return seeds;
    }

    private static ChallengeSeedService.ChallengeSeed challengeSeed(Map<String, Object> challenge) {
        List<ChallengeRubricItem> rubric = new ArrayList<>();
        for (Object item : RawYaml.asList(challenge.get("rubric"))) {
            Map<String, Object> criterion = RawYaml.asMap(item);
            rubric.add(
                    new ChallengeRubricItem(
                            (String) criterion.get("id"),
                            (String) criterion.get("criterion"),
                            Math.toIntExact(RawYaml.longValue(criterion.get("weightBp"))),
                            RubricAxis.valueOf((String) criterion.get("axis"))));
        }
        Map<String, Object> rawHints = RawYaml.asMap(challenge.get("hints"));
        Map<String, String> hints = new LinkedHashMap<>();
        rawHints.forEach((key, hint) -> hints.put(key, String.valueOf(hint)));
        Object minutes = challenge.get("estimatedMinutes");
        return new ChallengeSeedService.ChallengeSeed(
                (String) challenge.get("seedKey"),
                ChallengePurpose.valueOf((String) challenge.get("purpose")),
                Math.toIntExact(RawYaml.longValue(challenge.get("difficulty"))),
                Boolean.TRUE.equals(challenge.get("isTransfer")),
                strings(challenge.get("skills")),
                (String) challenge.get("title"),
                minutes == null ? null : Math.toIntExact(RawYaml.longValue(minutes)),
                (String) challenge.get("scenario"),
                (String) challenge.get("prompt"),
                strings(challenge.get("constraints")),
                strings(challenge.get("expectedConcepts")),
                rubric,
                strings(challenge.get("commonMistakes")),
                strings(challenge.get("transferTargets")),
                hints);
    }

    private static List<String> strings(@Nullable Object value) {
        return RawYaml.asList(value).stream().map(String::valueOf).toList();
    }

    /** YAML 원문 문자열 → {@code numeric(3,2)} (docs/19 §3.0: double을 거치지 않는다). */
    private static BigDecimal importance(Object value) {
        return Objects.requireNonNull(RawYaml.decimal(value), "importance").setScale(2);
    }

    private static int level(Map<String, Object> levels, String axis) {
        return Math.toIntExact(RawYaml.longValue(levels.get(axis)));
    }
}
