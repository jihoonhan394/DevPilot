package com.devpilot.today.domain;

import org.jspecify.annotations.Nullable;

/**
 * 개념 노트의 근거 문서 또는 더 읽을거리 (docs/19 §3.14). 서버는 이 URL을 요청하지 않는다 — 앱이 새 탭으로 열 뿐이다(docs/07 §5.5).
 *
 * @param versionScope 근거 문서의 버전. 더 읽을거리에는 없을 수 있다
 */
public record LessonSource(String title, String url, @Nullable String versionScope) {}
