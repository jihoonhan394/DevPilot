package com.devpilot.integration.ai.api;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * 공급자 선불 잔액 조회 결과 (docs/17 §8.7).
 *
 * @param supported false면 잔액 조회를 지원하지 않는 provider ({@code fake}, {@code disabled}) — job은 아무것도 하지
 *     않는다
 * @param available DeepSeek {@code is_available}
 * @param totalBalanceUsd {@code balance_infos[]} 중 {@code currency = USD}의 {@code total_balance}.
 *     없으면 null
 */
public record AiBalance(
        boolean supported, boolean available, @Nullable BigDecimal totalBalanceUsd) {

    public static AiBalance unsupported() {
        return new AiBalance(false, false, null);
    }

    public static AiBalance of(boolean available, @Nullable BigDecimal totalBalanceUsd) {
        return new AiBalance(true, available, totalBalanceUsd);
    }
}
