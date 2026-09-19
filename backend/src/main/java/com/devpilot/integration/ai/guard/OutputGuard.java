package com.devpilot.integration.ai.guard;

import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardContext;

/**
 * 출력 가드 (docs/17 §6.1). 적용 대상이 아닌 operation이면 no-op이다. 수정은 새 record로 하고, 위반은 {@link
 * GuardViolation}으로 돌려준다(재시도 여부는 {@code AiGateway}가 정한다).
 */
public interface OutputGuard {

    GuardName name();

    /**
     * @param output 이 operation의 출력 record
     * @param lastAttempt 마지막 시도면 {@code LanguageGuard}는 위반 대신 {@code WARNED}
     */
    GuardOutcome apply(
            AiOperation operation, Object output, GuardContext context, boolean lastAttempt);
}
