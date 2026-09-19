package com.devpilot.today.application;

import com.devpilot.today.domain.CuratedRepo;
import org.jspecify.annotations.Nullable;

/**
 * {@code GET /readings/{readingKey}}의 저장소 (docs/05 §19.7). 서버는 {@code url}을 요청하지 않는다.
 *
 * @param license 명시가 없으면 {@code UNSPECIFIED} — 그 저장소는 읽기만 한다(docs/19 §3.8)
 * @param cloneHint 로컬로 가져오는 명령 (RC-4)
 * @param pinnedCommit 줄 번호의 기준 커밋. null 가능
 */
public record CuratedRepoView(
        String key,
        String name,
        String url,
        String subPath,
        String license,
        @Nullable String licenseNote,
        String stack,
        String why,
        String cloneHint,
        @Nullable String pinnedCommit) {

    static CuratedRepoView of(CuratedRepo repo) {
        return new CuratedRepoView(
                repo.key(),
                repo.name(),
                repo.url(),
                repo.subPath(),
                repo.license(),
                repo.licenseNote(),
                repo.stack(),
                repo.why(),
                repo.cloneHint(),
                repo.pinnedCommit());
    }
}
