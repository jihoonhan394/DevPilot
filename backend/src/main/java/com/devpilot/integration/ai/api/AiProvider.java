package com.devpilot.integration.ai.api;

/**
 * AI 공급자 port (docs/03 §3.3, docs/17 §5.1). {@code AiGateway}만 호출한다. 구현은 {@code
 * DeepSeekAiProvider}, {@code FakeAiProvider}, {@code DisabledAiProvider}이고 {@code
 * devpilot.ai.provider}로 하나가 등록된다. provider는 재시도하지 않는다 — 재시도는 {@code AiGateway}가 한다(docs/17 §5.2).
 */
public interface AiProvider {

    /** 설정 이름 (docs/04 §3: {@code deepseek}, {@code fake}, {@code disabled}). */
    String name();

    /** 호출 1회. 전송·HTTP 오류는 {@link AiProviderException} (docs/17 §5.3). */
    AiProviderResponse send(AiProviderCall call);

    /** 잔액 조회 (docs/17 §8.7). 지원하지 않으면 {@link AiBalance#unsupported()}. */
    AiBalance checkBalance();
}
