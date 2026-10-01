package com.devpilot.integration.ai.api;

/**
 * 사용자별 동시 실행 예약 (docs/17 §8.1·§8.3). {@code close}는 여러 번 호출해도 안전하다. 동기 호출은 {@code AiGateway}가, 비동기
 * 시작 요청은 리소스 INSERT 트랜잭션이 끝난 뒤 같은 메서드의 {@code finally}에서 닫는다.
 */
public interface AiConcurrencyReservation extends AutoCloseable {

    /** 아무것도 예약하지 않은 예약 (거부·{@code checkLimitsOnly}). */
    AiConcurrencyReservation NONE = () -> {};

    @Override
    void close();
}
