package com.devpilot.integration.ai.disabled;

import com.devpilot.integration.ai.api.AiBalance;
import com.devpilot.integration.ai.api.AiCallStatus;
import com.devpilot.integration.ai.api.AiProvider;
import com.devpilot.integration.ai.api.AiProviderCall;
import com.devpilot.integration.ai.api.AiProviderException;
import com.devpilot.integration.ai.api.AiProviderResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI를 끈 provider ({@code devpilot.ai.provider = disabled}, docs/03 §3.3). {@code AiGateway}와 {@code
 * AiBudgetGuard}가 호출 전에 {@code AI_UNAVAILABLE}로 막으므로 {@link #send}는 호출되지 않는다. 호출되면 재시도 불가 {@code
 * PROVIDER_ERROR}다.
 */
@Component
@ConditionalOnProperty(name = "devpilot.ai.provider", havingValue = "disabled")
public class DisabledAiProvider implements AiProvider {

    @Override
    public String name() {
        return "disabled";
    }

    @Override
    public AiProviderResponse send(AiProviderCall call) {
        throw new AiProviderException(AiCallStatus.PROVIDER_ERROR, "disabled", false, null, null);
    }

    @Override
    public AiBalance checkBalance() {
        return AiBalance.unsupported();
    }
}
