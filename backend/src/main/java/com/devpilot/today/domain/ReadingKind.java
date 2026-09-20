package com.devpilot.today.domain;

/**
 * {@code learning_task.reading_key}가 가리키는 자료의 종류 (docs/05 §19.7). 코드 읽기와 개념 읽기가 key namespace 하나를
 * 같이 쓰므로 클라이언트가 {@code READ.}·{@code DOC.} 접두사로 추측하지 않고 이 값을 본다.
 */
public enum ReadingKind {

    /** 저장소 코드 읽기 ({@code READ_CODE}, docs/19 §3.8). */
    CODE,

    /** 공식 문서 개념 읽기 ({@code READING}, docs/19 §3.13). */
    CONCEPT
}
