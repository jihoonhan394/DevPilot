package com.devpilot.learning.domain;

/** 팁을 읽은 뒤 고른 값 (docs/04 §3). {@code user_daily_tip.feedback}, 한 번만 기록한다(docs/05 §20.3). */
public enum TipFeedback {
    KNEW_IT,
    LEARNED,
    WILL_TRY
}
