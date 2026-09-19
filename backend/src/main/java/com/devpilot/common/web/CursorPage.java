package com.devpilot.common.web;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * cursor 목록 응답 (docs/05 §2.1).
 *
 * @param items 비어 있으면 {@code []}
 * @param nextCursor 마지막 페이지면 {@code null}
 */
public record CursorPage<T>(List<T> items, @Nullable String nextCursor) {

    public CursorPage {
        items = List.copyOf(items);
    }
}
