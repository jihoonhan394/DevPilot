package com.devpilot.content.infrastructure;

import com.devpilot.content.domain.LoadedDocument;
import com.devpilot.content.domain.RawContent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * {@code content/} YAML 읽기 (docs/19 §2·§3.0). {@code catalog.yaml}의 {@code files}에 나열된 파일만 나열 순서대로
 * 읽는다 — 디렉터리를 훑지 않는다. SnakeYAML safe 로딩(임의 타입 생성 금지), anchor·alias 금지, 중복 키 금지. 소수({@code
 * importance})와 날짜({@code verifiedAt})는 {@code double}·{@code Date}로 바꾸지 않고 원문 문자열로 둔다(docs/06 §1
 * N-1).
 */
@Component
public class YamlContentReader {

    private static final String CATALOG = "catalog.yaml";
    private static final List<String> LIST_KEYS =
            List.of("skillTrees", "roleTargets", "planTemplates", "reviewCards", "challenges");
    private static final List<String> SINGLE_KEYS = List.of("curatedSources", "curatedRepos");

    private final ResourceLoader resourceLoader;

    public YamlContentReader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * @param location content 루트. 예: {@code classpath:content/}
     */
    public RawContent read(String location) {
        String base = location.endsWith("/") ? location : location + "/";
        LoadedDocument catalog = load(base + CATALOG);
        Map<String, LoadedDocument> documents = new LinkedHashMap<>();
        if (catalog.root() instanceof Map<?, ?> catalogMap
                && catalogMap.get("files") instanceof Map<?, ?> files) {
            for (String key : LIST_KEYS) {
                if (files.get(key) instanceof List<?> paths) {
                    for (Object path : paths) {
                        addDocument(documents, base, path);
                    }
                }
            }
            for (String key : SINGLE_KEYS) {
                addDocument(documents, base, files.get(key));
            }
        }
        return new RawContent(catalog, documents);
    }

    private void addDocument(Map<String, LoadedDocument> documents, String base, Object path) {
        if (path instanceof String relative && !documents.containsKey(relative)) {
            documents.put(relative, load(base + relative));
        }
    }

    private LoadedDocument load(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            return LoadedDocument.missing();
        }
        String text;
        try {
            text = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot read content file", exception);
        }
        try {
            return LoadedDocument.parsed(newYaml().load(text));
        } catch (YAMLException exception) {
            return LoadedDocument.failed(exception.getClass().getSimpleName());
        }
    }

    /** 스레드 안전하지 않으므로 호출마다 만든다. */
    static Yaml newYaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        options.setAllowRecursiveKeys(false);
        DumperOptions dumperOptions = new DumperOptions();
        return new Yaml(
                new SafeConstructor(options),
                new Representer(dumperOptions),
                dumperOptions,
                options,
                new LiteralResolver());
    }

    /** float·timestamp 암시 태그를 끈 resolver: {@code 0.80}, {@code 2026-09-17}은 문자열로 남는다. */
    private static final class LiteralResolver extends Resolver {

        @Override
        public void addImplicitResolver(Tag tag, Pattern regexp, String first) {
            if (Tag.FLOAT.equals(tag) || Tag.TIMESTAMP.equals(tag)) {
                return;
            }
            super.addImplicitResolver(tag, regexp, first);
        }

        @Override
        public void addImplicitResolver(Tag tag, Pattern regexp, String first, int limit) {
            if (Tag.FLOAT.equals(tag) || Tag.TIMESTAMP.equals(tag)) {
                return;
            }
            super.addImplicitResolver(tag, regexp, first, limit);
        }
    }
}
