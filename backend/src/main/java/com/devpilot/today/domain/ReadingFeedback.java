package com.devpilot.today.domain;

/**
 * {@code READ_CODE} 과제를 완료할 때 사용자가 고르는 읽기 평가 (docs/04 §3, docs/05 §8.4, 선택). 저장만 하고
 * 레벨·planner·budget 규칙의 입력이 아니다(docs/06 §5.3). 사람이 하는 소스 점검(docs/19 §8.5)에서 export로 읽는다.
 */
public enum ReadingFeedback {
    HELPFUL,
    TOO_HARD,
    BORING
}
