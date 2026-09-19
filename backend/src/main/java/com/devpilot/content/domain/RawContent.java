package com.devpilot.content.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 검증 전 콘텐츠 원본 (docs/19 §2): {@code catalog.yaml}과 거기 나열된 파일들. 키는 content 루트 기준 상대 경로다.
 *
 * @param documents 나열된 파일 경로 → 읽은 결과. 목록 순서를 유지한다
 */
public record RawContent(LoadedDocument catalog, Map<String, LoadedDocument> documents) {

    public RawContent {
        documents = Collections.unmodifiableMap(new LinkedHashMap<>(documents));
    }

    /** 나열되지 않았거나 읽지 않은 경로는 {@link LoadedDocument#missing()}. */
    public LoadedDocument document(String path) {
        return documents.getOrDefault(path, LoadedDocument.missing());
    }
}
