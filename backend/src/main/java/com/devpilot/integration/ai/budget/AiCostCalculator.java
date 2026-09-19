package com.devpilot.integration.ai.budget;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.math.FixedPointMath;
import com.devpilot.integration.ai.api.AiUsage;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 호출 비용 (docs/17 §8.4, BL-AIP-11). 단가는 USD/1M tokens를 기동 시 micro 정수로 바꾸고(N-6), 결정 F대로 피크 시간 판정 없이
 * {@code peak-multiplier}를 항상 곱한다. 정수 연산만 쓴다.
 *
 * <pre>
 * num  = (input − cacheRead) × inMicro + cacheRead × hitMicro + output × outMicro
 * cost = ceilDiv(peak × num, 1_000_000)
 * </pre>
 */
@Component
public class AiCostCalculator {

    private static final long TOKENS_PER_PRICE_UNIT = 1_000_000L;

    private final Map<String, Price> prices = new HashMap<>();
    private final int peakMultiplier;
    private final String configuredModel;

    @Autowired
    public AiCostCalculator(DevPilotProperties properties) {
        this(properties.ai().pricing(), properties.ai().model());
    }

    AiCostCalculator(DevPilotProperties.Pricing pricing, String configuredModel) {
        pricing.models()
                .forEach(
                        (model, price) ->
                                prices.put(
                                        model,
                                        new Price(
                                                FixedPointMath.toMicros(price.input()),
                                                FixedPointMath.toMicros(price.cacheHit()),
                                                FixedPointMath.toMicros(price.output()))));
        if (!prices.containsKey(configuredModel)) {
            throw new IllegalStateException("no price for model " + configuredModel);
        }
        this.peakMultiplier = pricing.peakMultiplier();
        this.configuredModel = configuredModel;
    }

    /** 설정 모델 단가로 계산한다 (응답 모델이 달라도 설정 모델 단가를 쓴다, docs/17 §8.4). */
    public long costMicroUsd(AiUsage usage) {
        return costMicroUsd(configuredModel, usage);
    }

    /** 지정 모델 단가로 계산한다 (eval·테스트). */
    public long costMicroUsd(String model, AiUsage usage) {
        Price price = prices.get(model);
        if (price == null) {
            throw new IllegalArgumentException("no price for model " + model);
        }
        long cached = Math.min(usage.cachedTokens(), usage.inputTokens());
        long uncached = usage.inputTokens() - cached;
        long num =
                Math.addExact(
                        Math.addExact(
                                Math.multiplyExact(uncached, price.inputMicro()),
                                Math.multiplyExact(cached, price.cacheHitMicro())),
                        Math.multiplyExact((long) usage.outputTokens(), price.outputMicro()));
        return Math.ceilDiv(Math.multiplyExact(peakMultiplier, num), TOKENS_PER_PRICE_UNIT);
    }

    private record Price(long inputMicro, long cacheHitMicro, long outputMicro) {}
}
