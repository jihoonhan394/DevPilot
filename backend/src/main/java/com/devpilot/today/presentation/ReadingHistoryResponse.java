package com.devpilot.today.presentation;

import com.devpilot.today.application.ReadingHistoryView;
import java.util.List;

/** {@code GET /readings} 응답 (docs/05 §19.14). cursor를 쓰지 않는다 — 한 사람이 받은 읽기는 많아야 수십 개다. */
public record ReadingHistoryResponse(List<ReadingHistoryView> readings) {

    public ReadingHistoryResponse {
        readings = List.copyOf(readings);
    }
}
