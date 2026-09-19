package com.devpilot.content.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.content.domain.LoadedDocument;
import com.devpilot.content.domain.RawContent;
import com.devpilot.testsupport.UnitTest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

/** docs/19 §2·§3.0: 나열된 파일만 읽고, safe 로딩·중복 키 금지·alias 금지, 소수·날짜는 문자열로 둔다. */
@UnitTest
class YamlContentReaderTest {

    private static final String BASE = "memory:content/";
    private static final String CATALOG =
            String.join(
                    "\n",
                    "catalogVersion: 1",
                    "files:",
                    "  skillTrees: [skill-tree/a.yaml]",
                    "  curatedSources: curated-sources.yaml",
                    "diagnosticCategories: []",
                    "retired: {}");

    @Test
    void shouldReadOnlyListedFilesInCatalogOrder() {
        RawContent content =
                reader(
                                Map.of(
                                        "catalog.yaml", CATALOG,
                                        "skill-tree/a.yaml", "skills: []",
                                        "skill-tree/unlisted.yaml", "skills: []",
                                        "curated-sources.yaml", "sources: []"))
                        .read(BASE);

        assertThat(content.documents().keySet())
                .containsExactly("skill-tree/a.yaml", "curated-sources.yaml");
        assertThat(content.document("skill-tree/unlisted.yaml").found()).isFalse();
    }

    @Test
    void shouldKeepDecimalsAndDatesAsStringsWhenParsed() {
        RawContent content =
                reader(
                                Map.of(
                                        "catalog.yaml",
                                        CATALOG,
                                        "skill-tree/a.yaml",
                                        "importance: 0.90\n"
                                                + "verifiedAt: 2026-09-17\n"
                                                + "steps: 90",
                                        "curated-sources.yaml",
                                        "sources: []"))
                        .read(BASE);

        Object root = content.document("skill-tree/a.yaml").root();
        assertThat(root)
                .isEqualTo(Map.of("importance", "0.90", "verifiedAt", "2026-09-17", "steps", 90));
    }

    @Test
    void shouldFailDocumentWhenKeyIsDuplicated() {
        LoadedDocument document =
                reader(
                                Map.of(
                                        "catalog.yaml", CATALOG,
                                        "skill-tree/a.yaml", "code: A\ncode: B",
                                        "curated-sources.yaml", "sources: []"))
                        .read(BASE)
                        .document("skill-tree/a.yaml");

        assertThat(document.found()).isTrue();
        assertThat(document.root()).isNull();
        assertThat(document.error()).isNotBlank();
    }

    @Test
    void shouldFailDocumentWhenAliasIsUsed() {
        LoadedDocument document =
                reader(
                                Map.of(
                                        "catalog.yaml", CATALOG,
                                        "skill-tree/a.yaml", "base: &b [1, 2]\ncopy: *b",
                                        "curated-sources.yaml", "sources: []"))
                        .read(BASE)
                        .document("skill-tree/a.yaml");

        assertThat(document.error()).isNotBlank();
    }

    @Test
    void shouldFailDocumentWhenCustomTagRequestsJavaType() {
        LoadedDocument document =
                reader(
                                Map.of(
                                        "catalog.yaml", CATALOG,
                                        "skill-tree/a.yaml", "value: !!java.io.File /tmp/x",
                                        "curated-sources.yaml", "sources: []"))
                        .read(BASE)
                        .document("skill-tree/a.yaml");

        assertThat(document.root()).isNull();
        assertThat(document.error()).isNotBlank();
    }

    @Test
    void shouldReportMissingCatalogWhenLocationIsEmpty() {
        RawContent content = reader(Map.of()).read(BASE);

        assertThat(content.catalog().found()).isFalse();
        assertThat(content.documents()).isEmpty();
    }

    private static YamlContentReader reader(Map<String, String> files) {
        Map<String, String> byLocation = new HashMap<>();
        files.forEach((path, text) -> byLocation.put(BASE + path, text));
        return new YamlContentReader(
                new DefaultResourceLoader() {
                    @Override
                    public Resource getResource(String location) {
                        String text = byLocation.get(location);
                        if (text == null) {
                            return new ByteArrayResource(new byte[0]) {
                                @Override
                                public boolean exists() {
                                    return false;
                                }
                            };
                        }
                        return new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8));
                    }
                });
    }
}
