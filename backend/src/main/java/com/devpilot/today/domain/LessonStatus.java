package com.devpilot.today.domain;

/**
 * 노트 하나의 진행 상태 (docs/05 §21.9). 푼 단위 수만으로 정해진다 — 따로 저장하는 값이 아니다.
 *
 * <p>선언 순서가 목록 정렬 순서다(docs/05 §21.9): 이어서 할 것이 맨 위, 아직 안 연 것, 끝낸 것 순.
 */
public enum LessonStatus {
    /** 마친 단위가 하나도 없다. */
    NOT_STARTED,

    /** 일부만 마쳤다. 목록에서 맨 위로 올라간다. */
    IN_PROGRESS,

    /** 모든 단위를 한 번 이상 마쳤다. */
    DONE;

    /** 푼 단위 수 → 상태. {@code unitCount}가 0이면 {@code NOT_STARTED}다. */
    public static LessonStatus of(int solvedUnitCount, int unitCount) {
        if (solvedUnitCount <= 0) {
            return NOT_STARTED;
        }
        return solvedUnitCount >= unitCount ? DONE : IN_PROGRESS;
    }

    /** 목록 정렬에서의 자리 (docs/05 §21.9 1번). 작을수록 위다. */
    public int listRank() {
        return switch (this) {
            case IN_PROGRESS -> 0;
            case NOT_STARTED -> 1;
            case DONE -> 2;
        };
    }
}
