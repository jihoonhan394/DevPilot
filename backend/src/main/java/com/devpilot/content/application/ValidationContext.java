package com.devpilot.content.application;

import com.devpilot.content.domain.LoadedDocument;
import com.devpilot.content.domain.RawContent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * 한 번의 콘텐츠 검증 동안 규칙 사이에 오가는 상태 (docs/19 §4.1). 앞 단계(catalog, skill tree, role target)의 결과를 뒤 단계가
 * 참조한다. 스레드 한정.
 */
final class ValidationContext {

    /** {@code files.conceptReadings}를 생략했을 때의 경로 (docs/19 §3.1). */
    static final String DEFAULT_CONCEPT_READINGS = "concept-readings.yaml";

    private final RawContent content;
    private final List<ContentValidationReport.Issue> errors = new ArrayList<>();
    private final List<ContentValidationReport.Issue> warnings = new ArrayList<>();
    private final List<String> trustedSourceHosts;

    Map<String, Object> files = Map.of();
    Set<String> retiredSkillCodes = Set.of();
    Set<String> retiredSeedKeys = Set.of();
    Set<String> retiredConceptKeys = Set.of();
    Set<String> retiredSourceIds = Set.of();
    Set<String> retiredReadingKeys = Set.of();
    Set<String> retiredLessonKeys = Set.of();
    List<?> diagnosticCategories = List.of();

    /** 코드 읽기·개념 읽기가 같이 쓰는 key namespace (docs/19 §3.13, CV-120). */
    final Set<String> readingKeys = new HashSet<>();

    /** 은퇴하지 않은 코드 읽기·개념 읽기가 덮는 skill code (CV-125). */
    final Set<String> readableSkillCodes = new HashSet<>();

    /** 유효한 skill code → skill 원본(파일 순서 유지). */
    final Map<String, Map<String, Object>> skillsByCode = new LinkedHashMap<>();

    /** skill code → 정의 파일. */
    final Map<String, String> skillFiles = new LinkedHashMap<>();

    final Set<String> nonRootCodes = new HashSet<>();

    /** JAVA_BACKEND role target: skill code → 원본. 기본 트랙 기준 검사(CV-18 등)가 쓴다. */
    final Map<String, Map<String, Object>> targets = new LinkedHashMap<>();

    /** 트랙 전체의 role target: skill code → 트랙별 원본 목록. 진단 준비(CV-59)는 어느 트랙이든 보면 된다. */
    final Map<String, List<Map<String, Object>>> targetsBySkill = new LinkedHashMap<>();

    /** 구조 검사를 통과한 plan template(CV-61 대상). */
    final List<Map<String, Object>> validTemplates = new ArrayList<>();

    final Set<String> cardSkills = new HashSet<>();

    /** DIAGNOSTIC challenge의 skill category 집합들 (CV-59 끝 검사). */
    final List<Set<String>> diagnosticChallengeCategories = new ArrayList<>();

    ValidationContext(RawContent content, List<String> trustedSourceHosts) {
        this.content = content;
        this.trustedSourceHosts = List.copyOf(trustedSourceHosts);
    }

    void error(String rule, String where, String message) {
        errors.add(new ContentValidationReport.Issue(rule, where, message));
    }

    void warn(String rule, String where, String message) {
        warnings.add(new ContentValidationReport.Issue(rule, where, message));
    }

    int errorCount() {
        return errors.size();
    }

    ContentValidationReport report() {
        return new ContentValidationReport(errors, warnings);
    }

    LoadedDocument catalog() {
        return content.catalog();
    }

    List<String> trustedSourceHosts() {
        return trustedSourceHosts;
    }

    /** 나열된 파일을 읽은 결과. 없거나 파싱 실패면 CV-02를 남기고 null (Python {@code load_yaml}). */
    @Nullable Object load(String path) {
        LoadedDocument document = content.document(path);
        if (!document.found()) {
            error("CV-02", path, "file not found: " + path);
            return null;
        }
        if (document.error() != null) {
            error("CV-02", path, "YAML parse error: " + document.error());
            return null;
        }
        return document.root();
    }

    /** {@code files.<key>} 목록의 문자열 경로. */
    List<String> fileList(String key) {
        return RawYaml.asList(files.get(key)).stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    @Nullable String singleFile(String key) {
        return files.get(key) instanceof String path ? path : null;
    }

    /** {@code files.conceptReadings}. 생략하면 기본 경로 (docs/19 §3.1·§3.13). */
    String conceptReadingsFile() {
        String listed = singleFile("conceptReadings");
        return listed == null ? DEFAULT_CONCEPT_READINGS : listed;
    }

    /** {@code catalog.yaml}에 나열되지 않았고 파일도 없으면 {@code true} — 선택 파일이라 건너뛴다. */
    boolean conceptReadingsAbsent() {
        return singleFile("conceptReadings") == null
                && !content.document(DEFAULT_CONCEPT_READINGS).found();
    }
}
