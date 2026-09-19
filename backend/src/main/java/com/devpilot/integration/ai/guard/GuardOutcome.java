package com.devpilot.integration.ai.guard;

import com.devpilot.integration.ai.api.GuardAction;
import java.util.ArrayList;
import java.util.List;

/**
 * 가드 1개(또는 체인)의 결과 (docs/17 §6.1). record는 불변이므로 수정은 새 인스턴스다.
 *
 * @param value 가드가 고친 출력 (고칠 것이 없으면 입력 그대로)
 */
public record GuardOutcome(
        Object value, List<GuardAction> actions, List<GuardViolation> violations) {

    public GuardOutcome {
        actions = List.copyOf(actions);
        violations = List.copyOf(violations);
    }

    public static GuardOutcome unchanged(Object value) {
        return new GuardOutcome(value, List.of(), List.of());
    }

    /** 가드 구현이 쓰는 누적기. */
    static final class Builder {

        private final GuardName guard;
        private final List<GuardAction> actions = new ArrayList<>();
        private final List<GuardViolation> violations = new ArrayList<>();

        Builder(GuardName guard) {
            this.guard = guard;
        }

        Builder action(String action, String detail) {
            actions.add(new GuardAction(guard.name(), action, detail));
            return this;
        }

        Builder violation(String path, String message) {
            violations.add(new GuardViolation(guard.name(), path, message));
            return this;
        }

        GuardOutcome build(Object value) {
            return new GuardOutcome(value, actions, violations);
        }
    }
}
