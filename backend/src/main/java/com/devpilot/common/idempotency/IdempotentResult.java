package com.devpilot.common.idempotency;

import org.springframework.http.ResponseEntity;

/**
 * {@link IdempotencyService#execute} 결과. {@code replayed = true}면 저장된 응답을 재생한 것이다.
 *
 * @param response 최초 처리 응답 또는 저장된 status·body로 만든 응답
 * @param replayed 재생 여부 — {@link #toResponseEntity()}가 {@code Idempotent-Replayed: true} 헤더를 붙인다
 */
public record IdempotentResult<T>(ResponseEntity<T> response, boolean replayed) {

    /** 응답 헤더 이름 (docs/05 §1.7). */
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    public ResponseEntity<T> toResponseEntity() {
        if (!replayed) {
            return response;
        }
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .header(REPLAYED_HEADER, "true")
                .body(response.getBody());
    }
}
