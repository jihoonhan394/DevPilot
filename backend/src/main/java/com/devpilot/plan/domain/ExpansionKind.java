package com.devpilot.plan.domain;

/**
 * 확장 제안의 종류 (docs/04 §3, docs/06 §4.4 6단계). 저장하지 않고 replan preview 응답에만 쓴다.
 *
 * <ul>
 *   <li>{@code RESTORE_DEFERRED}: defer된 SHOULD·LATER 목표를 다시 넣는다 → replan {@code restoredDeferrals}
 *   <li>{@code RAISE_TARGET}: MUST 목표의 한 축을 1 올린다 → replan {@code acceptedTargetRaises}
 * </ul>
 */
public enum ExpansionKind {
    RESTORE_DEFERRED,
    RAISE_TARGET
}
