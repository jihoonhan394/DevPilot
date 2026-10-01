package com.devpilot.goal.domain;

import java.util.UUID;

/**
 * 학습 목표일이 바뀌었다 (docs/06 §11.1, BL-GOL-01). plan 모듈이 같은 트랜잭션에서 구독해 활성 plan의 {@code
 * replan_recommended}를 켠다. goal은 plan을 모른다(docs/03 §2.2 규칙 2).
 */
public record LearningGoalDatesChanged(UUID userId, UUID learningGoalId) {}
