package com.devpilot.content.domain;

import org.jspecify.annotations.Nullable;

/**
 * YAML 파일 하나를 읽은 결과 (docs/19 §2). 파싱은 SnakeYAML safe 로딩이고 값은 {@code Map}·{@code List}·{@code
 * String}·{@code Integer}·{@code Boolean}·null이다 — 소수·날짜는 원문 문자열로 남긴다(docs/19 §3.0).
 *
 * @param found 파일이 있었는가
 * @param root 파싱 결과. 없거나 파싱 실패면 null
 * @param error 파싱 오류 요약. 성공이면 null
 */
public record LoadedDocument(boolean found, @Nullable Object root, @Nullable String error) {

    public static LoadedDocument missing() {
        return new LoadedDocument(false, null, null);
    }

    public static LoadedDocument failed(String error) {
        return new LoadedDocument(true, null, error);
    }

    public static LoadedDocument parsed(@Nullable Object root) {
        return new LoadedDocument(true, root, null);
    }
}
