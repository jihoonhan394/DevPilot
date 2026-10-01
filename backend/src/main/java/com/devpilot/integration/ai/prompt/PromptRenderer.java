package com.devpilot.integration.ai.prompt;

import com.devpilot.integration.ai.api.PromptValue;
import com.devpilot.integration.ai.api.TruncateMode;
import com.devpilot.integration.ai.api.UserContentBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * user message 렌더링 (docs/17 §3.0 토큰 추정·절삭, §9.3 치환·escape). 순수 계산이고 상태는 호출마다 만든다.
 *
 * <pre>
 * estimateTokens(s) = ceilDiv(ASCII 문자 수, 3) + 비ASCII 문자 수
 * est > budget 이면: 아직 최소 보존량에 닿지 않은 truncatable 입력 중 truncateOrder가 가장 작은 것을
 * need = est − budget 토큰 이상 줄이고(최소 보존량까지만) 다시 렌더링한다. 대상이 없으면 IllegalStateException.
 * </pre>
 */
public final class PromptRenderer {

    /** placeholder 정규식 (docs/17 §9.3). */
    public static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z][a-zA-Z0-9]*)\\}\\}");

    static final String DROPPED = "(생략)";
    private static final int ASCII_CHARS_PER_TOKEN = 3;
    private static final int MAX_ASCII = 0x7F;

    private PromptRenderer() {}

    /** 보수적 토큰 추정 (docs/17 §3.0). 정수만 쓴다. */
    public static int estimateTokens(String text) {
        int ascii = 0;
        int other = 0;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            if (codePoint <= MAX_ASCII) {
                ascii++;
            } else {
                other++;
            }
            index += Character.charCount(codePoint);
        }
        return Math.ceilDiv(ascii, ASCII_CHARS_PER_TOKEN) + other;
    }

    /** 태그 escape (docs/17 §9.3). 대소문자를 무시한다. */
    public static String escapeTags(String value) {
        String escaped = replaceIgnoreCase(value, "</user_content", "&lt;/user_content");
        escaped = replaceIgnoreCase(escaped, "<user_content", "&lt;user_content");
        escaped = replaceIgnoreCase(escaped, "</validation_feedback", "&lt;/validation_feedback");
        return replaceIgnoreCase(escaped, "<validation_feedback", "&lt;validation_feedback");
    }

    /**
     * 템플릿을 렌더링하고 토큰 예산을 넘으면 절삭한다.
     *
     * @return 렌더링된 user message와 추정 토큰
     */
    public static Rendered render(
            String template,
            Map<String, PromptValue> variables,
            List<UserContentBlock> userContent,
            int inputTokenBudget) {
        List<Slot> slots = new ArrayList<>();
        variables.forEach((name, value) -> slots.add(Slot.ofVariable(name, value)));
        userContent.forEach(block -> slots.add(Slot.ofUserContent(block)));
        String rendered = substitute(template, slots);
        int estimate = estimateTokens(rendered);
        while (estimate > inputTokenBudget) {
            Optional<Slot> target =
                    slots.stream()
                            .filter(Slot::canShrink)
                            .min(Comparator.comparingInt(Slot::order));
            if (target.isEmpty()) {
                throw new IllegalStateException(
                        "prompt input exceeds budget after truncation: "
                                + estimate
                                + " > "
                                + inputTokenBudget);
            }
            target.get().shrink(estimate - inputTokenBudget);
            rendered = substitute(template, slots);
            estimate = estimateTokens(rendered);
        }
        return new Rendered(rendered, estimate);
    }

    private static String substitute(String template, List<Slot> slots) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder(template.length() * 2);
        while (matcher.find()) {
            String name = matcher.group(1);
            Slot slot =
                    slots.stream()
                            .filter(candidate -> candidate.name.equals(name))
                            .findFirst()
                            .orElseThrow(
                                    () -> new IllegalArgumentException("missing input " + name));
            matcher.appendReplacement(out, Matcher.quoteReplacement(slot.render()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String replaceIgnoreCase(String value, String target, String replacement) {
        String lower = value.toLowerCase(Locale.ROOT);
        String needle = target.toLowerCase(Locale.ROOT);
        int found = lower.indexOf(needle);
        if (found < 0) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        int from = 0;
        while (found >= 0) {
            out.append(value, from, found).append(replacement);
            from = found + target.length();
            found = lower.indexOf(needle, from);
        }
        out.append(value, from, value.length());
        return out.toString();
    }

    /** 렌더링 결과. */
    public record Rendered(String text, int estimatedTokens) {}

    /** 입력 1개의 현재 상태 (렌더링 1회 안에서만 쓴다). */
    private static final class Slot {

        private final String name;
        private final @Nullable UserContentBlock block;
        private final @Nullable Integer order;
        private final @Nullable TruncateMode mode;
        private final int minKeep;
        private final String emptyText;
        private final boolean listValue;
        private final List<String> items;
        private String text;
        private int removed;
        private boolean dropped;
        private boolean exhausted;

        private Slot(
                String name,
                @Nullable UserContentBlock block,
                @Nullable Integer order,
                @Nullable TruncateMode mode,
                int minKeep,
                String text,
                List<String> items) {
            this.name = name;
            this.block = block;
            this.order = order;
            this.mode = mode;
            this.minKeep = minKeep;
            this.text = text;
            this.listValue =
                    mode == TruncateMode.ITEMS_FROM_END || mode == TruncateMode.ITEMS_FROM_START;
            this.emptyText = items.isEmpty() ? text : "";
            this.items = new ArrayList<>(items);
            this.exhausted = order == null;
        }

        static Slot ofVariable(String name, PromptValue value) {
            return new Slot(
                    name,
                    null,
                    value.truncateOrder(),
                    value.mode(),
                    value.minKeep(),
                    value.text(),
                    value.items());
        }

        static Slot ofUserContent(UserContentBlock block) {
            return new Slot(
                    block.name(),
                    block,
                    block.truncateOrder(),
                    block.mode(),
                    block.minKeep(),
                    block.text(),
                    block.items());
        }

        int order() {
            return order == null ? Integer.MAX_VALUE : order;
        }

        boolean canShrink() {
            return !exhausted;
        }

        /** {@code need} 토큰 이상 줄인다. 최소 보존량에 닿으면 더 줄이지 않는다. */
        void shrink(int need) {
            switch (Objects.requireNonNull(mode, "mode")) {
                case DROP -> {
                    dropped = true;
                    exhausted = true;
                }
                case ITEMS_FROM_END, ITEMS_FROM_START -> shrinkItems(need);
                case TAIL_CHARS -> shrinkChars(need);
                case TAIL_LINES -> shrinkLines(need);
            }
        }

        private void shrinkItems(int need) {
            int reduced = 0;
            while (items.size() > minKeep && reduced < need) {
                String item =
                        mode == TruncateMode.ITEMS_FROM_END
                                ? items.remove(items.size() - 1)
                                : items.remove(0);
                reduced += estimateTokens(item) + 1;
                removed++;
            }
            if (items.size() <= minKeep) {
                exhausted = true;
            }
        }

        private void shrinkChars(int need) {
            int[] codePoints = text.codePoints().toArray();
            int keep = codePoints.length;
            long cost = 0;
            long target = (long) need * ASCII_CHARS_PER_TOKEN;
            while (keep > minKeep && cost < target) {
                keep--;
                cost += codePoints[keep] <= MAX_ASCII ? 1 : ASCII_CHARS_PER_TOKEN;
            }
            removed += codePoints.length - keep;
            text = new String(codePoints, 0, keep);
            if (keep <= minKeep) {
                exhausted = true;
            }
        }

        private void shrinkLines(int need) {
            List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
            int reduced = 0;
            while (lines.size() > minKeep && reduced < need) {
                String line = lines.remove(lines.size() - 1);
                reduced += estimateTokens(line) + 1;
                removed++;
            }
            text = String.join("\n", lines);
            if (lines.size() <= minKeep) {
                exhausted = true;
            }
        }

        String render() {
            String body = body();
            UserContentBlock content = block;
            if (content == null) {
                return escapeTags(body);
            }
            StringBuilder out = new StringBuilder(body.length() + 80);
            out.append("<user_content name=\"")
                    .append(content.name())
                    .append("\" kind=\"")
                    .append(content.kind().name())
                    .append('"');
            if (content.language() != null) {
                out.append(" language=\"").append(content.language()).append('"');
            }
            out.append(">\n");
            if (!body.isEmpty()) {
                out.append(escapeTags(body)).append('\n');
            }
            out.append("</user_content>");
            return out.toString();
        }

        private String body() {
            if (dropped) {
                return DROPPED;
            }
            if (listValue) {
                return itemsBody();
            }
            if (!items.isEmpty()) {
                return String.join("\n", items);
            }
            UserContentBlock content = block;
            String value =
                    content != null && content.kind().numbered() ? numbered(text, content) : text;
            if (removed == 0) {
                return value;
            }
            String marker =
                    mode == TruncateMode.TAIL_LINES
                            ? "…(이하 " + removed + "줄 생략)"
                            : "…(이하 " + removed + "자 생략)";
            return value + "\n" + marker;
        }

        private String itemsBody() {
            if (items.isEmpty() && removed == 0) {
                return emptyText;
            }
            List<String> lines = new ArrayList<>(items);
            if (removed > 0) {
                String marker = "…(" + removed + "개 생략)";
                if (mode == TruncateMode.ITEMS_FROM_END) {
                    lines.add(marker);
                } else {
                    lines.add(0, marker);
                }
            }
            return String.join("\n", lines);
        }

        private static String numbered(String code, UserContentBlock content) {
            if (code.isEmpty()) {
                return code;
            }
            String[] lines = code.split("\n", -1);
            StringBuilder out = new StringBuilder(code.length() + lines.length * 6);
            for (int index = 0; index < lines.length; index++) {
                if (index > 0) {
                    out.append('\n');
                }
                out.append(
                        String.format(
                                Locale.ROOT,
                                "%4d| %s",
                                content.firstLineNumber() + index,
                                lines[index]));
            }
            return out.toString();
        }
    }
}
