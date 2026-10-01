package com.devpilot.integration.ai.guard;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardAction;
import com.devpilot.integration.ai.api.GuardContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 출력 가드 체인 (docs/17 §6.1). 순서: {@code ENUM} → {@code SKILL_CODE} → {@code VERIFICATION} → {@code
 * FINDING_COUNT} → {@code CODE_LEAK} → {@code NO_ANSWER} → {@code LANGUAGE}. 앞 가드가 고친 값을 다음 가드가 받고,
 * 위반이 있어도 나머지 가드를 모두 실행해 feedback에 함께 넣는다.
 */
@Component
public class OutputGuardChain {

    private final List<OutputGuard> guards;

    public OutputGuardChain(List<OutputGuard> guards) {
        List<OutputGuard> ordered = new ArrayList<>(guards);
        ordered.sort(Comparator.comparingInt(guard -> guard.name().ordinal()));
        if (ordered.size() != GuardName.values().length) {
            throw new IllegalStateException("expected one guard per GuardName but got " + ordered);
        }
        this.guards = List.copyOf(ordered);
    }

    /** 체인 적용. */
    public GuardOutcome apply(
            AiOperation operation, Object output, GuardContext context, boolean lastAttempt) {
        Object value = output;
        List<GuardAction> actions = new ArrayList<>();
        List<GuardViolation> violations = new ArrayList<>();
        for (OutputGuard guard : guards) {
            GuardOutcome outcome = guard.apply(operation, value, context, lastAttempt);
            value = outcome.value();
            actions.addAll(outcome.actions());
            violations.addAll(outcome.violations());
        }
        return new GuardOutcome(value, actions, violations);
    }
}
