package com.devpilot.review.domain;

/** 복습 등급 (docs/04 §3). 선언 순서가 ordinal이다: AGAIN(0) < HARD(1) < GOOD(2) < EASY(3). */
public enum ReviewRating {
    AGAIN,
    HARD,
    GOOD,
    EASY
}
