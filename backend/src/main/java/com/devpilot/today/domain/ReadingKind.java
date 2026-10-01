package com.devpilot.today.domain;

/**
 * {@code learning_task.reading_key}가 가리키는 자료의 종류 (docs/05 §19.7). 코드 읽기·개념 읽기·개념 노트가 key namespace
 * 하나를 같이 쓰므로 클라이언트가 {@code READ.}·{@code DOC.}·{@code LESSON.} 접두사로 추측하지 않고 이 값을 본다.
 */
public enum ReadingKind {

    /** 저장소 코드 읽기 ({@code READ_CODE}, docs/19 §3.8). */
    CODE,

    /** 공식 문서 개념 읽기 ({@code READING}, docs/19 §3.13). */
    CONCEPT,

    /**
     * 개념 노트 ({@code READING}, docs/19 §3.14). 화면은 이 값을 보고 노트 화면(docs/02 SCR-LESSON)으로 간다.
     *
     * <p>새 {@code TaskType}을 만들지 않고 {@code READING}을 재사용하기로 했다(docs/06 §5.13, D-1) — enum·CHECK
     * 제약·migration·통계가 그대로 남는다. 대신 {@code reading_key}가 세 종류를 담게 되어 이 값이 필요해졌다.
     */
    LESSON
}
