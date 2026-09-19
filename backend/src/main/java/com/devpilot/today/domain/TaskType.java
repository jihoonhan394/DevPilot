package com.devpilot.today.domain;

/**
 * 학습 과제 종류 (docs/04 §3). {@code READ_CODE}는 큐레이션 저장소 읽기다(docs/06 §5.3, RC-1~RC-4, S3). {@code
 * REVIEW}는 복습 과제(main이 아님)이고 나머지는 main 과제다.
 */
public enum TaskType {
    RECALL,
    REVIEW,
    CHALLENGE,
    PROJECT_TASK,
    COACH_REVIEW,
    READING,
    READ_CODE,
    EXPLAIN
}
