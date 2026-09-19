package com.devpilot.goal.application;

import java.util.UUID;

/**
 * port: 활성 plan의 {@code replan_recommended} (docs/05 §5.1 {@code
 * LearningGoalView.replanRecommended}). 값은 plan 모듈에 있지만 goal은 plan에 의존할 수 없으므로(docs/03 §2.2) goal이
 * 정의하고 plan이 구현한다.
 */
public interface ReplanRecommendationProvider {

    /** 활성 plan이 없으면 false. */
    boolean isReplanRecommended(UUID userId);
}
