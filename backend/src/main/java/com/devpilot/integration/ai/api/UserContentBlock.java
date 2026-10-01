package com.devpilot.integration.ai.api;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * 사용자가 입력한 원문 1개 (docs/17 §3.0 "user content", §5.1). 저장 전에 {@code SecretMasker}를 이미 거친 값이다. {@code
 * <user_content name kind language>} 블록으로 감싸고 {@code CODE}·{@code DIFF}·{@code LOG}는 줄 번호를
 * 붙인다(§9.3). 속성값에는 고정 이름·enum만 쓴다.
 *
 * @param name 템플릿 placeholder 이름 (예: {@code learnerExplanation})
 * @param language 코드 언어 enum 이름. 없으면 null
 * @param firstLineNumber 줄 번호 시작 값 (발췌 코드)
 * @param items 목록 값의 항목(러버덕 대화 등). 목록이 아니면 비어 있다
 */
public record UserContentBlock(
        String name,
        UserContentKind kind,
        @Nullable String language,
        String text,
        int firstLineNumber,
        @Nullable Integer truncateOrder,
        @Nullable TruncateMode mode,
        int minKeep,
        List<String> items) {

    private static final Pattern ATTRIBUTE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    public UserContentBlock {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
        items = List.copyOf(items);
        if (!ATTRIBUTE.matcher(name).matches()
                || (language != null && !ATTRIBUTE.matcher(language).matches())) {
            throw new IllegalArgumentException("user content attributes must be fixed names");
        }
        if ((truncateOrder == null) != (mode == null)) {
            throw new IllegalArgumentException("truncateOrder and mode go together");
        }
        if (firstLineNumber < 1) {
            throw new IllegalArgumentException("firstLineNumber must be >= 1");
        }
    }

    /** 절삭 가능한 TEXT 블록. */
    public static UserContentBlock text(String name, String text, Truncation truncation) {
        return new UserContentBlock(
                name,
                UserContentKind.TEXT,
                null,
                text,
                1,
                truncation.order(),
                truncation.mode(),
                truncation.minKeep(),
                List.of());
    }

    /** 코드 블록(줄 번호). */
    public static UserContentBlock code(
            String name,
            UserContentKind kind,
            @Nullable String language,
            String text,
            int firstLineNumber,
            Truncation truncation) {
        return new UserContentBlock(
                name,
                kind,
                language,
                text,
                firstLineNumber,
                truncation.order(),
                truncation.mode(),
                truncation.minKeep(),
                List.of());
    }

    /** 절삭 가능한 목록 TEXT 블록. 비었으면 본문이 빈 블록이다. */
    public static UserContentBlock items(String name, List<String> items, Truncation truncation) {
        return new UserContentBlock(
                name,
                UserContentKind.TEXT,
                null,
                String.join("\n", items),
                1,
                truncation.order(),
                truncation.mode(),
                truncation.minKeep(),
                items);
    }

    public boolean truncatable() {
        return truncateOrder != null;
    }
}
