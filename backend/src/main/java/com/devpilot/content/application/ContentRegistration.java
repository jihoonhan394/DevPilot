package com.devpilot.content.application;

import com.devpilot.content.domain.RawContent;
import com.devpilot.plan.application.PlanTemplateRegistry;
import com.devpilot.review.application.SeedCardRegistry;
import com.devpilot.today.application.CuratedReadingRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 검증을 마친 콘텐츠를 메모리 registry에 등록한다 (docs/04 §9, docs/19 §3): plan template → {@link
 * PlanTemplateRegistry}, review card → {@link SeedCardRegistry}, curated reading(은퇴한 것 포함) → {@link
 * CuratedReadingRegistry}. {@link ContentSeeder}가 부른다.
 */
@Component
class ContentRegistration {

    private final PlanTemplateRegistry planTemplateRegistry;
    private final SeedCardRegistry seedCardRegistry;
    private final CuratedReadingRegistry curatedReadingRegistry;

    ContentRegistration(
            PlanTemplateRegistry planTemplateRegistry,
            SeedCardRegistry seedCardRegistry,
            CuratedReadingRegistry curatedReadingRegistry) {
        this.planTemplateRegistry = planTemplateRegistry;
        this.seedCardRegistry = seedCardRegistry;
        this.curatedReadingRegistry = curatedReadingRegistry;
    }

    /** 등록하고 개수를 돌려준다. */
    Registered register(RawContent content, Map<String, Object> catalog) {
        documents(content, catalog, "planTemplates")
                .forEach(
                        document ->
                                planTemplateRegistry.register(CatalogMapping.template(document)));
        seedCardRegistry.register(
                CatalogMapping.reviewCards(documents(content, catalog, "reviewCards")));
        Object curatedRepos = RawYaml.asMap(catalog.get("files")).get("curatedRepos");
        if (curatedRepos instanceof String path) {
            curatedReadingRegistry.register(
                    CatalogMapping.curatedReadings(RawYaml.asMap(content.document(path).root())));
        }
        return new Registered(seedCardRegistry.cards().size(), curatedReadingRegistry.all().size());
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
    record Registered(int seedCards, int readings) {}
}
