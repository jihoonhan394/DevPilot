package com.devpilot.today.application;

/**
 * 해 보겠다고 표시한 팁의 실험 후보 (docs/05 §8.1, docs/06 §5.12 TIP-6).
 *
 * <p><b>과제가 아니다.</b> {@code learning_task}를 만들지 않고 planner에도 들어가지 않는다 — 점수·제안 분기·시간 배분과 무관한 표시용이다.
 * 남는 시간에 해 볼 것 하나를 눈에 띄게 두는 자리다.
 */
public record TipExperimentView(
        String tipKey, String title, String experiment, int estimatedMinutes) {}
