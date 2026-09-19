package com.devpilot.integration.ai.api;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 서버가 만든 prompt 변수 1개 (docs/17 §3.0 "variable", §5.1). 태그 escape(§9.3)만 적용해 치환한다. 절삭 대상이면 {@code
 * truncateOrder}(작을수록 먼저)·{@code mode}·{@code minKeep}(최소 보존량)을 둔다.
 *
 * @param text 렌더링할 값. 목록 값이면 {@code items}를 줄바꿈으로 이은 것(비었으면 대체 문자열)
 * @param items 목록 값의 항목. 목록이 아니면 비어 있다({@code ITEMS_*} 절삭은 이 항목 단위로 한다)
 */
public record PromptValue(
        String text,
        @Nullable Integer truncateOrder,
        @Nullable TruncateMode mode,
        int minKeep,
        List<String> items) {

    public PromptValue {
        Objects.requireNonNull(text, "text");
        items = List.copyOf(items);
        if ((truncateOrder == null) != (mode == null)) {
            throw new IllegalArgumentException("truncateOrder and mode go together");
        }
        if (minKeep < 0) {
            throw new IllegalArgumentException("minKeep must be >= 0");
        }
    }

    /** 절삭하지 않는 값. */
    public static PromptValue of(String text) {
        return new PromptValue(text, null, null, 0, List.of());
    }

    /** 절삭 가능한 문자열 값 ({@code DROP}, {@code TAIL_CHARS}, {@code TAIL_LINES}). */
    public static PromptValue truncatable(String text, Truncation truncation) {
        return new PromptValue(
                text, truncation.order(), truncation.mode(), truncation.minKeep(), List.of());
    }

    /** 절삭하지 않는 목록 값. 비었으면 {@code emptyText}. */
    public static PromptValue list(List<String> items, String emptyText) {
        return new PromptValue(join(items, emptyText), null, null, 0, items);
    }

    /** 절삭 가능한 목록 값 ({@code ITEMS_FROM_END}, {@code ITEMS_FROM_START}, {@code DROP}). */
    public static PromptValue truncatableList(
            List<String> items, String emptyText, Truncation truncation) {
        return new PromptValue(
                join(items, emptyText),
                truncation.order(),
                truncation.mode(),
                truncation.minKeep(),
                items);
    }

    /** 절삭 대상인가. */
    public boolean truncatable() {
        return truncateOrder != null;
    }

    private static String join(List<String> items, String emptyText) {
        return items.isEmpty() ? emptyText : String.join("\n", items);
    }
}
