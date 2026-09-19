package com.devpilot.today.domain;

import org.jspecify.annotations.Nullable;

/**
 * 큐레이션 저장소 ({@code content/curated-repos.yaml#repos[]}, docs/19 §3.8). 서버는 {@code url}을 요청하지
 * 않는다(docs/07 §5.5).
 *
 * @param subPath 저장소 안의 하위 경로. 없으면 {@code ""}
 * @param licenseNote 선택 안내 문구
 * @param pinnedCommit 줄 번호의 기준 커밋. SHA 조회에 실패했으면 null (CV-82 WARN)
 */
public record CuratedRepo(
        String key,
        String name,
        String url,
        String subPath,
        String license,
        @Nullable String licenseNote,
        String stack,
        String why,
        String cloneHint,
        @Nullable String pinnedCommit) {}
