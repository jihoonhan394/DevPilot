package com.devpilot.integration.ai;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.config.DevPilotProperties.AiOperationSettings;
import com.devpilot.integration.ai.api.AiOperation;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * AI 호출 설정 (docs/03 §9 {@code devpilot.ai}, BL-AIP-02): operation별 설정, provider·model 이름, 재시도 기본
 * 대기. 11개 operation이 모두 있어야 하고, 없거나 모르는 키가 있으면 기동 실패다.
 */
@Component
public class AiOperationCatalog {

    private final Map<AiOperation, AiOperationSettings> settings = new EnumMap<>(AiOperation.class);
    private final String provider;
    private final String model;
    private final Duration retryAfterDefault;

    public AiOperationCatalog(DevPilotProperties properties) {
        properties
                .ai()
                .operations()
                .forEach(
                        (key, value) -> {
                            AiOperation operation =
                                    AiOperation.fromSettingKey(key)
                                            .orElseThrow(
                                                    () ->
                                                            new IllegalStateException(
                                                                    "unknown AI operation setting "
                                                                            + key));
                            settings.put(operation, value);
                        });
        for (AiOperation operation : AiOperation.values()) {
            if (!settings.containsKey(operation)) {
                throw new IllegalStateException(
                        "devpilot.ai.operations has no settings for " + operation);
            }
        }
        this.provider = properties.ai().provider();
        this.model = properties.ai().model();
        this.retryAfterDefault = properties.ai().deepseek().retryAfterDefault();
    }

    public AiOperationSettings settings(AiOperation operation) {
        return settings.get(operation);
    }

    /** {@code SYNC} operation인가 (docs/03 §5.3). */
    public boolean sync(AiOperation operation) {
        return settings(operation).mode() == DevPilotProperties.AiMode.SYNC;
    }

    /** {@code devpilot.ai.provider} (소문자). */
    public String provider() {
        return provider;
    }

    /** {@code devpilot.ai.model}. */
    public String model() {
        return model;
    }

    /** 전송 오류 재시도 전 대기 기본값 ({@code Retry-After}가 없을 때, docs/17 §5.2). */
    public Duration retryAfterDefault() {
        return retryAfterDefault;
    }
}
