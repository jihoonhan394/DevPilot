package com.devpilot.content.application;

import com.devpilot.content.domain.RawContent;
import com.devpilot.plan.application.PlanTemplateRegistry;
import com.devpilot.review.application.SeedCardRegistry;
import com.devpilot.today.application.ConceptReadingRegistry;
import com.devpilot.today.application.CuratedReadingRegistry;
import com.devpilot.today.domain.ConceptReading;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 검증을 마친 콘텐츠를 메모리 registry에 등록한다 (docs/04 §9, docs/19 §3.12 7번): plan template → {@link
 * PlanTemplateRegistry}, review card → {@link SeedCardRegistry}, curated reading(은퇴한 것 포함) → {@link
 * CuratedReadingRegistry}, 개념 읽기(은퇴한 것 포함) → {@link ConceptReadingRegistry}. {@link ContentSeeder}가
 * 부른다.
 */
@Component
class ContentRegistration {

    /** {@code files.conceptReadings}를 생략했을 때의 경로 (docs/19 §3.1·§3.13). */
    private static final String DEFAULT_CONCEPT_READINGS = "concept-readings.yaml";

    private final PlanTemplateRegistry planTemplateRegistry;
    private final SeedCardRegistry seedCardRegistry;
    private final CuratedReadingRegistry curatedReadingRegistry;
    private final ConceptReadingRegistry conceptReadingRegistry;

    ContentRegistration(
            PlanTemplateRegistry planTemplateRegistry,
            SeedCardRegistry seedCardRegistry,
            CuratedReadingRegistry curatedReadingRegistry,
            ConceptReadingRegistry conceptReadingRegistry) {
        this.planTemplateRegistry = planTemplateRegistry;
        this.seedCardRegistry = seedCardRegistry;
        this.curatedReadingRegistry = curatedReadingRegistry;
        this.conceptReadingRegistry = conceptReadingRegistry;
    }

    /** 등록하고 개수를 돌려준다. */
    Registered register(RawContent content, Map<String, Object> catalog) {
        documents(content, catalog, "planTemplates")
                .forEach(
                        document ->
                                planTemplateRegistry.register(CatalogMapping.template(document)));
        seedCardRegistry.register(
                CatalogMapping.reviewCards(documents(content, catalog, "reviewCards")));
        Map<String, Object> files = RawYaml.asMap(catalog.get("files"));
        Object curatedRepos = files.get("curatedRepos");
        if (curatedRepos instanceof String path) {
            curatedReadingRegistry.register(
                    CatalogMapping.curatedReadings(RawYaml.asMap(content.document(path).root())));
        }
        registerConceptReadings(content, files);
        return new Registered(
                seedCardRegistry.cards().size(),
                curatedReadingRegistry.all().size(),
                conceptReadingRegistry.all().size());
    }

    /**
     * 개념 읽기 등록 (docs/19 §3.12 7번). {@code files.conceptReadings}는 선택 키라 생략하면 기본 경로를 쓴다. 두 registry에
     * 같은 key가 있으면 {@code GET /readings/{key}}가 무엇을 돌려줄지 정해지지 않으므로 기동을 멈춘다(docs/05 §19.7 "조회 순서").
     */
    private void registerConceptReadings(RawContent content, Map<String, Object> files) {
        Object listed = files.get("conceptReadings");
        String path = listed instanceof String value ? value : DEFAULT_CONCEPT_READINGS;
        List<ConceptReading> readings =
                CatalogMapping.conceptReadings(RawYaml.asMap(content.document(path).root()));
        List<String> collisions =
                readings.stream()
                        .map(ConceptReading::key)
                        .filter(key -> curatedReadingRegistry.find(key).isPresent())
                        .toList();
        if (!collisions.isEmpty()) {
            throw new IllegalStateException(
                    "reading key collides with a code reading: " + collisions);
        }
        conceptReadingRegistry.register(readings);
    }

    static List<Map<String, Object>> documents(
            RawContent content, Map<String, Object> catalog, String key) {
        Map<String, Object> files = RawYaml.asMap(catalog.get("files"));
        List<Map<String, Object>> documents = new ArrayList<>();
        for (Object path : RawYaml.asList(files.get(key))) {
            documents.add(RawYaml.asMap(content.document((String) path).root()));
        }
        return documents;
    }

    /** 등록 개수 (로그용). */
    record Registered(int seedCards, int readings, int conceptReadings) {}
}
